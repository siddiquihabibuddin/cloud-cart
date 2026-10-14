#!/usr/bin/env bash
# Shared helpers for start.sh, stop.sh and deploy-localstack.sh. Sourced, not executed.
#
# EMULATOR selects the local AWS emulator (default: floci):
#   floci       free, MIT, no token (https://github.com/floci-io/floci)
#   localstack  needs a LocalStack account/auth token; state is lost on restart in the free tier
#
# Both listen on :4566 and serve API Gateway at /restapis/<id>/<stage>/_user_request_/, so the
# templates, frontend rewrites and MCP config are identical for either. Only one can run at a time.

ROOT="${ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
STATE_DIR="$ROOT/.dev"
FRONTEND_PORT=3000
MCP_PORT=8090
EMULATOR="${EMULATOR:-floci}"
EMULATOR_PORT=4566
EMULATOR_ENDPOINT="http://localhost:$EMULATOR_PORT"
# Log search UI (Loki + Alloy + Grafana). OBSERVABILITY=off skips it.
OBSERVABILITY="${OBSERVABILITY:-on}"
OBS_NETWORK="cloudcart-obs"
LOKI_CONTAINER="cloudcart-loki"
ALLOY_CONTAINER="cloudcart-alloy"
GRAFANA_CONTAINER="cloudcart-grafana"
LOKI_PORT=3100
GRAFANA_PORT=3001
LOKI_IMAGE="grafana/loki:3.4.2"
ALLOY_IMAGE="grafana/alloy:v1.8.1"
GRAFANA_IMAGE="grafana/grafana:11.5.2"
OPENSEARCH_CONTAINER="${OPENSEARCH_CONTAINER:-opensearch}"
OPENSEARCH_PORT=9200
# Where Lambda containers reach the host-side OpenSearch container
OPENSEARCH_INTERNAL_ENDPOINT="http://host.docker.internal:$OPENSEARCH_PORT"
export PATH="$PATH:$HOME/Library/Python/3.9/bin"

mkdir -p "$STATE_DIR"

case "$EMULATOR" in
  floci|localstack) ;;
  *) echo "Unknown EMULATOR '$EMULATOR' (use floci or localstack)" >&2; exit 1 ;;
esac

log() { echo "==> $*"; }
warn() { echo "  WARNING: $*" >&2; }

port_pids() { lsof -ti "tcp:$1" -sTCP:LISTEN 2>/dev/null || true; }

kill_port() {
  local pids
  pids="$(port_pids "$1")"
  [ -z "$pids" ] && return 0
  # shellcheck disable=SC2086
  kill $pids 2>/dev/null || true
  for _ in $(seq 1 10); do
    [ -z "$(port_pids "$1")" ] && return 0
    sleep 1
  done
  # shellcheck disable=SC2046
  kill -9 $(port_pids "$1") 2>/dev/null || true
}

wait_for_port() { # port, seconds
  for _ in $(seq 1 "$2"); do
    [ -n "$(port_pids "$1")" ] && return 0
    sleep 1
  done
  return 1
}

# Stop the MCP server (started as `mvn spring-boot:run`). The Maven parent goes first: if only the
# JVM is killed, Maven prints a "Failed to execute goal ... exit code 143" banner into mcp.log.
stop_mcp() {
  local pid ppid
  pid="$(port_pids "$MCP_PORT" | head -1)"
  [ -z "$pid" ] && return 0
  ppid="$(ps -o ppid= -p "$pid" 2>/dev/null | tr -d ' ')"
  if [ -n "$ppid" ] && ps -o command= -p "$ppid" 2>/dev/null | grep -q "spring-boot:run"; then
    kill "$ppid" 2>/dev/null || true
    sleep 2
  fi
  kill_port "$MCP_PORT"
}

# AWS CLI pointed at the active emulator
aws_cli() {
  if [ "$EMULATOR" = "localstack" ]; then
    awslocal "$@"
  else
    AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 AWS_PAGER="" \
      aws --endpoint-url "$EMULATOR_ENDPOINT" "$@"
  fi
}

container_running() { [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null || echo false)" = "true" ]; }
container_exists() { docker inspect "$1" >/dev/null 2>&1; }

emulator_container() { echo "$EMULATOR"; }
emulator_running() { container_running "$(emulator_container)"; }

emulator_health_url() {
  if [ "$EMULATOR" = "localstack" ]; then
    echo "$EMULATOR_ENDPOINT/_localstack/health"
  else
    echo "$EMULATOR_ENDPOINT/_floci/health"
  fi
}

emulator_image() {
  if [ "$EMULATOR" = "localstack" ]; then
    echo "localstack/localstack-pro:latest"
  else
    echo "floci/floci:latest"
  fi
}

# Only one emulator can own :4566
check_port_free_of_other_emulator() {
  local other="localstack"
  [ "$EMULATOR" = "localstack" ] && other="floci"
  if container_running "$other"; then
    echo "The '$other' container is running and owns :$EMULATOR_PORT. Stop it first (docker stop $other, or EMULATOR=$other ./stop.sh)." >&2
    exit 1
  fi
}

create_emulator_container() {
  local name
  name="$(emulator_container)"
  log "Creating $EMULATOR container '$name'..."
  if [ "$EMULATOR" = "floci" ]; then
    # hybrid storage + volume keeps deployed stacks across ./stop.sh and ./start.sh
    docker run -d --name "$name" -p "$EMULATOR_PORT:4566" -u root \
      -e FLOCI_STORAGE_MODE=hybrid \
      -e FLOCI_STORAGE_PERSISTENT_PATH=/data \
      -v "$STATE_DIR/floci-data:/data" \
      -v /var/run/docker.sock:/var/run/docker.sock \
      "$(emulator_image)" >/dev/null
  else
    if [ -z "${LOCALSTACK_AUTH_TOKEN:-}" ] && [ -f "$HOME/.localstack/auth.json" ]; then
      LOCALSTACK_AUTH_TOKEN="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1])).get("LOCALSTACK_AUTH_TOKEN",""))' "$HOME/.localstack/auth.json" 2>/dev/null || true)"
    fi
    if [ -n "${LOCALSTACK_AUTH_TOKEN:-}" ]; then
      docker run -d --name "$name" -p "$EMULATOR_PORT:4566" \
        -e LOCALSTACK_AUTH_TOKEN="$LOCALSTACK_AUTH_TOKEN" \
        -v /var/run/docker.sock:/var/run/docker.sock \
        "$(emulator_image)" >/dev/null
    else
      warn "LOCALSTACK_AUTH_TOKEN not set; using LocalStack Community"
      docker run -d --name "$name" -p "$EMULATOR_PORT:4566" -e ACTIVATE_PRO=0 \
        -v /var/run/docker.sock:/var/run/docker.sock \
        localstack/localstack:latest >/dev/null
    fi
  fi
}

# emulator_start [--update]   --update pulls the latest image and recreates the container
emulator_start() {
  command -v docker >/dev/null || { echo "docker not found" >&2; exit 1; }
  local name
  name="$(emulator_container)"
  check_port_free_of_other_emulator

  if [ "${1:-}" = "--update" ]; then
    log "Pulling the latest $EMULATOR image..."
    docker pull "$(emulator_image)"
    container_exists "$name" && { log "Removing old container '$name'..."; docker rm -f "$name" >/dev/null; }
  fi

  if emulator_running; then
    log "$EMULATOR already running"
  elif container_exists "$name"; then
    log "Starting existing $EMULATOR container '$name'..."
    docker start "$name" >/dev/null
  else
    create_emulator_container
    warn "fresh container: run ./deploy-localstack.sh to deploy the stacks"
  fi

  log "Waiting for $EMULATOR on :$EMULATOR_PORT..."
  for _ in $(seq 1 60); do
    curl -sf "$(emulator_health_url)" >/dev/null 2>&1 && return 0
    if ! emulator_running; then
      echo "$EMULATOR exited during startup. Last log lines:" >&2
      docker logs --tail 15 "$name" >&2 || true
      echo "" >&2
      echo "If this is an image/license error, run: ./start.sh --update (a new container has no stacks deployed)" >&2
      exit 1
    fi
    sleep 2
  done
  echo "$EMULATOR did not become healthy after 2 minutes; see: docker logs $name" >&2
  exit 1
}

emulator_stop() {
  if emulator_running; then
    log "Stopping $EMULATOR..."
    docker stop "$(emulator_container)" >/dev/null
  else
    log "$EMULATOR not running"
  fi
}

# Floci stubs AWS::OpenSearchService::Domain, so search runs against a plain OpenSearch container
# (the search template takes its endpoint as a parameter). LocalStack provides a real domain.
opensearch_start() {
  [ "$EMULATOR" = "floci" ] || return 0
  if container_running "$OPENSEARCH_CONTAINER"; then
    log "OpenSearch already running"
    return 0
  fi
  if container_exists "$OPENSEARCH_CONTAINER"; then
    log "Starting OpenSearch..."
    docker start "$OPENSEARCH_CONTAINER" >/dev/null
  else
    log "Creating OpenSearch container '$OPENSEARCH_CONTAINER'..."
    docker run -d --name "$OPENSEARCH_CONTAINER" -p "$OPENSEARCH_PORT:9200" \
      -e discovery.type=single-node -e DISABLE_SECURITY_PLUGIN=true -e DISABLE_INSTALL_DEMO_CONFIG=true \
      -e OPENSEARCH_JAVA_OPTS="-Xms512m -Xmx512m" \
      opensearchproject/opensearch:2.13.0 >/dev/null
  fi
  for _ in $(seq 1 40); do
    curl -sf "http://localhost:$OPENSEARCH_PORT" >/dev/null 2>&1 && return 0
    sleep 3
  done
  warn "OpenSearch not responding on :$OPENSEARCH_PORT yet; search may be unavailable"
}

opensearch_stop() {
  if container_running "$OPENSEARCH_CONTAINER"; then
    log "Stopping OpenSearch..."
    docker stop "$OPENSEARCH_CONTAINER" >/dev/null
  fi
}

observability_enabled() { [ "$OBSERVABILITY" != "off" ]; }

# start_container <name> <docker run args...>: start it if it exists, else create it
start_container() {
  local name="$1"; shift
  if container_running "$name"; then
    return 0
  elif container_exists "$name"; then
    docker start "$name" >/dev/null
  else
    docker run -d --name "$name" "$@" >/dev/null
  fi
}

# Loki stores logs, Alloy ships them (Lambda containers, the emulator, and .dev/*.log from the host
# processes), Grafana is the UI. Data lives in named volumes, so logs survive ./stop.sh.
observability_start() {
  if ! observability_enabled; then
    log "Observability disabled (OBSERVABILITY=off)"
    return 0
  fi
  log "Starting Loki + Alloy + Grafana..."
  docker network inspect "$OBS_NETWORK" >/dev/null 2>&1 || docker network create "$OBS_NETWORK" >/dev/null

  start_container "$LOKI_CONTAINER" --network "$OBS_NETWORK" -p "$LOKI_PORT:3100" \
    -v "$ROOT/observability/loki.yaml:/etc/loki/local-config.yaml:ro" \
    -v cloudcart-loki-data:/loki \
    "$LOKI_IMAGE" -config.file=/etc/loki/local-config.yaml
  for _ in $(seq 1 40); do
    curl -sf "http://localhost:$LOKI_PORT/ready" >/dev/null 2>&1 && break
    sleep 2
  done
  curl -sf "http://localhost:$LOKI_PORT/ready" >/dev/null 2>&1 || warn "Loki not ready yet; logs will appear once it is"

  start_container "$ALLOY_CONTAINER" --network "$OBS_NETWORK" \
    -v "$ROOT/observability/alloy.alloy:/etc/alloy/config.alloy:ro" \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v "$STATE_DIR:/logs:ro" \
    "$ALLOY_IMAGE" run --server.http.listen-addr=0.0.0.0:12345 --storage.path=/var/lib/alloy/data /etc/alloy/config.alloy

  start_container "$GRAFANA_CONTAINER" --network "$OBS_NETWORK" -p "$GRAFANA_PORT:3000" \
    -e GF_AUTH_ANONYMOUS_ENABLED=true -e GF_AUTH_ANONYMOUS_ORG_ROLE=Admin -e GF_AUTH_DISABLE_LOGIN_FORM=true \
    -e GF_ANALYTICS_REPORTING_ENABLED=false -e GF_ANALYTICS_CHECK_FOR_UPDATES=false \
    -e GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH=/etc/grafana/dashboards/cloudcart.json \
    -v "$ROOT/observability/grafana/provisioning:/etc/grafana/provisioning:ro" \
    -v "$ROOT/observability/grafana/dashboards:/etc/grafana/dashboards:ro" \
    -v cloudcart-grafana-data:/var/lib/grafana \
    "$GRAFANA_IMAGE"
  log "Grafana: http://localhost:$GRAFANA_PORT  (no login; dashboard 'CloudCart Logs')"
}

observability_stop() {
  local c
  for c in "$GRAFANA_CONTAINER" "$ALLOY_CONTAINER" "$LOKI_CONTAINER"; do
    if container_running "$c"; then
      log "Stopping $c..."
      docker stop "$c" >/dev/null
    fi
  done
}

gateway_url() {
  local id
  id="$(aws_cli apigateway get-rest-apis --query "items[?name=='UnifiedApiDev'].id | [0]" --output text 2>/dev/null || true)"
  [ -n "$id" ] && [ "$id" != "None" ] || return 1
  echo "$EMULATOR_ENDPOINT/restapis/$id/dev/_user_request_"
}

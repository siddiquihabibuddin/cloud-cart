#!/usr/bin/env bash
# Start the CloudCart local stack: the AWS emulator (Floci by default, or EMULATOR=localstack),
# OpenSearch (Floci only), the MCP server (if GROQ_API_KEY is set) and the frontend.
# Stop it with ./stop.sh.
#
#   ./start.sh            normal start (reuses the existing emulator container and cached image)
#   ./start.sh --update   pull the latest emulator image and recreate its container (use after an
#                         image/license error; the new container has no stacks deployed)
#
# Floci persists deployed stacks across restarts. LocalStack keeps API Gateway/CloudFormation/Lambda
# state in memory only, so run deploy-localstack.sh after every LocalStack (re)start.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/emulator.sh
source "$ROOT/lib/emulator.sh"

start_mcp() {
  if [ -z "${GROQ_API_KEY:-}" ]; then
    log "GROQ_API_KEY not set; skipping MCP server (shopping assistant disabled)"
    return 0
  fi
  if [ -n "$(port_pids "$MCP_PORT")" ]; then
    log "MCP server already running on :$MCP_PORT"
    return 0
  fi
  local url
  url="${UNIFIED_API_URL:-$(gateway_url || true)}"
  if [ -z "$url" ]; then
    warn "could not find the UnifiedApiDev gateway (stacks deployed? aws CLI installed?); skipping MCP server"
    return 0
  fi
  log "Starting MCP server on :$MCP_PORT (logs: .dev/mcp.log)..."
  (cd "$ROOT/cloudcart-mcp-server" && UNIFIED_API_URL="$url" nohup mvn -q spring-boot:run </dev/null \
    >>"$STATE_DIR/mcp.log" 2>&1 &)
  wait_for_port "$MCP_PORT" 90 || warn "MCP server not listening yet; check .dev/mcp.log"
}

start_frontend() {
  if [ -n "$(port_pids "$FRONTEND_PORT")" ]; then
    log "Frontend already running on :$FRONTEND_PORT"
    return 0
  fi
  [ -d "$ROOT/cloudcart-frontend/node_modules" ] || { log "Installing frontend dependencies..."; (cd "$ROOT/cloudcart-frontend" && npm install); }
  log "Starting frontend on :$FRONTEND_PORT (logs: .dev/frontend.log)..."
  (cd "$ROOT/cloudcart-frontend" && nohup npm run dev </dev/null >>"$STATE_DIR/frontend.log" 2>&1 &)
  wait_for_port "$FRONTEND_PORT" 60 && log "Frontend: http://localhost:$FRONTEND_PORT" \
    || warn "frontend not listening yet; check .dev/frontend.log"
}

status() {
  emulator_running && echo "$EMULATOR: running (:$EMULATOR_PORT)" || echo "$EMULATOR: stopped"
  if [ "$EMULATOR" = "floci" ]; then
    container_running "$OPENSEARCH_CONTAINER" && echo "opensearch: running (:$OPENSEARCH_PORT)" || echo "opensearch: stopped"
  fi
  if observability_enabled; then
    container_running "$GRAFANA_CONTAINER" && echo "grafana: running (http://localhost:$GRAFANA_PORT)" || echo "grafana: stopped"
  fi
  [ -n "$(port_pids "$MCP_PORT")" ] && echo "mcp server: running (:$MCP_PORT)" || echo "mcp server: stopped"
  [ -n "$(port_pids "$FRONTEND_PORT")" ] && echo "frontend: running (:$FRONTEND_PORT)" || echo "frontend: stopped"
}

log "Emulator: $EMULATOR"
emulator_start "${1:-}"
opensearch_start
observability_start
start_mcp
start_frontend
echo ""
status

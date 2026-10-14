#!/usr/bin/env bash
# Stop the CloudCart local stack: the frontend, the MCP server, OpenSearch and the AWS emulator
# (Floci by default, or EMULATOR=localstack). Start it with ./start.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/emulator.sh
source "$ROOT/lib/emulator.sh"

if [ -n "$(port_pids "$FRONTEND_PORT")" ]; then
  log "Stopping frontend..."
  kill_port "$FRONTEND_PORT"
else
  log "Frontend not running"
fi

if [ -n "$(port_pids "$MCP_PORT")" ]; then
  log "Stopping MCP server..."
  stop_mcp
fi

observability_stop
opensearch_stop
emulator_stop

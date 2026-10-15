#!/usr/bin/env bash
# Starts the Message Delivery server.
#
# Usage: scripts/run-delivery.sh [registry port]      (default port 1099)
#
# When bank servers run on other machines, set HOST to this machine's IP address,
# e.g. HOST=10.0.0.5 scripts/run-delivery.sh, so RMI hands out a reachable address.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="${JAR:-$ROOT/target/bank.jar}"
PORT="${1:-1099}"

OPTS=()
if [[ -n "${HOST:-}" ]]; then
    OPTS+=("-Djava.rmi.server.hostname=$HOST")
fi

exec java "${OPTS[@]}" -cp "$JAR" com.ass2.delivery.DeliveryServerMain "$PORT"

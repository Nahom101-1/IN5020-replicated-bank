#!/usr/bin/env bash
# Starts one bank server (one replica of the account).
#
# Usage: scripts/run-bank.sh <server ip:port> <account name> <number of replicas> <currency file> [batch file]
#   e.g. scripts/run-bank.sh 10.0.0.5:1099 group01 3 input/TradingRate.txt input/Rep1.txt
#
# Environment:
#   SYNC=naive|ordered  getSyncedBalance implementation (default: ordered)
#   HOST=<ip>           this machine's IP address; required when the delivery server is on
#                       another machine, because it calls back to this bank server over RMI
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="${JAR:-$ROOT/target/bank.jar}"

OPTS=("-Dsync=${SYNC:-ordered}")
if [[ -n "${HOST:-}" ]]; then
    OPTS+=("-Djava.rmi.server.hostname=$HOST")
fi

exec java "${OPTS[@]}" -jar "$JAR" "$@"

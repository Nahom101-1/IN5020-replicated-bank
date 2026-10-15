#!/usr/bin/env bash
# Runs the graded scenario from input/README.txt on this machine and saves one log per replica:
#   t=0s   3 replicas with input/Rep1.txt
#   t=7s   2 replicas with input/Rep2.txt
#   t=12s  1 replica  with input/Rep3.txt
#
# Usage: scripts/run-scenario.sh [naive|ordered] [port]      (defaults: ordered 1099)
# Logs:  logs/<mode>/delivery.log and logs/<mode>/rep1-a.log, rep1-b.log, ... rep3-a.log
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MODE="${1:-ordered}"
PORT="${2:-1099}"
ACCOUNT="group01"
RATES="input/TradingRate.txt"
LOGS="logs/$MODE"

mkdir -p "$LOGS"
rm -f "$LOGS"/*.log

scripts/run-delivery.sh "$PORT" > "$LOGS/delivery.log" 2>&1 &
DELIVERY=$!
trap 'kill $DELIVERY 2>/dev/null || true' EXIT

# Wait until the delivery server is listening before starting bank servers.
for _ in $(seq 50); do
    grep -q "listening" "$LOGS/delivery.log" && break
    sleep 0.2
done
grep -q "listening" "$LOGS/delivery.log" || { echo "Delivery server did not start:"; cat "$LOGS/delivery.log"; exit 1; }

BANKS=()
start_bank() {   # start_bank <log name> <batch file>
    SYNC="$MODE" NAME="$1" scripts/run-bank.sh "localhost:$PORT" "$ACCOUNT" 3 "$RATES" "$2" > "$LOGS/$1.log" 2>&1 &
    BANKS+=($!)
    echo "$(date +%T) started $1 ($2)"
}

start_bank rep1-a input/Rep1.txt
start_bank rep1-b input/Rep1.txt
start_bank rep1-c input/Rep1.txt
sleep 7
start_bank rep2-a input/Rep2.txt
start_bank rep2-b input/Rep2.txt
sleep 5
start_bank rep3-a input/Rep3.txt

wait "${BANKS[@]}" || true
echo "$(date +%T) all bank servers have exited; logs are in $LOGS/"

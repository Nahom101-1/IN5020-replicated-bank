#!/usr/bin/env bash
# Builds the runnable jar target/bank.jar.
# Uses Maven if it is installed, otherwise plain javac + jar (no tests are run then).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if command -v mvn >/dev/null 2>&1; then
    mvn -q package
else
    echo "Maven not found, building with javac"
    rm -rf target/classes
    mkdir -p target/classes
    javac --release 21 -d target/classes $(find src/main/java -name '*.java')
    jar --create --file target/bank.jar --main-class com.ass2.bank.BankServerMain -C target/classes .
fi
echo "Built $ROOT/target/bank.jar"

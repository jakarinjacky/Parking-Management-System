#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
export PLATFORM_DEMO=true
export PLATFORM_DATA_DIR=data/platform-demo
mkdir -p bin
platform_sources=()
while IFS= read -r source; do platform_sources+=("$source"); done < <(find src -name '*.java' -print)
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d bin "${platform_sources[@]}"
echo 'DEMO ONLY: http://localhost:8080/platform.html'
echo 'Accounts: superadmin / owner / owner2 / admin / staff; password: DemoPass123!'
exec java -cp bin server.ParkingServer

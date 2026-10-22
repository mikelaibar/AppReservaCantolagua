#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TASK_TOOLS="${CANTOLAGUA_TOOLS:-/workspace/.tools}"
SRC="$TASK_ROOT/android/app/src/main/java/es/cantolagua/misemana"
BUILD="$TASK_ROOT/android/build/live-booking"
mkdir -p "$BUILD"
java -m jdk.compiler/com.sun.tools.javac.Main -cp "$TASK_TOOLS/jsoup.jar:$TASK_TOOLS/json.jar" \
  -d "$BUILD" "$SRC/Plan.java" "$SRC/ClubClient.java" "$TASK_ROOT/tests/LiveBooking.java"
if [ -z "${CANTOLAGUA_TEST_CA_FILE:-}" ] && [ -f /usr/local/share/ca-certificates/environment-proxy-ca.crt ]; then
  export CANTOLAGUA_TEST_CA_FILE=/usr/local/share/ca-certificates/environment-proxy-ca.crt
fi
java -cp "$BUILD:$TASK_TOOLS/jsoup.jar:$TASK_TOOLS/json.jar" es.cantolagua.misemana.LiveBooking "$@"

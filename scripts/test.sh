#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TASK_TOOLS="${CANTOLAGUA_TOOLS:-/workspace/.tools}"
CLASSES="$TASK_ROOT/android/build/tests"
mkdir -p "$CLASSES"
CP="$TASK_TOOLS/jsoup.jar:$TASK_TOOLS/json.jar"
SRC="$TASK_ROOT/android/app/src/main/java/es/cantolagua/misemana"
java -m jdk.compiler/com.sun.tools.javac.Main -cp "$CP" -d "$CLASSES" "$SRC/Plan.java" "$SRC/ClubClient.java" "$TASK_ROOT/tests/CoreTests.java"
java -cp "$CLASSES:$CP" es.cantolagua.misemana.CoreTests "$TASK_ROOT/tests/fixtures/calendar.html"

#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TASK_TOOLS="${CANTOLAGUA_TOOLS:-/workspace/.tools}"
CLASSES="$TASK_ROOT/android/build/tests"
mkdir -p "$CLASSES"
CP="$TASK_TOOLS/jsoup.jar:$TASK_TOOLS/json.jar"
SRC="$TASK_ROOT/android/app/src/main/java/es/cantolagua/misemana"
java -m jdk.compiler/com.sun.tools.javac.Main -cp "$CP" -d "$CLASSES" "$SRC/Plan.java" "$SRC/ClubClient.java" "$SRC/Timetable.java" "$TASK_ROOT/tests/CoreTests.java" "$TASK_ROOT/tests/TimetableTests.java" "$TASK_ROOT/tests/CatalogueFetchTests.java" "$TASK_ROOT/tests/BookingFlowTests.java"
java -cp "$CLASSES:$CP" es.cantolagua.misemana.CoreTests "$TASK_ROOT/tests/fixtures/calendar.html"

java -cp "$CLASSES:$CP" es.cantolagua.misemana.TimetableTests "$TASK_ROOT/tests/fixtures/calendar-week.html"

java -cp "$CLASSES:$CP" es.cantolagua.misemana.CatalogueFetchTests "$TASK_ROOT/tests/fixtures"
java -cp "$CLASSES:$CP" es.cantolagua.misemana.BookingFlowTests

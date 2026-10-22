#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TASK_TOOLS="${CANTOLAGUA_TOOLS:-/workspace/.tools}"
SDK="$TASK_TOOLS/android-sdk";BT="$SDK/build-tools/35.0.0";BUILD="$TASK_ROOT/android/build/device-tests"
SIGNING="${CANTOLAGUA_SIGNING_DIR:-/workspace/.cantolagua-android-signing}"
mkdir -p "$BUILD/classes" "$BUILD/dex"
mkdir -p "$BUILD/assets"
cp "$TASK_ROOT"/tests/fixtures/* "$BUILD/assets/"
rm -f "$BUILD/assets/runtime-proxy-ca.crt"
if [ -n "${CANTOLAGUA_TEST_CA_FILE:-}" ]; then
    cp "$CANTOLAGUA_TEST_CA_FILE" "$BUILD/assets/runtime-proxy-ca.crt"
fi

java -m jdk.compiler/com.sun.tools.javac.Main -source 8 -target 8 -Xlint:-options -bootclasspath "$SDK/platforms/android-35/android.jar:$BT/core-lambda-stubs.jar" -cp "$TASK_ROOT/android/build/classes:$TASK_TOOLS/jsoup.jar" -d "$BUILD/classes" "$TASK_ROOT/tests/DeviceChecks.java" "$TASK_ROOT/tests/CalendarDeviceChecks.java" "$TASK_ROOT/tests/UpgradeSeed.java" "$TASK_ROOT/tests/NetworkDeviceChecks.java" "$TASK_ROOT/tests/BookingFlowChecks.java"
java -m jdk.jartool/sun.tools.jar.Main cf "$BUILD/tests.jar" -C "$BUILD/classes" .
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --lib "$SDK/platforms/android-35/android.jar" --classpath "$TASK_ROOT/android/build/classes.jar" --min-api 26 --output "$BUILD/dex" "$BUILD/tests.jar"
"$BT/aapt2" link -I "$SDK/platforms/android-35/android.jar" --manifest "$TASK_ROOT/tests/AndroidManifest.xml" -o "$BUILD/unsigned.apk" --min-sdk-version 26 --target-sdk-version 35 -A "$BUILD/assets"
python3 - "$BUILD" <<'PY'
import zipfile,pathlib,sys
p=pathlib.Path(sys.argv[1])
with zipfile.ZipFile(p/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:z.write(p/'dex/classes.dex','classes.dex')
PY
"$BT/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
"$BT/apksigner" sign --ks "$SIGNING/release.p12" --ks-key-alias cantolagua --ks-pass "file:$SIGNING/password" --out "$BUILD/device-tests.apk" "$BUILD/aligned.apk"

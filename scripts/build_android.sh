#!/usr/bin/env bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TASK_TOOLS="${CANTOLAGUA_TOOLS:-/workspace/.tools}"
SDK="$TASK_TOOLS/android-sdk"
BT="$SDK/build-tools/35.0.0"
SRC="$TASK_ROOT/android/app/src/main"
BUILD="$TASK_ROOT/android/build"
SIGNING="${CANTOLAGUA_SIGNING_DIR:-/workspace/.cantolagua-android-signing}"
python3 "$TASK_ROOT/scripts/bootstrap_android.py"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$TASK_ROOT/artifacts"
rm -rf "$BUILD/classes" "$BUILD/dex" "$BUILD/gen"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"
"$BT/aapt2" compile --dir "$SRC/res" -o "$BUILD/resources.zip"
"$BT/aapt2" link -I "$SDK/platforms/android-35/android.jar" --manifest "$SRC/AndroidManifest.xml" --java "$BUILD/gen" -o "$BUILD/resources.apk" --min-sdk-version 26 --target-sdk-version 35 --version-code 6 --version-name 1.0.5 -A "$SRC/assets" "$BUILD/resources.zip"
find "$SRC/java" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
java -m jdk.compiler/com.sun.tools.javac.Main -source 8 -target 8 -Xlint:-options -bootclasspath "$SDK/platforms/android-35/android.jar:$BT/core-lambda-stubs.jar" -classpath "$TASK_TOOLS/jsoup.jar" -d "$BUILD/classes" @"$BUILD/sources.txt"
java -m jdk.jartool/sun.tools.jar.Main cf "$BUILD/classes.jar" -C "$BUILD/classes" .
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --lib "$SDK/platforms/android-35/android.jar" --min-api 26 --output "$BUILD/dex" "$BUILD/classes.jar" "$TASK_TOOLS/jsoup.jar"
python3 - "$BUILD" <<'PY'
import pathlib,sys,shutil,zipfile
p=pathlib.Path(sys.argv[1]);shutil.copyfile(p/'resources.apk',p/'unsigned.apk')
with zipfile.ZipFile(p/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
    for dex in (p/'dex').glob('*.dex'):z.write(dex,dex.name)
PY
"$BT/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
umask 077
mkdir -p "$SIGNING"
if [ ! -f "$SIGNING/release.p12" ]; then
    python3 - "$SIGNING/password" <<'PY'
import secrets,pathlib,sys
pathlib.Path(sys.argv[1]).write_text(secrets.token_urlsafe(36))
PY
    keytool -genkeypair -noprompt -keystore "$SIGNING/release.p12" -storetype PKCS12 -alias cantolagua -keyalg RSA -keysize 3072 -validity 10000 -storepass:file "$SIGNING/password" -keypass:file "$SIGNING/password" -dname 'CN=Mi Cantolagua, O=AppReservaCantolagua, C=ES'
fi
APK="$TASK_ROOT/artifacts/MiCantolagua-1.0.5.apk"
"$BT/apksigner" sign --ks "$SIGNING/release.p12" --ks-key-alias cantolagua --ks-pass "file:$SIGNING/password" --out "$APK" "$BUILD/aligned.apk"
"$BT/apksigner" verify --verbose "$APK"
chmod 644 "$APK"
(cd "$TASK_ROOT/artifacts" && sha256sum MiCantolagua-1.0.5.apk > MiCantolagua-1.0.5.apk.sha256)
echo "APK generado: $APK"

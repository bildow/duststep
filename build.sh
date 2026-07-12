#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BUILD_TOOLS="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-35/android.jar"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
KEYSTORE="$ROOT/build/debug.keystore"

rm -rf "$ROOT/build"
mkdir -p "$ROOT/build/compiled" "$ROOT/build/generated" "$ROOT/build/classes" "$ROOT/build/dex"

"$BUILD_TOOLS/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$ROOT/build/compiled/res.zip"

"$BUILD_TOOLS/aapt2" link \
  -I "$PLATFORM" \
  --manifest "$ROOT/app/src/main/AndroidManifest.xml" \
  --java "$ROOT/build/generated" \
  --min-sdk-version 26 \
  --target-sdk-version 35 \
  -o "$ROOT/build/duststep-unsigned.apk" \
  "$ROOT/build/compiled/res.zip"

"$JAVA_HOME/bin/javac" \
  -source 11 -target 11 \
  -classpath "$PLATFORM" \
  -d "$ROOT/build/classes" \
  $(find "$ROOT/build/generated" "$ROOT/app/src/main/java" -name '*.java' | sort)

"$BUILD_TOOLS/d8" \
  --min-api 26 \
  --output "$ROOT/build/dex" \
  $(find "$ROOT/build/classes" -name '*.class' | sort)

cp "$ROOT/build/duststep-unsigned.apk" "$ROOT/build/duststep-with-dex.apk"
(cd "$ROOT/build/dex" && zip -q -r "$ROOT/build/duststep-with-dex.apk" classes.dex)

"$BUILD_TOOLS/zipalign" -f 4 "$ROOT/build/duststep-with-dex.apk" "$ROOT/build/duststep-aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -v \
    -keystore "$KEYSTORE" \
    -storepass android \
    -alias androiddebugkey \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Dustforge,C=US" >/dev/null
fi

"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$ROOT/build/duststep-debug.apk" \
  "$ROOT/build/duststep-aligned.apk"

"$BUILD_TOOLS/apksigner" verify "$ROOT/build/duststep-debug.apk"
echo "Built $ROOT/build/duststep-debug.apk"

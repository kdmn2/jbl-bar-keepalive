#!/usr/bin/env bash
set -euo pipefail

# Standalone Android APK build script (no Gradle required)
# Automatically downloads minimal Android build-tools & platform-34 if ANDROID_HOME is not set.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="${ANDROID_HOME:-$ROOT_DIR/.sdk}"
BUILD_TOOLS="$SDK_DIR/android-14"
PLATFORM="$SDK_DIR/android-34"
OUT_APK="$ROOT_DIR/JBLBarKeepAlive.apk"

if [[ ! -x "$BUILD_TOOLS/aapt2" || ! -f "$PLATFORM/android.jar" ]]; then
    echo "==> Downloading minimal Android SDK build-tools (r34) & platform (34)..."
    mkdir -p "$SDK_DIR"
    pushd "$SDK_DIR" >/dev/null
    [[ -f build-tools_r34-linux.zip ]] || curl -sLO "https://dl.google.com/android/repository/build-tools_r34-linux.zip"
    [[ -f platform-34-ext7_r03.zip ]] || curl -sLO "https://dl.google.com/android/repository/platform-34-ext7_r03.zip"
    unzip -q -o build-tools_r34-linux.zip
    unzip -q -o platform-34-ext7_r03.zip
    popd >/dev/null
fi

cd "$ROOT_DIR"
rm -rf build
mkdir -p build/compiled_res build/gen build/classes build/dex

echo "==> 1/6 Compiling resources with aapt2..."
"$BUILD_TOOLS/aapt2" compile --dir res -o build/compiled_res/res.zip

echo "==> 2/6 Linking resources and AndroidManifest.xml..."
"$BUILD_TOOLS/aapt2" link \
    -o build/unaligned.apk \
    -I "$PLATFORM/android.jar" \
    --manifest AndroidManifest.xml \
    --java build/gen \
    build/compiled_res/res.zip

echo "==> 3/6 Compiling Java sources..."
javac -source 8 -target 8 \
    -bootclasspath "$PLATFORM/android.jar:$BUILD_TOOLS/core-lambda-stubs.jar" \
    -d build/classes \
    src/com/kidman/jblkeepalive/*.java

echo "==> 4/6 Converting bytecode to DEX with d8..."
"$BUILD_TOOLS/d8" \
    --lib "$PLATFORM/android.jar" \
    --min-api 26 \
    --output build/dex \
    build/classes/com/kidman/jblkeepalive/*.class

echo "==> 5/6 Packaging and zipaligning APK..."
jar -uf build/unaligned.apk -C build/dex classes.dex
"$BUILD_TOOLS/zipalign" -f -p 4 build/unaligned.apk build/aligned.apk

echo "==> 6/6 Signing APK..."
KEYSTORE="build/release.keystore"
keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -alias jblkeepalive \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=JBLKeepAlive, OU=Audio, O=OpenSource, L=Earth, S=Earth, C=US" 2>/dev/null

"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias jblkeepalive \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$OUT_APK" \
    build/aligned.apk

"$BUILD_TOOLS/apksigner" verify "$OUT_APK"
echo "==> Done! Built signed APK: $OUT_APK ($(du -h "$OUT_APK" | cut -f1))"

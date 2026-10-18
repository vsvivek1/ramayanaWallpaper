#!/usr/bin/env bash
# Builds the APK without Gradle, using only the Android SDK build tools, a JDK and the
# Kotlin compiler jars. This is how the APK was built in an environment with no access
# to Google's Maven repository. In Android Studio, just use Gradle instead.
#
# Needs: ANDROID_HOME (with platforms/android-36 and build-tools/37.0.0),
#        KOTLIN_LIB (folder holding kotlin-compiler-embeddable and its dependency jars).
#
# Release bundle for Google Play (also needs bundletool and your upload key):
#   BUNDLETOOL=/path/bundletool.jar UPLOAD_KEYSTORE=upload.jks UPLOAD_ALIAS=upload \
#   UPLOAD_PASS=... ./build-local.sh
#   -> build-local/bhagavatham-wallpapers.aab
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:?set ANDROID_HOME}"
BT="$SDK/build-tools/${BUILD_TOOLS:-37.0.0}"
JAR="$SDK/platforms/android-36/android.jar"
KLIB="${KOTLIN_LIB:?set KOTLIN_LIB}"
STDLIB=$(ls "$KLIB"/kotlin-stdlib-2*.jar | head -1)
OUT=build-local
VERSION_CODE=1
VERSION_NAME=1.0

rm -rf "$OUT"; mkdir -p "$OUT"/{gen,classes,dex}

echo "1/6 Compiling resources"
"$BT/aapt2" compile --dir app/src/main/res -o "$OUT/res.zip"

echo "2/6 Linking resources and manifest"
# Gradle takes the package name from build.gradle.kts; aapt2 needs it in the manifest.
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.calecutech.bhagavatham">|' \
  app/src/main/AndroidManifest.xml > "$OUT/AndroidManifest.xml"
"$BT/aapt2" link -I "$JAR" \
  --manifest "$OUT/AndroidManifest.xml" \
  --min-sdk-version 26 --target-sdk-version 36 \
  --version-code $VERSION_CODE --version-name $VERSION_NAME \
  -A app/src/main/assets \
  --java "$OUT/gen" --custom-package com.calecutech.bhagavatham \
  -o "$OUT/base.apk" "$OUT/res.zip"

echo "3/6 Compiling R.java"
javac -nowarn -source 11 -target 11 -cp "$JAR" -d "$OUT/classes" $(find "$OUT/gen" -name '*.java') 2>&1 | grep -v "bootstrap classpath" || true

echo "4/6 Compiling Kotlin"
java -cp "$(ls "$KLIB"/*.jar | tr '\n' ':')" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -jvm-target 11 \
  -cp "$JAR:$STDLIB:$OUT/classes" \
  -d "$OUT/classes" $(find app/src/main/java -name '*.kt')

echo "5/6 Shrinking and converting to dex (R8)"
# R8 ships inside build-tools' d8.jar. It shrinks and obfuscates the code and writes
# mapping.txt, which is uploaded to Play with the bundle so crash reports stay readable.
(cd "$OUT/classes" && zip -q -r ../classes.jar .)
java -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 --release --min-api 26 --lib "$JAR" \
  --pg-conf app/proguard-rules.pro --pg-map-output "$OUT/mapping.txt" \
  --output "$OUT/dex" "$OUT/classes.jar" "$STDLIB"

echo "6/6 Packaging and signing"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -j ../unsigned.apk classes*.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KS="${KEYSTORE:-$HOME/.android/debug.keystore}"
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --out "$OUT/bhagavatham-wallpapers.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/bhagavatham-wallpapers.apk"
echo "Built $OUT/bhagavatham-wallpapers.apk"

if [ -n "${BUNDLETOOL:-}" ]; then
  echo "Release bundle"
  # Same resources linked in protobuf format, laid out as a bundle module.
  "$BT/aapt2" link --proto-format -I "$JAR" \
    --manifest "$OUT/AndroidManifest.xml" \
    --min-sdk-version 26 --target-sdk-version 36 \
    --version-code $VERSION_CODE --version-name $VERSION_NAME \
    -A app/src/main/assets -o "$OUT/proto.apk" "$OUT/res.zip"
  rm -rf "$OUT/module" "$OUT/proto"; mkdir -p "$OUT/module/manifest" "$OUT/module/dex" "$OUT/proto"
  (cd "$OUT/proto" && unzip -q ../proto.apk)
  mv "$OUT/proto/AndroidManifest.xml" "$OUT/module/manifest/"
  mv "$OUT/proto/resources.pb" "$OUT/proto/res" "$OUT/proto/assets" "$OUT/module/"
  cp "$OUT"/dex/classes*.dex "$OUT/module/dex/"
  (cd "$OUT/module" && zip -q -r ../base.zip .)
  rm -f "$OUT/bhagavatham-wallpapers.aab"
  java -jar "$BUNDLETOOL" build-bundle --modules="$OUT/base.zip" --output="$OUT/unsigned.aab"
  jarsigner -keystore "${UPLOAD_KEYSTORE:?set UPLOAD_KEYSTORE}" -storepass "${UPLOAD_PASS:?set UPLOAD_PASS}" \
    -sigalg SHA256withRSA -digestalg SHA-256 \
    -signedjar "$OUT/bhagavatham-wallpapers.aab" "$OUT/unsigned.aab" "${UPLOAD_ALIAS:-upload}" >/dev/null
  java -jar "$BUNDLETOOL" validate --bundle="$OUT/bhagavatham-wallpapers.aab" >/dev/null
  echo "Built $OUT/bhagavatham-wallpapers.aab (mapping: $OUT/mapping.txt)"
fi

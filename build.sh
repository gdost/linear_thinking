#!/bin/sh
# Builds a signed debug APK with the plain Android SDK tools (no Gradle).
set -e
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-/usr/lib/android-sdk}
JAR=$SDK/platforms/android-23/android.jar
rm -rf build && mkdir -p build/classes
aapt package -f -M AndroidManifest.xml -S res -I "$JAR" -J build -F build/app.unsigned.apk
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$JAR" \
  -d build/classes $(find build -name R.java) $(find src -name '*.java')
dalvik-exchange --dex --output=build/classes.dex build/classes
(cd build && aapt add app.unsigned.apk classes.dex >/dev/null)
zipalign -f 4 build/app.unsigned.apk build/app.aligned.apk
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out build/water-glass-debug.apk build/app.aligned.apk
echo "Built build/water-glass-debug.apk"

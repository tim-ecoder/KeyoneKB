#!/bin/bash
set -e

# K12KB manual APK build script
# Requires: JDK 11, Android SDK (platform 28, build-tools 28.0.3), dx tool
#
# Environment variables (override defaults as needed):
#   PROJECT     - project root (default: script's parent directory)
#   SDK         - Android SDK path (default: $HOME/android-sdk)
#   JAVAC       - javac path (default: $HOME/jdk11/bin/javac)
#   JARSIGNER   - jarsigner path (default: $HOME/jdk11/bin/jarsigner)
#   KEYTOOL     - keytool path (default: $HOME/jdk11/bin/keytool)
#   DX          - dx tool path (default: /usr/lib/android-sdk/build-tools/debian/dx)
#
# Required SDK libs (place in $SDK/libs/):
#   appcompat-v7-25.4.0.jar, support-v4-25.4.0.jar, support-compat-25.4.0.jar,
#   support-core-ui-25.4.0.jar, support-core-utils-25.4.0.jar,
#   support-fragment-25.4.0.jar, support-media-compat-25.4.0.jar,
#   support-annotations-25.4.0.jar, support-vector-drawable-25.4.0.jar,
#   animated-vector-drawable-25.4.0.jar, gson-2.8.7.jar,
#   jackson-core-2.13.0.jar, jackson-annotations-2.13.0.jar,
#   jackson-databind-2.13.0.jar, rhino-1.7.7.2.jar, rhino-android-1.6.0.jar
#
# IMPORTANT: Code must NOT use lambdas, method references, or streams.
#   Android 8.x (BB KEY2) dex will cause BootstrapMethodError at runtime.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT="${PROJECT:-$(dirname "$SCRIPT_DIR")}"
APP="$PROJECT/app"

SDK="${SDK:-$HOME/android-sdk}"
ANDROID_JAR="$SDK/platforms/android-28/android.jar"
AAPT2="$SDK/build-tools/28.0.3/aapt2"
ZIPALIGN="$SDK/build-tools/28.0.3/zipalign"
DX="${DX:-/usr/lib/android-sdk/build-tools/debian/dx}"
JAVAC="${JAVAC:-$HOME/jdk11/bin/javac}"
JARSIGNER="${JARSIGNER:-$HOME/jdk11/bin/jarsigner}"
KEYTOOL="${KEYTOOL:-$HOME/jdk11/bin/keytool}"

BUILD="$PROJECT/build_manual"
LIBS="$SDK/libs"

# Version info — update these for each release
VERSION_CODE=2752
VERSION_NAME="v2.8b12s"

# Validate tools exist
for tool in "$AAPT2" "$ZIPALIGN" "$DX" "$JAVAC" "$JARSIGNER"; do
    if [ ! -f "$tool" ]; then
        echo "ERROR: Tool not found: $tool"
        exit 1
    fi
done

if [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: android.jar not found: $ANDROID_JAR"
    exit 1
fi

rm -rf "$BUILD"
mkdir -p "$BUILD"/{compiled,linked,gen,classes,aidl_gen,dex}

echo "=== Step 1: AIDL ==="
mkdir -p "$BUILD/aidl_gen/com/android/internal/telephony"
cat > "$BUILD/aidl_gen/com/android/internal/telephony/ITelephony.java" << 'AIDLJAVA'
package com.android.internal.telephony;
public interface ITelephony {
    boolean endCall();
    void answerRingingCall();
    void silenceRinger();
}
AIDLJAVA

echo "=== Step 2: Compile resources ==="
find "$APP/src/main/res" -type d -mindepth 1 -maxdepth 1 | while read dir; do
    find "$dir" -type f | while read file; do
        $AAPT2 compile "$file" -o "$BUILD/compiled/" 2>/dev/null || true
    done
done
echo "  $(ls "$BUILD/compiled/" | wc -l) resource files"

echo "=== Step 3: Link resources ==="
$AAPT2 link \
    -o "$BUILD/linked/app-unsigned.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$APP/src/main/AndroidManifest.xml" \
    --java "$BUILD/gen" \
    --auto-add-overlay \
    --min-sdk-version 26 \
    --target-sdk-version 27 \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
    -A "$APP/src/main/assets" \
    "$BUILD"/compiled/*.flat

echo "=== Step 4: BuildConfig ==="
mkdir -p "$BUILD/gen/com/ai10/k12kb"
cat > "$BUILD/gen/com/ai10/k12kb/BuildConfig.java" << BCJAVA
package com.ai10.k12kb;
public final class BuildConfig {
    public static final boolean DEBUG = true;
    public static final String APPLICATION_ID = "com.ai10.k12kb";
    public static final String BUILD_TYPE = "debug";
    public static final int VERSION_CODE = $VERSION_CODE;
    public static final String VERSION_NAME = "$VERSION_NAME";
}
BCJAVA

echo "=== Step 5: Compile Java ==="
CP="$ANDROID_JAR"
for jar in "$LIBS"/*.jar; do CP="$CP:$jar"; done

find "$APP/src/main/java" -name "*.java" > "$BUILD/sources.txt"
find "$BUILD/gen" -name "*.java" >> "$BUILD/sources.txt"
find "$BUILD/aidl_gen" -name "*.java" >> "$BUILD/sources.txt"

echo "  $(wc -l < "$BUILD/sources.txt") Java files"
$JAVAC -source 1.8 -target 1.8 -encoding UTF-8 -classpath "$CP" -d "$BUILD/classes" @"$BUILD/sources.txt" 2>&1

echo "=== Step 6: DEX ==="
$DX --dex --min-sdk-version=26 --output="$BUILD/dex/classes.dex" "$BUILD/classes" "$LIBS"/*.jar 2>&1

echo "=== Step 7: Package ==="
cp "$BUILD/linked/app-unsigned.apk" "$BUILD/app-unsigned.apk"
zip -j -u "$BUILD/app-unsigned.apk" "$BUILD/dex/classes.dex"

echo "=== Step 8: Align ==="
$ZIPALIGN -f 4 "$BUILD/app-unsigned.apk" "$BUILD/app-aligned.apk"

echo "=== Step 9: Sign ==="
if [ ! -f "$PROJECT/debug.keystore" ]; then
    echo "  Generating new signing keystore..."
    $KEYTOOL -genkey -v -keystore "$PROJECT/debug.keystore" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" 2>&1
else
    echo "  Reusing existing keystore"
fi

$JARSIGNER -keystore "$PROJECT/debug.keystore" -storepass android -keypass android -signedjar "$BUILD/app-signed.apk" "$BUILD/app-aligned.apk" androiddebugkey 2>&1

OUTPUT_APK="$PROJECT/K12KB-${VERSION_NAME}.apk"
cp "$BUILD/app-signed.apk" "$OUTPUT_APK"
SIZE=$(stat -c%s "$OUTPUT_APK")
echo ""
echo "=== BUILD COMPLETE ==="
echo "Output: $(basename "$OUTPUT_APK") ($SIZE bytes / $(echo "scale=1; $SIZE/1048576" | bc) MB)"

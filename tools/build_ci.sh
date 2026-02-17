#!/bin/bash
set -e

# K12KB CI APK build script
# Uses system Android SDK tools (aapt, dx, javac, jarsigner)
# Support libraries downloaded from GitHub (dandar3) and Maven Central

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT="${PROJECT:-$(dirname "$SCRIPT_DIR")}"
APP="$PROJECT/app"

SDK="${SDK:-/usr/lib/android-sdk}"
ANDROID_JAR="$SDK/platforms/android-23/android.jar"
DX="${DX:-/usr/lib/android-sdk/build-tools/debian/dx}"
JAVAC="${JAVAC:-$(which javac)}"
JARSIGNER="${JARSIGNER:-$(which jarsigner)}"
KEYTOOL="${KEYTOOL:-$(which keytool)}"
AAPT="${AAPT:-$(which aapt)}"

BUILD="$PROJECT/build_ci"
LIBS="$BUILD/libs"

# Version info (must match app/build.gradle)
VERSION_CODE=2746
VERSION_NAME="v2.8b07s"

echo "=== K12KB CI Build ==="
echo "Project: $PROJECT"
echo "Java: $($JAVAC -version 2>&1)"
echo "aapt: $($AAPT version 2>&1)"
echo "dx: $($DX --version 2>&1 || echo 'unknown')"

# Validate tools
for tool in "$DX" "$JAVAC" "$JARSIGNER" "$AAPT"; do
    if [ ! -f "$tool" ] && ! which "$tool" >/dev/null 2>&1; then
        echo "ERROR: Tool not found: $tool"
        exit 1
    fi
done

if [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: android.jar not found: $ANDROID_JAR"
    exit 1
fi

# Clean build dir (preserve libs cache)
rm -rf "$BUILD/gen" "$BUILD/classes" "$BUILD/aidl_gen" "$BUILD/compiled"
mkdir -p "$BUILD"/{gen,classes,aidl_gen} "$LIBS"

echo ""
echo "=== Step 1: Download dependencies ==="
download_jar() {
    local name="$1" url="$2" dest="$LIBS/$1"
    if [ -f "$dest" ] && [ "$(stat -c%s "$dest")" -gt 100 ]; then
        echo "  [cached] $name ($(stat -c%s "$dest") bytes)"
        return 0
    fi
    echo -n "  Downloading $name... "
    if curl -sL -o "$dest" "$url" && file "$dest" | grep -q "archive\|Java\|Zip"; then
        echo "OK ($(stat -c%s "$dest") bytes)"
    else
        echo "FAILED"
        rm -f "$dest"
        return 1
    fi
}

# Maven Central dependencies
download_jar "gson-2.8.7.jar" "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.8.7/gson-2.8.7.jar"
download_jar "jackson-core-2.13.0.jar" "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.13.0/jackson-core-2.13.0.jar"
download_jar "jackson-annotations-2.13.0.jar" "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.13.0/jackson-annotations-2.13.0.jar"
download_jar "jackson-databind-2.13.0.jar" "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.13.0/jackson-databind-2.13.0.jar"
download_jar "rhino-1.7.7.2.jar" "https://repo1.maven.org/maven2/org/mozilla/rhino/1.7.7.2/rhino-1.7.7.2.jar"

# rhino-android: download AAR and extract classes.jar
if [ ! -f "$LIBS/rhino-android-1.6.0.jar" ] || [ "$(stat -c%s "$LIBS/rhino-android-1.6.0.jar")" -lt 100 ]; then
    echo -n "  Downloading rhino-android-1.6.0... "
    TMPAAR="$BUILD/rhino-android.aar"
    curl -sL -o "$TMPAAR" "https://repo1.maven.org/maven2/com/faendir/rhino/rhino-android/1.6.0/rhino-android-1.6.0.aar"
    mkdir -p "$BUILD/aar_extract"
    (cd "$BUILD/aar_extract" && unzip -q -o "$TMPAAR" classes.jar 2>/dev/null)
    cp "$BUILD/aar_extract/classes.jar" "$LIBS/rhino-android-1.6.0.jar"
    echo "OK ($(stat -c%s "$LIBS/rhino-android-1.6.0.jar") bytes)"
else
    echo "  [cached] rhino-android-1.6.0.jar"
fi

# Android Support Libraries from GitHub (dandar3 repos)
download_jar "android-support-v7-appcompat.jar" "https://raw.githubusercontent.com/dandar3/android-support-v7-appcompat/25.4.0/libs/android-support-v7-appcompat.jar"
download_jar "android-support-compat.jar" "https://raw.githubusercontent.com/dandar3/android-support-compat/25.4.0/libs/android-support-compat.jar"
download_jar "android-support-core-utils.jar" "https://raw.githubusercontent.com/dandar3/android-support-core-utils/25.4.0/libs/android-support-core-utils.jar"
download_jar "android-support-annotations.jar" "https://raw.githubusercontent.com/dandar3/android-support-annotations/25.4.0/libs/android-support-annotations.jar"

echo ""
echo "=== Step 2: Create API 26+ framework stubs ==="
# The android.jar from API 23 lacks some API 24-28 classes used by the project
# Create minimal stubs for compilation only (runtime will use real device APIs)
FSTUBS="$BUILD/framework_stubs"
mkdir -p "$FSTUBS"

# NotificationChannel (API 26)
mkdir -p "$FSTUBS/android/app"
cat > "$FSTUBS/android/app/NotificationChannel.java" << 'JAVA'
package android.app;
import android.net.Uri;
public class NotificationChannel {
    public static final int IMPORTANCE_LOW = 2;
    public static final int IMPORTANCE_DEFAULT = 3;
    public NotificationChannel(String id, CharSequence name, int importance) {}
    public void setDescription(String description) {}
    public void enableLights(boolean lights) {}
    public void enableVibration(boolean vibration) {}
    public void setShowBadge(boolean showBadge) {}
    public void setSound(Uri sound, Object attributes) {}
}
JAVA

# VibrationEffect (API 26)
cat > "$FSTUBS/android/os/VibrationEffect.java" << 'JAVA'
package android.os;
public abstract class VibrationEffect {
    public static final int DEFAULT_AMPLITUDE = -1;
    public static VibrationEffect createOneShot(long milliseconds, int amplitude) { return null; }
    public static VibrationEffect createWaveform(long[] timings, int repeat) { return null; }
}
JAVA

# GestureDescription (API 24) and GestureResultCallback
mkdir -p "$FSTUBS/android/accessibilityservice"
cat > "$FSTUBS/android/accessibilityservice/GestureDescription.java" << 'JAVA'
package android.accessibilityservice;
import android.graphics.Path;
public class GestureDescription {
    public static class Builder {
        public Builder() {}
        public Builder addStroke(StrokeDescription stroke) { return this; }
        public GestureDescription build() { return new GestureDescription(); }
    }
    public static class StrokeDescription {
        public StrokeDescription(Path path, long startTime, long duration) {}
        public StrokeDescription(Path path, long startTime, long duration, boolean willContinue) {}
    }
}
JAVA

# AccessibilityService.GestureResultCallback (API 24) - inner class stub
# We put it as a separate top-level class since we can't extend the framework AccessibilityService
# The reflection-based call in the patched source handles this

# ShortcutInfo (API 25)
mkdir -p "$FSTUBS/android/content/pm"
cat > "$FSTUBS/android/content/pm/ShortcutInfo.java" << 'JAVA'
package android.content.pm;
import android.content.Intent;
import android.graphics.drawable.Icon;
public class ShortcutInfo {
    public static class Builder {
        public Builder(android.content.Context context, String id) {}
        public Builder setShortLabel(CharSequence label) { return this; }
        public Builder setLongLabel(CharSequence label) { return this; }
        public Builder setIcon(Icon icon) { return this; }
        public Builder setIntent(Intent intent) { return this; }
        public Builder setIntents(Intent[] intents) { return this; }
        public Builder setRank(int rank) { return this; }
        public ShortcutInfo build() { return null; }
    }
}
JAVA

# ShortcutManager (API 25)
cat > "$FSTUBS/android/content/pm/ShortcutManager.java" << 'JAVA'
package android.content.pm;
import java.util.List;
public class ShortcutManager {
    public void setDynamicShortcuts(List<ShortcutInfo> shortcuts) {}
    public void removeAllDynamicShortcuts() {}
}
JAVA

# Build.VERSION_CODES additions (O = API 26, N = API 24, etc.)
mkdir -p "$FSTUBS/android/os"
cat > "$FSTUBS/android/os/BuildVersionCodes.java" << 'JAVA'
package android.os;
// Stub to add VERSION_CODES.O etc. - already in API 23 as Build.VERSION_CODES
// but these constants are added at compile time, so we just need them to resolve
JAVA

# Compile framework stubs
echo "  Compiling framework stubs..."
find "$FSTUBS" -name "*.java" > "$BUILD/fstub_sources.txt"
NSTUBS=$(wc -l < "$BUILD/fstub_sources.txt")
echo "  $NSTUBS stub source files"
mkdir -p "$BUILD/fstub_classes"
$JAVAC -source 1.8 -target 1.8 -classpath "$ANDROID_JAR" \
    -d "$BUILD/fstub_classes" \
    @"$BUILD/fstub_sources.txt" 2>&1 || true
(cd "$BUILD/fstub_classes" && jar cf "$LIBS/framework-stubs.jar" . 2>/dev/null)
echo "  Created framework-stubs.jar"

echo ""
echo "=== Step 2b: Generate AIDL interfaces ==="
mkdir -p "$BUILD/aidl_gen/com/android/internal/telephony"
cat > "$BUILD/aidl_gen/com/android/internal/telephony/ITelephony.java" << 'AIDLJAVA'
package com.android.internal.telephony;
public interface ITelephony {
    boolean endCall();
    void answerRingingCall();
    void silenceRinger();
}
AIDLJAVA

echo ""
echo "=== Step 3: Patch resources and manifest for API 23 compatibility ==="
# Copy res to temp dir and remove attributes not in API 23
RES_PATCHED="$BUILD/res_patched"
rm -rf "$RES_PATCHED"
cp -r "$APP/src/main/res" "$RES_PATCHED"

# Fix SeekBar android:min (API 26+) - remove the attribute
sed -i 's/android:min="[^"]*"//g' "$RES_PATCHED/layout/activity_prediction_settings.xml"

# Fix accessibility config android:canPerformGestures (API 26+)
sed -i 's/android:canPerformGestures="[^"]*"//g' "$RES_PATCHED/xml/k12kb_accessibility_service_config.xml"

# Fix vector drawable API 24+ attributes (gradient attrs, fillType)
rm -rf "$RES_PATCHED/drawable-v24"

# Patch AndroidManifest.xml - remove android:roundIcon (API 25) and android:directBootAware (API 24)
MANIFEST_PATCHED="$BUILD/AndroidManifest.xml"
cp "$APP/src/main/AndroidManifest.xml" "$MANIFEST_PATCHED"
sed -i 's/android:roundIcon="[^"]*"//g' "$MANIFEST_PATCHED"
sed -i 's/android:directBootAware="[^"]*"//g' "$MANIFEST_PATCHED"

echo "  Patched resource files and manifest for API 23 aapt"

echo ""
echo "=== Step 4: Package resources with aapt ==="
$AAPT package -f -m \
    -J "$BUILD/gen" \
    -M "$MANIFEST_PATCHED" \
    -S "$RES_PATCHED" \
    -I "$ANDROID_JAR" \
    --auto-add-overlay \
    --min-sdk-version 26 \
    --target-sdk-version 27 \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" 2>&1 || {
    echo "WARNING: aapt resource packaging had issues, continuing..."
}
echo "  R.java generated: $(find "$BUILD/gen" -name "R.java" | wc -l) files"

echo ""
echo "=== Step 5: BuildConfig ==="
mkdir -p "$BUILD/gen/com/ai10/k12kb"
cat > "$BUILD/gen/com/ai10/k12kb/BuildConfig.java" << BCJAVA
package com.ai10.k12kb;
public final class BuildConfig {
    public static final boolean DEBUG = false;
    public static final String APPLICATION_ID = "com.ai10.k12kb";
    public static final String BUILD_TYPE = "release";
    public static final int VERSION_CODE = $VERSION_CODE;
    public static final String VERSION_NAME = "$VERSION_NAME";
}
BCJAVA

echo ""
echo "=== Step 5b: Patch Java sources for API 23 compatibility ==="
# Copy Java sources and patch API 26+ references
SRC_PATCHED="$BUILD/src_patched"
rm -rf "$SRC_PATCHED"
cp -r "$APP/src/main/java" "$SRC_PATCHED"

# Replace Build.VERSION_CODES.O with literal 26
find "$SRC_PATCHED" -name "*.java" -exec sed -i 's/Build\.VERSION_CODES\.O\b/26/g' {} +
# Replace Build.VERSION_CODES.N with literal 24
find "$SRC_PATCHED" -name "*.java" -exec sed -i 's/Build\.VERSION_CODES\.N\b/24/g' {} +
# Replace Build.VERSION_CODES.N_MR1 with literal 25
find "$SRC_PATCHED" -name "*.java" -exec sed -i 's/Build\.VERSION_CODES\.N_MR1/25/g' {} +
# Replace Build.VERSION_CODES.P with literal 28
find "$SRC_PATCHED" -name "*.java" -exec sed -i 's/Build\.VERSION_CODES\.P\b/28/g' {} +

# Fix NotificationManager.IMPORTANCE_DEFAULT -> use integer 3
find "$SRC_PATCHED" -name "*.java" -exec sed -i 's/NotificationManager\.IMPORTANCE_DEFAULT/3/g' {} +
find "$SRC_PATCHED" -name "*.java" -exec sed -i 's/NotificationManager\.IMPORTANCE_LOW/2/g' {} +
find "$SRC_PATCHED" -name "*.java" -exec sed -i 's/NotificationManager\.IMPORTANCE_HIGH/4/g' {} +

# Patch dispatchGesture: wrap in try/reflection since it doesn't exist in API 23's AccessibilityService
python3 - "$SRC_PATCHED/com/ai10/k12kb/K12KbAccessibilityService.java" << 'PYEOF'
import sys, re
filepath = sys.argv[1]
with open(filepath, 'r') as f:
    content = f.read()
# Replace dispatchGesture(...) with reflective call
content = content.replace(
    'dispatchGesture(clickBuilder.build(), null, null);',
    '''try {
                    java.lang.reflect.Method[] methods = getClass().getMethods();
                    for (java.lang.reflect.Method m : methods) {
                        if (m.getName().equals("dispatchGesture")) {
                            m.invoke(this, clickBuilder.build(), null, null);
                            break;
                        }
                    }
                } catch (Exception _e) { _e.printStackTrace(); }'''
)
# Replace createNotificationChannel calls in any file
with open(filepath, 'w') as f:
    f.write(content)
PYEOF

# Patch NotificationProcessor: createNotificationChannel via reflection
python3 - "$SRC_PATCHED/com/ai10/k12kb/NotificationProcessor.java" << 'PYEOF'
import sys, re
filepath = sys.argv[1]
with open(filepath, 'r') as f:
    content = f.read()
# Replace notificationManager.createNotificationChannel(xxx) with reflection
content = re.sub(
    r'notificationManager\.createNotificationChannel\((\w+)\);',
    r'try { notificationManager.getClass().getMethod("createNotificationChannel", android.app.NotificationChannel.class).invoke(notificationManager, \1); } catch (Exception _e) { _e.printStackTrace(); }',
    content
)
with open(filepath, 'w') as f:
    f.write(content)
PYEOF

# Patch Notification.Builder(context, channelId) -> use reflection for 2-arg constructor (API 26+)
# The 2-arg constructor is API 26+ and not in API 23 android.jar,
# so we use reflection to call it, preserving the channel ID for proper notification display.
python3 - "$SRC_PATCHED/com/ai10/k12kb/NotificationProcessor.java" << 'PYEOF'
import sys
filepath = sys.argv[1]
with open(filepath, 'r') as f:
    content = f.read()
# Replace 2-arg Notification.Builder with reflection that passes channelId
content = content.replace(
    'builder2Layout = new Notification.Builder(context, layoutModeChannelId1);',
    'try { builder2Layout = (Notification.Builder) Notification.Builder.class.getConstructor(Context.class, String.class).newInstance(context, layoutModeChannelId1); } catch (Exception _nb) { builder2Layout = new Notification.Builder(context); }'
)
content = content.replace(
    'builder2Gesture = new Notification.Builder(context, gestureModeChannelId1);',
    'try { builder2Gesture = (Notification.Builder) Notification.Builder.class.getConstructor(Context.class, String.class).newInstance(context, gestureModeChannelId1); } catch (Exception _nb) { builder2Gesture = new Notification.Builder(context); }'
)
with open(filepath, 'w') as f:
    f.write(content)
PYEOF

# Patch Manifest.permission.ANSWER_PHONE_CALLS (API 26) -> use string literal
find "$SRC_PATCHED" -name "*.java" -exec sed -i 's/Manifest\.permission\.ANSWER_PHONE_CALLS/"android.permission.ANSWER_PHONE_CALLS"/g' {} +

# Patch TelecomManager.acceptRingingCall() and endCall() - API 26
# Replace with reflection calls
python3 - "$SRC_PATCHED/com/ai10/k12kb/InputMethodServiceCoreCustomizable.java" << 'PYEOF'
import sys
filepath = sys.argv[1]
with open(filepath, 'r') as f:
    content = f.read()
# Replace telecomManager.acceptRingingCall() with reflection
content = content.replace(
    'telecomManager.acceptRingingCall();',
    'try { telecomManager.getClass().getMethod("acceptRingingCall").invoke(telecomManager); } catch (Exception _e) { _e.printStackTrace(); }'
)
# Replace telecomManager.endCall() with reflection
content = content.replace(
    'return telecomManager.endCall();',
    'try { return (Boolean) telecomManager.getClass().getMethod("endCall").invoke(telecomManager); } catch (Exception _e) { _e.printStackTrace(); return false; }'
)
# Replace requestShowSelf with reflection (API 25)
content = content.replace(
    'this.requestShowSelf(InputMethodManager.SHOW_IMPLICIT);',
    'try { this.getClass().getMethod("requestShowSelf", int.class).invoke(this, InputMethodManager.SHOW_IMPLICIT); } catch (Exception _e) { _e.printStackTrace(); }'
)
with open(filepath, 'w') as f:
    f.write(content)
PYEOF

# Patch K12KbIME.java requestShowSelf
python3 - "$SRC_PATCHED/com/ai10/k12kb/K12KbIME.java" << 'PYEOF'
import sys
filepath = sys.argv[1]
with open(filepath, 'r') as f:
    content = f.read()
content = content.replace(
    'this.requestShowSelf(InputMethodManager.SHOW_IMPLICIT);',
    'try { this.getClass().getMethod("requestShowSelf", int.class).invoke(this, InputMethodManager.SHOW_IMPLICIT); } catch (Exception _e) { _e.printStackTrace(); }'
)
with open(filepath, 'w') as f:
    f.write(content)
PYEOF

# Patch AccessibilityService.disableSelf() (API 24) with reflection
python3 - "$SRC_PATCHED/com/ai10/k12kb/K12KbAccessibilityService.java" << 'PYEOF'
import sys
filepath = sys.argv[1]
with open(filepath, 'r') as f:
    content = f.read()
content = content.replace(
    'disableSelf();',
    'try { getClass().getMethod("disableSelf").invoke(this); } catch (Exception _e) { _e.printStackTrace(); }'
)
with open(filepath, 'w') as f:
    f.write(content)
PYEOF

# Patch Vibrator.vibrate(VibrationEffect) -> use reflection for API 26+
python3 - "$SRC_PATCHED/com/ai10/k12kb/InputMethodServiceCoreCustomizable.java" << 'PYEOF'
import sys
filepath = sys.argv[1]
with open(filepath, 'r') as f:
    content = f.read()
content = content.replace(
    'vibratorService.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));',
    'try { Object ve = Class.forName("android.os.VibrationEffect").getMethod("createOneShot", long.class, int.class).invoke(null, ms, -1); vibratorService.getClass().getMethod("vibrate", Class.forName("android.os.VibrationEffect")).invoke(vibratorService, ve); } catch (Exception _e) { vibratorService.vibrate(ms); }'
)
with open(filepath, 'w') as f:
    f.write(content)
PYEOF

# Patch PillBadgeHelper return type issue
python3 - "$SRC_PATCHED/com/ai10/k12kb/PillBadgeHelper.java" << 'PYEOF'
import sys
filepath = sys.argv[1]
with open(filepath, 'r') as f:
    content = f.read()
# Cast findViewWithTag result to TextView
content = content.replace(
    'return pill.findViewWithTag(BADGE_TAG);',
    'return (android.widget.TextView) pill.findViewWithTag(BADGE_TAG);'
)
with open(filepath, 'w') as f:
    f.write(content)
PYEOF

echo "  Patched VERSION_CODES, dispatchGesture, NotificationChannel, and more"

echo ""
echo "=== Step 6: Compile Java ==="
CP="$ANDROID_JAR"
for jar in "$LIBS"/*.jar; do CP="$CP:$jar"; done

find "$SRC_PATCHED" -name "*.java" > "$BUILD/sources.txt"
find "$BUILD/gen" -name "*.java" >> "$BUILD/sources.txt"
find "$BUILD/aidl_gen" -name "*.java" >> "$BUILD/sources.txt"

NSOURCES=$(wc -l < "$BUILD/sources.txt")
echo "  $NSOURCES Java source files"
mkdir -p "$BUILD/classes"
$JAVAC -source 1.8 -target 1.8 -encoding UTF-8 \
    -classpath "$CP" \
    -d "$BUILD/classes" \
    -Xlint:-options \
    @"$BUILD/sources.txt" 2>&1 || {
    echo "WARNING: Some compilation warnings, checking for critical errors..."
    $JAVAC -source 1.8 -target 1.8 -encoding UTF-8 \
        -classpath "$CP" \
        -d "$BUILD/classes" \
        -nowarn \
        @"$BUILD/sources.txt" 2>&1
}

echo "  Compiled classes: $(find "$BUILD/classes" -name "*.class" | wc -l)"

echo ""
echo "=== Step 7: DEX ==="
# Some JARs (e.g. Jackson) have META-INF/versions/11/module-info.class that dx can't handle
# Strip them first
for jar in "$LIBS"/*.jar; do
    zip -q -d "$jar" 'META-INF/versions/*' 2>/dev/null || true
    zip -q -d "$jar" 'module-info.class' 2>/dev/null || true
done
$DX --dex --min-sdk-version=26 --output="$BUILD/classes.dex" "$BUILD/classes" "$LIBS"/*.jar 2>&1

echo ""
echo "=== Step 8: Package APK ==="
$AAPT package -f \
    -M "$MANIFEST_PATCHED" \
    -S "$RES_PATCHED" \
    -A "$APP/src/main/assets" \
    -I "$ANDROID_JAR" \
    --auto-add-overlay \
    --min-sdk-version 26 \
    --target-sdk-version 27 \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
    -F "$BUILD/app-unsigned.apk" 2>&1

# Add DEX file
(cd "$BUILD" && zip -q -u app-unsigned.apk classes.dex)

# Add native libraries (prebuilt in jniLibs)
JNILIBS="$APP/src/main/jniLibs"
if [ -d "$JNILIBS" ]; then
    echo "  Adding native libraries..."
    for ABI_DIR in "$JNILIBS"/*/; do
        ABI=$(basename "$ABI_DIR")
        for SO in "$ABI_DIR"*.so; do
            if [ -f "$SO" ]; then
                SONAME=$(basename "$SO")
                mkdir -p "$BUILD/lib/$ABI"
                cp "$SO" "$BUILD/lib/$ABI/$SONAME"
                echo "    lib/$ABI/$SONAME ($(stat -c%s "$SO") bytes)"
            fi
        done
    done
    (cd "$BUILD" && zip -q -r -u app-unsigned.apk lib/)
fi

echo ""
echo "=== Step 9: Sign ==="
if [ ! -f "$PROJECT/debug.keystore" ]; then
    echo "  Generating new signing keystore..."
    $KEYTOOL -genkey -v -keystore "$PROJECT/debug.keystore" \
        -storepass android -alias androiddebugkey -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" 2>&1
else
    echo "  Reusing existing keystore"
fi

$JARSIGNER -keystore "$PROJECT/debug.keystore" \
    -storepass android -keypass android \
    -signedjar "$BUILD/app-signed.apk" \
    "$BUILD/app-unsigned.apk" androiddebugkey 2>&1

OUTPUT_APK="$PROJECT/K12KB-${VERSION_NAME}.apk"
cp "$BUILD/app-signed.apk" "$OUTPUT_APK"
SIZE=$(stat -c%s "$OUTPUT_APK")
echo ""
echo "=== BUILD COMPLETE ==="
echo "Output: $(basename "$OUTPUT_APK") ($SIZE bytes / $(echo "scale=1; $SIZE/1048576" | bc) MB)"
echo "Path: $OUTPUT_APK"

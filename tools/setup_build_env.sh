#!/bin/bash
set -e

# K12KB Build Environment Setup Script
# Sets up Android APK build environment on Ubuntu 24.04 x86_64
# WITHOUT Gradle or Android Studio — manual shell-script builds only.
#
# Prerequisites: Ubuntu 24.04, root/sudo access, internet
# Result: JDK 11, Android SDK (platform 28, build-tools 28.0.3),
#         all dependency JARs, and library AAR resources installed.

echo "=== K12KB Build Environment Setup ==="

# ─── Step 1: System packages ────────────────────────────────────────────────
echo "[1/4] Installing system packages..."
apt-get install -y zip bc dalvik-exchange wget curl git aapt zipalign \
    android-sdk-build-tools-common 2>&1 | tail -1
echo "  dx:       $(ls /usr/lib/android-sdk/build-tools/debian/dx 2>/dev/null && echo OK || echo MISSING)"
echo "  aapt2:    $(which aapt2 2>/dev/null && echo OK || echo MISSING)"
echo "  zipalign: $(which zipalign 2>/dev/null && echo OK || echo MISSING)"

# ─── Step 2: JDK 11 (Eclipse Temurin) ───────────────────────────────────────
echo ""
echo "[2/4] Setting up JDK 11..."
if [ -f "$HOME/jdk11/bin/javac" ]; then
    echo "  JDK 11 already installed: $($HOME/jdk11/bin/javac -version 2>&1)"
else
    echo "  Finding latest Temurin JDK 11..."
    JDK_URL=$(wget -q "https://api.github.com/repos/adoptium/temurin11-binaries/releases/latest" -O - | \
        python3 -c "import json,sys;[print(a['browser_download_url']) for a in json.load(sys.stdin)['assets'] if 'OpenJDK11U-jdk_x64_linux_hotspot' in a['name'] and a['name'].endswith('.tar.gz')]" 2>/dev/null | head -1)
    echo "  Downloading: $JDK_URL"
    wget -q "$JDK_URL" -O /tmp/jdk11.tar.gz
    mkdir -p "$HOME/jdk11"
    tar xzf /tmp/jdk11.tar.gz -C "$HOME/jdk11" --strip-components=1
    rm -f /tmp/jdk11.tar.gz
    echo "  Installed: $($HOME/jdk11/bin/javac -version 2>&1)"
fi

# ─── Step 3: Android SDK (minimal) ──────────────────────────────────────────
echo ""
echo "[3/4] Setting up Android SDK..."
SDK="$HOME/android-sdk"
mkdir -p "$SDK/platforms/android-28" "$SDK/build-tools/28.0.3" "$SDK/libs" "$SDK/extras/appcompat-v7"

# android.jar for API 28 (from Sable/android-platforms GitHub repo)
if [ ! -f "$SDK/platforms/android-28/android.jar" ] || [ "$(stat -c%s "$SDK/platforms/android-28/android.jar")" -lt 1000 ]; then
    echo "  Downloading android-28 android.jar..."
    BLOB_SHA=$(curl -s "https://api.github.com/repos/Sable/android-platforms/contents/android-28/android.jar" | \
        python3 -c "import json,sys; print(json.load(sys.stdin)['sha'])" 2>/dev/null)
    curl -sL -H "Accept: application/vnd.github.v3.raw" \
        "https://api.github.com/repos/Sable/android-platforms/git/blobs/$BLOB_SHA" \
        -o "$SDK/platforms/android-28/android.jar"
    echo "  android.jar: $(du -h "$SDK/platforms/android-28/android.jar" | cut -f1)"
else
    echo "  android.jar already present"
fi

# Symlink system aapt2 and zipalign into SDK build-tools path
ln -sf "$(which aapt2)" "$SDK/build-tools/28.0.3/aapt2" 2>/dev/null || true
ln -sf "$(which zipalign)" "$SDK/build-tools/28.0.3/zipalign" 2>/dev/null || true

# ─── Step 4: Library JARs ───────────────────────────────────────────────────
echo ""
echo "[4/4] Downloading library JARs..."
LIBS="$SDK/libs"

download_jar_maven() {
    local name="$1" url="$2"
    if [ -f "$LIBS/$name" ] && [ "$(stat -c%s "$LIBS/$name")" -gt 100 ]; then
        echo "  [cached] $name"
        return 0
    fi
    echo -n "  Downloading $name... "
    curl -sL -o "$LIBS/$name" "$url"
    if file "$LIBS/$name" | grep -q "archive\|Java\|Zip"; then
        echo "OK ($(du -h "$LIBS/$name" | cut -f1))"
    else
        echo "FAILED (not a valid archive)"
        rm -f "$LIBS/$name"
        return 1
    fi
}

download_jar_github() {
    local repo="$1" jar_name="$2" output_name="$3" tag="$4"
    if [ -f "$LIBS/$output_name" ] && [ "$(stat -c%s "$LIBS/$output_name")" -gt 100 ]; then
        echo "  [cached] $output_name"
        return 0
    fi
    echo -n "  Downloading $output_name (GitHub: dandar3/$repo)... "
    local sha=$(curl -s "https://api.github.com/repos/dandar3/$repo/contents/libs/$jar_name?ref=$tag" | \
        python3 -c "import json,sys; print(json.load(sys.stdin).get('sha',''))" 2>/dev/null)
    if [ -z "$sha" ]; then
        echo "FAILED (could not get blob SHA)"
        return 1
    fi
    curl -sL -H "Accept: application/vnd.github.v3.raw" \
        "https://api.github.com/repos/dandar3/$repo/git/blobs/$sha" \
        -o "$LIBS/$output_name"
    echo "OK ($(du -h "$LIBS/$output_name" | cut -f1))"
}

# Third-party JARs from Maven Central
download_jar_maven "gson-2.8.7.jar" \
    "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.8.7/gson-2.8.7.jar"
download_jar_maven "jackson-core-2.13.0.jar" \
    "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.13.0/jackson-core-2.13.0.jar"
download_jar_maven "jackson-annotations-2.13.0.jar" \
    "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.13.0/jackson-annotations-2.13.0.jar"
download_jar_maven "jackson-databind-2.13.0.jar" \
    "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.13.0/jackson-databind-2.13.0.jar"
download_jar_maven "rhino-1.7.7.2.jar" \
    "https://repo1.maven.org/maven2/org/mozilla/rhino/1.7.7.2/rhino-1.7.7.2.jar"

# rhino-android: download AAR and extract classes.jar
if [ ! -f "$LIBS/rhino-android-1.6.0.jar" ] || [ "$(stat -c%s "$LIBS/rhino-android-1.6.0.jar")" -lt 100 ]; then
    echo -n "  Downloading rhino-android-1.6.0... "
    curl -sL -o /tmp/rhino-android.aar \
        "https://repo1.maven.org/maven2/com/faendir/rhino/rhino-android/1.6.0/rhino-android-1.6.0.aar"
    mkdir -p /tmp/rhino-extract && cd /tmp/rhino-extract
    unzip -q -o /tmp/rhino-android.aar classes.jar 2>/dev/null
    cp classes.jar "$LIBS/rhino-android-1.6.0.jar"
    cd - >/dev/null
    rm -rf /tmp/rhino-extract /tmp/rhino-android.aar
    echo "OK"
else
    echo "  [cached] rhino-android-1.6.0.jar"
fi

# Android Support Libraries from GitHub (dandar3 repos, tag 25.4.0)
download_jar_github "android-support-v7-appcompat" "android-support-v7-appcompat.jar" "appcompat-v7-25.4.0.jar" "25.4.0"
download_jar_github "android-support-v4" "android-support-v4.jar" "support-v4-25.4.0.jar" "25.4.0"
download_jar_github "android-support-compat" "android-support-compat.jar" "support-compat-25.4.0.jar" "25.4.0"
download_jar_github "android-support-core-ui" "android-support-core-ui.jar" "support-core-ui-25.4.0.jar" "25.4.0"
download_jar_github "android-support-core-utils" "android-support-core-utils.jar" "support-core-utils-25.4.0.jar" "25.4.0"
download_jar_github "android-support-fragment" "android-support-fragment.jar" "support-fragment-25.4.0.jar" "25.4.0"
download_jar_github "android-support-media-compat" "android-support-media-compat.jar" "support-media-compat-25.4.0.jar" "25.4.0"
download_jar_github "android-support-annotations" "android-support-annotations.jar" "support-annotations-25.4.0.jar" "25.4.0"
download_jar_github "android-support-vector-drawable" "android-support-vector-drawable.jar" "support-vector-drawable-25.4.0.jar" "25.4.0"
download_jar_github "android-support-animated-vector-drawable" "android-support-animated-vector-drawable.jar" "animated-vector-drawable-25.4.0.jar" "25.4.0"

# Strip multi-release module-info files that dx can't handle
for jar in "$LIBS"/jackson-*.jar; do
    zip -q -d "$jar" 'META-INF/versions/*' 2>/dev/null || true
    zip -q -d "$jar" 'module-info.class' 2>/dev/null || true
done

# Download appcompat-v7 AAR resources (needed for aapt2 resource linking)
if [ ! -d "$SDK/extras/appcompat-v7/res" ]; then
    echo "  Downloading appcompat-v7 resources..."
    curl -sL "https://codeload.github.com/dandar3/android-support-v7-appcompat/tar.gz/refs/tags/25.4.0" -o /tmp/appcompat-v7.tar.gz
    tar -xzf /tmp/appcompat-v7.tar.gz -C /tmp/
    cp -r /tmp/android-support-v7-appcompat-25.4.0/res "$SDK/extras/appcompat-v7/"
    cp /tmp/android-support-v7-appcompat-25.4.0/AndroidManifest.xml "$SDK/extras/appcompat-v7/"
    rm -rf /tmp/appcompat-v7.tar.gz /tmp/android-support-v7-appcompat-25.4.0
    echo "  appcompat-v7 resources: $(find "$SDK/extras/appcompat-v7/res" -type f | wc -l) files"
else
    echo "  [cached] appcompat-v7 resources"
fi

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Installed components:"
echo "  JDK 11:      $HOME/jdk11/bin/javac"
echo "  android.jar: $SDK/platforms/android-28/android.jar"
echo "  aapt2:       $SDK/build-tools/28.0.3/aapt2"
echo "  zipalign:    $SDK/build-tools/28.0.3/zipalign"
echo "  dx:          /usr/lib/android-sdk/build-tools/debian/dx"
echo "  Library JARs: $LIBS/ ($(ls "$LIBS"/*.jar 2>/dev/null | wc -l) files)"
echo ""
echo "To build the APK, run:"
echo "  bash tools/build_apk.sh"
echo "  # or"
echo "  bash /tmp/build_apk3.sh"

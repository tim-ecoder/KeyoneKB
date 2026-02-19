#!/bin/bash
# Build libnativesymspell.so for arm64-v8a using Android NDK
set -e

NDK_DIR="$(cygpath "$LOCALAPPDATA")/Android/Sdk/ndk/21.3.6528147"
TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/windows-x86_64"
CC="$TOOLCHAIN/bin/aarch64-linux-android26-clang"
STRIP="$TOOLCHAIN/bin/aarch64-linux-android-strip"

SRC_DIR="app/src/main/jni"
OUT_DIR="app/src/main/jniLibs/arm64-v8a"

echo "Compiling libnativesymspell.so ..."
"$CC" -shared -fPIC -O2 -Wall \
    -o "$OUT_DIR/libnativesymspell.so" \
    "$SRC_DIR/symspell.c" \
    "$SRC_DIR/keyboard_distance.c" \
    "$SRC_DIR/jni_bridge.c" \
    "$SRC_DIR/cdb.c" \
    "$SRC_DIR/translation_jni.c" \
    -lm -llog -landroid

"$STRIP" "$OUT_DIR/libnativesymspell.so"

echo "Done: $(ls -lh "$OUT_DIR/libnativesymspell.so" | awk '{print $5}')"

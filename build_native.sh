#!/bin/bash
# Build libnativesymspell.so for arm64-v8a using Android NDK
set -e

# NDK берётся из ANDROID_HOME (последняя установленная версия), а на машине
# разработчика — из прежнего windows-пути. Раньше здесь стоял только он, и на
# Linux скрипт не запускался вовсе.
if [ -z "$NDK_DIR" ]; then
    SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
    NDK_DIR=$(ls -d "$SDK_DIR"/ndk/* 2>/dev/null | sort -V | tail -1)
fi
if [ -z "$NDK_DIR" ] && [ -n "$LOCALAPPDATA" ]; then
    NDK_DIR="$(cygpath "$LOCALAPPDATA")/Android/Sdk/ndk/21.3.6528147"
fi
if [ ! -d "$NDK_DIR" ]; then
    echo "не нашёл NDK: задайте NDK_DIR или ANDROID_HOME" >&2
    exit 1
fi

TOOLCHAIN=$(ls -d "$NDK_DIR"/toolchains/llvm/prebuilt/* 2>/dev/null | head -1)
CC="$TOOLCHAIN/bin/aarch64-linux-android26-clang"
STRIP="$TOOLCHAIN/bin/aarch64-linux-android-strip"
[ -x "$STRIP" ] || STRIP="$TOOLCHAIN/bin/llvm-strip"

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

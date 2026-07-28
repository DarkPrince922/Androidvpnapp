#!/usr/bin/env bash
# Собирает libhev-socks5-tunnel.so (мост TUN -> SOCKS) под все ABI и кладёт
# в app/src/main/jniLibs/. Требуется Android NDK: export NDK_HOME=...
# JNI-класс привязан к пакету com.darkprince.vpn.vpn (см. TProxyService.kt).
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "${NDK_HOME:-}" || ! -d "${NDK_HOME:-}" ]]; then
  echo "Android NDK не найден: задайте переменную NDK_HOME" >&2
  exit 1
fi

ABIS="armeabi-v7a arm64-v8a x86_64"
SRC="$DIR/third_party/hev-socks5-tunnel"

if [[ ! -d "$SRC" ]]; then
  git clone --recursive --depth 1 https://github.com/heiher/hev-socks5-tunnel "$SRC"
fi

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

mkdir -p "$TMPDIR/jni"
ln -s "$SRC" "$TMPDIR/jni/hev-socks5-tunnel"
echo 'include $(call all-subdir-makefiles)' > "$TMPDIR/jni/Android.mk"

(
  cd "$TMPDIR"
  "$NDK_HOME/ndk-build" \
    NDK_PROJECT_PATH=. \
    APP_BUILD_SCRIPT=jni/Android.mk \
    "APP_ABI=$ABIS" \
    APP_PLATFORM=android-26 \
    NDK_LIBS_OUT="$TMPDIR/libs" \
    NDK_OUT="$TMPDIR/obj" \
    "APP_CFLAGS=-O3 -DPKGNAME=com/darkprince/vpn/vpn" \
    "APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu"
)

DEST="$DIR/app/src/main/jniLibs"
mkdir -p "$DEST"
cp -r "$TMPDIR/libs/." "$DEST/"
echo "OK: библиотеки скопированы в $DEST"

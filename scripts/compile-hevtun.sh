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
# Версия зафиксирована: иначе каждая сборка тянет новый HEAD, и патч ниже
# однажды перестанет накладываться молча.
HEVTUN_COMMIT="180cda8b304b71b9d9ef8ea93aeb0e4e00e15f7d"

if [[ ! -d "$SRC" ]]; then
  git clone https://github.com/heiher/hev-socks5-tunnel "$SRC"
fi

(
  cd "$SRC"
  git fetch -q origin
  git checkout -q --force "$HEVTUN_COMMIT"
  git submodule update --init --recursive -q
  # Короткая UDP-датаграмма роняла весь VPN: ядро помечает такое сообщение
  # нулевым адресом, а туннель разыменовывал его без проверки.
  git checkout -q -- src/hev-socks5-session-udp.c
  git apply "$DIR/patches/hev-socks5-tunnel-udp-null-addr.patch"
  echo "Патч про нулевой адрес UDP наложен"
)

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

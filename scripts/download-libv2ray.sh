#!/usr/bin/env bash
# Скачивает libv2ray.aar (ядро Xray, gomobile-биндинг) из релизов
# 2dust/AndroidLibXrayLite в app/libs/.
set -euo pipefail

VERSION="${LIBV2RAY_VERSION:-v26.7.19}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$DIR/app/libs"

mkdir -p "$DEST"
URL="https://github.com/2dust/AndroidLibXrayLite/releases/download/${VERSION}/libv2ray.aar"
echo "Downloading $URL"
curl -fL --retry 3 -o "$DEST/libv2ray.aar" "$URL"
echo "OK: $DEST/libv2ray.aar"

#!/usr/bin/env bash
# Скачивает базы геоданных Xray (geoip.dat, geosite.dat) в assets приложения.
# Нужны для правил роутинга вида geoip:private / geosite:category-ads-all.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$DIR/app/src/main/assets"
mkdir -p "$DEST"

BASE="https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download"
for name in geoip.dat geosite.dat; do
  echo "Downloading $BASE/$name"
  curl -fL --retry 3 -o "$DEST/$name" "$BASE/$name"
done
echo "OK: geodata в $DEST"

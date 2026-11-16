#!/usr/bin/env bash
# Снимает три экрана отладочного просмотра. Запускается внутри эмулятора.
set -euxo pipefail
mkdir -p /tmp/shots
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell wm size 1080x2340
adb shell wm density 420
for screen in home_off home_on home_light servers; do
  adb shell am force-stop com.darkprince.vpn
  adb shell am start -n com.darkprince.vpn/.debug.DesignPreviewActivity --es screen "$screen"
  sleep 6
  adb exec-out screencap -p > "/tmp/shots/$screen.png"
done
# shots

#!/usr/bin/env bash
# Launch smoke test on an emulator: Main, Settings sheet, Debug page must open without a crash.
# Usage: smoke-test.sh <apk> <out-dir>
set -euo pipefail
APK=$1 OUT=$2 PKG=com.saman.tunnel.debug
mkdir -p "$OUT"
adb root >/dev/null 2>&1 || true; adb wait-for-device   # root lets am start the non-exported DebugActivity
adb install -r -g "$APK"
adb logcat -c

shot() { sleep 3; adb exec-out screencap -p > "$OUT/$1.png"; }
alive() {
  adb logcat -d -b crash > "$OUT/crash.txt" || true
  if grep -q "$PKG" "$OUT/crash.txt" || ! adb shell pidof "$PKG" >/dev/null; then
    echo "CRASH at: $1"; cat "$OUT/crash.txt"; adb logcat -d > "$OUT/logcat.txt"; exit 1
  fi
  echo "ok: $1"
}

adb shell am start -W -n "$PKG/com.saman.tunnel.MainActivity"
shot main; alive main

# Settings is a bottom sheet behind the grab handle (content-desc Settings / تنظیمات).
adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null
B=$(grep -oE 'content-desc="(Settings|تنظیمات)"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' "$OUT/ui.xml" | grep -oE '[0-9]+' | tail -4 | tr '\n' ' ')
[ -n "$B" ] || { echo "Settings handle not found"; exit 1; }
set -- $B; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
shot settings; alive settings

adb shell am start -W -n "$PKG/com.saman.tunnel.DebugActivity"
shot debug; alive debug

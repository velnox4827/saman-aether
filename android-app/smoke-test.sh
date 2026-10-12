#!/usr/bin/env bash
# Emulator smoke test: Main/Settings/Debug open, core loads, WG and GOOL start in Proxy mode. Any failure = exit 1.
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

applog() { adb shell run-as "$PKG" cat files/saman-tunnel-app.log 2>/dev/null | tr -d '\r'; }
fail() { echo "FAIL: $1"; applog | tail -80; adb logcat -d > "$OUT/logcat.txt"; exit 1; }

adb shell am start -W -n "$PKG/com.saman.tunnel.MainActivity"
shot main; alive main
sleep 2; V=$(applog | grep -o 'aether=[^ ]*' | tail -1); echo "core: $V"
case "$V" in aether=[0-9]*) ;; *) fail "core library did not load ($V)";; esac

# Settings is a bottom sheet behind the grab handle (content-desc Settings / تنظیمات).
adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null
B=$(grep -oE 'content-desc="(Settings|تنظیمات)"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' "$OUT/ui.xml" | grep -oE '[0-9]+' | tail -4 | tr '\n' ' ')
[ -n "$B" ] || { echo "Settings handle not found"; exit 1; }
set -- $B; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
shot settings; alive settings

adb shell am start -W -n "$PKG/com.saman.tunnel.DebugActivity"
shot debug; alive debug

# Connect in Proxy mode via the launcher quick-connect path (same start() as the power button).
for MODE in WG GOOL; do
  adb shell am force-stop "$PKG"
  printf '<?xml version="1.0" encoding="utf-8"?>\n<map><string name="last_mode">%s</string></map>\n' "$MODE" |
    adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/saman_tunnel.xml && rm -f files/saman-tunnel-app.log'"
  adb shell am start -W -a com.saman.tunnel.QUICK_CONNECT -n "$PKG/com.saman.tunnel.MainActivity"
  for _ in $(seq 1 30); do applog | grep -q 'startCore ok=' && break; sleep 1; done
  L=$(applog)
  if grep -qE 'Starting AetherService failed|FATAL_JAVA|\[EXCEPTION\]|UnsatisfiedLinkError' <<<"$L"; then fail "$MODE start threw"; fi
  grep -q "Starting mode=$MODE" <<<"$L" || fail "$MODE service never launched core"
  grep -q 'startCore ok=true' <<<"$L" || fail "$MODE startCore not ok"
  shot "connect-$MODE"; alive "connect-$MODE"
  echo "ok: connect $MODE (proxy)"
done

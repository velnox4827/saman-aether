#!/usr/bin/env bash
set -euo pipefail

# Retain exact Aether 2.3.0 binaries built from official source commit
# 6175b67df370ab856bcee07fe85b524031903956, as shipped in signed v2.1.0.
BASELINE_SHA256=01be538eae2cc6a066acb9f64557c339907974a4398419e919b4073027ce8b12
CERT_SHA256=03233edadf89ed27c7cf80452916a7bd25e8c74abe0f12d2eefcb8b290b48805
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LIBS="${1:-$ROOT/android-app/app/src/main/jniLibs}"
SIGNER="${APKSIGNER:-${ANDROID_HOME:?Set ANDROID_HOME or APKSIGNER}/build-tools/35.0.0/apksigner}"
WORK="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/retained-aether.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
APK="$WORK/baseline.apk"
curl --fail --location --retry 3 --connect-timeout 20 \
  https://github.com/velnox4827/saman-aether/releases/download/v2.1.0/Saman-Tunnel-v2.1.0-universal-arm.apk \
  -o "$APK"
printf '%s  %s\n' "$BASELINE_SHA256" "$APK" | sha256sum --check --strict
CERTS="$("$SIGNER" verify --verbose --print-certs "$APK")"
grep -q "certificate SHA-256 digest: $CERT_SHA256" <<<"$CERTS"

# Only the unchanged Aether/C++/Psiphon assets: never copy the old bridge or HEV.
for abi in arm64-v8a armeabi-v7a; do
  case "$abi" in
    arm64-v8a) machine=AArch64 ;;
    armeabi-v7a) machine=ARM ;;
  esac
  for lib in libaether.so libc++_shared.so libpsiphon-tunnel-core.so; do
    unzip -p "$APK" "lib/$abi/$lib" > "$WORK/$lib"
    test -s "$WORK/$lib"
    readelf -h "$WORK/$lib" | grep -E "Machine: +$machine$" >/dev/null
    install -Dm755 "$WORK/$lib" "$LIBS/$abi/$lib"
    sha256sum "$LIBS/$abi/$lib"
  done
done

#!/usr/bin/env bash
set -euo pipefail

XRAY_COMMIT=676eaf66f8f05d4d558ee7a74b4736623d3feeb9 # official v26.11.9
GO_VERSION=go1.27.3
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/xray-build.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
LIBS="${1:-$ROOT/android-app/app/src/main/jniLibs}"
test "$(go env GOVERSION)" = "$GO_VERSION"
CLANG="${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to NDK 26.3.11579264}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
test -x "$CLANG"
export GOTOOLCHAIN=local CGO_ENABLED=1 GOOS=android

git init -q "$WORK/source"
git -C "$WORK/source" remote add origin https://github.com/XTLS/Xray-core.git
git -C "$WORK/source" fetch --depth 1 origin "$XRAY_COMMIT"
git -C "$WORK/source" checkout -q --detach FETCH_HEAD
test "$(git -C "$WORK/source" rev-parse HEAD)" = "$XRAY_COMMIT"
cd "$WORK/source"
install -Dm644 LICENSE "$ROOT/android-app/app/src/main/assets/licenses/Xray-core-LICENSE.txt"
printf 'Xray-core v26.11.9\nSource: https://github.com/XTLS/Xray-core/tree/%s\nApp-built upstream core, MPL-2.0; source is available at the pinned commit.\n' "$XRAY_COMMIT" > "$ROOT/android-app/app/src/main/assets/licenses/Xray-core-SOURCE.txt"

# Android build tags and Bionic resolver are necessary for domain-based nodes.
for abi in arm64-v8a armeabi-v7a; do
  case "$abi" in
    arm64-v8a) export GOARCH=arm64; machine='AArch64'; target=aarch64-linux-android24; interpreter=/system/bin/linker64 ;;
    armeabi-v7a) export GOARCH=arm GOARM=7; machine='ARM'; target=armv7a-linux-androideabi24; interpreter=/system/bin/linker ;;
  esac
  export CC="${CLANG%/clang}/$target-clang"
  binary="$WORK/xray-$abi"
  go build -mod=readonly -trimpath -buildvcs=false -ldflags '-s -w -checklinkname=0 -extldflags=-Wl,-z,max-page-size=16384' -o "$binary" ./main
  file "$binary"
  readelf -h "$binary" | grep -E "Machine: +$machine$" >/dev/null
  readelf -h "$binary" | grep -E 'Type: +DYN' >/dev/null
  readelf -l "$binary" | grep -F "Requesting program interpreter: $interpreter]" >/dev/null
  readelf -d "$binary" | grep -F 'Shared library: [libc.so]' >/dev/null
  if readelf -d "$binary" | grep -E 'libc\.so\.6|ld-linux' >/dev/null; then exit 1; fi
  install -Dm755 "$binary" "$LIBS/$abi/libxray.so"
done

# Small Android-native parent-death guard; the Go core stays a separate executable.
for abi in arm64-v8a armeabi-v7a; do
  case "$abi" in
    arm64-v8a) target=aarch64-linux-android24; machine=AArch64 ;;
    armeabi-v7a) target=armv7a-linux-androideabi24; machine=ARM ;;
  esac
  guard="$LIBS/$abi/libxray-guard.so"
  "$CLANG" --target="$target" -fPIE -pie -O2 -Wl,-z,max-page-size=16384 \
    "$ROOT/android-app/app/src/main/cpp/xray_guard.c" -o "$guard"
  chmod 755 "$guard"
  readelf -h "$guard" | grep -E "Machine: +$machine$" >/dev/null
  readelf -h "$guard" | grep -E 'Type: +DYN' >/dev/null
done

# Host CLI validation is not an Android 7 runtime test.
GOARCH="$(go env GOHOSTARCH)"
export GOARCH GOOS=linux CGO_ENABLED=0
unset GOARM CC
go build -mod=readonly -trimpath -buildvcs=false -ldflags '-s -w' -o "$WORK/xray-host" ./main
if [ -n "${GITHUB_ENV:-}" ]; then
  install -Dm755 "$WORK/xray-host" "$RUNNER_TEMP/xray-host"
  cc -O2 "$ROOT/android-app/app/src/main/cpp/xray_guard.c" -o "$RUNNER_TEMP/xray-guard-host"
  printf 'XRAY_BINARY=%s\nXRAY_GUARD=%s\n' "$RUNNER_TEMP/xray-host" "$RUNNER_TEMP/xray-guard-host" >> "$GITHUB_ENV"
fi
"$WORK/xray-host" version | tee "$WORK/version.txt"
grep -q '^Xray 26\.11\.9 ' "$WORK/version.txt"
printf '%s\n' '{"inbounds":[{"listen":"127.0.0.1","port":1819,"protocol":"socks","settings":{"udp":true}}],"outbounds":[{"protocol":"blackhole"}]}' > "$WORK/config.json"
"$WORK/xray-host" run -test -config "$WORK/config.json"
printf '%s\n' '{"outbounds":[{"protocol":"not-a-protocol"}]}' > "$WORK/invalid.json"
if "$WORK/xray-host" run -test -config "$WORK/invalid.json"; then
  printf '%s\n' 'Xray accepted an invalid config' >&2
  exit 1
fi
sha256sum "$LIBS/arm64-v8a/libxray.so" "$LIBS/armeabi-v7a/libxray.so"

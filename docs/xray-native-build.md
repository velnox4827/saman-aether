# Xray native build (v2.2.0)

Pins:
- Xray-core **v26.11.9**, official commit `676eaf66f8f05d4d558ee7a74b4736623d3feeb9`.
- Go **1.27.1** (`GOTOOLCHAIN=local`); upstream requires Go 1.27.
- HEV **2.20.0**, official submodule commit `75dbc1e9ace6c70d3d1b94df7c283efe1de7effc`.
- Aether remains **2.3.0**; NDK **26.3.11579264**, API **24** remain unchanged.

Both workflows call `bash android-app/scripts/build-xray.sh`. With Go 1.27.1,
GNU `readelf`, `file`, Git and the Linux-host NDK installed:

```sh
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/26.3.11579264"
bash android-app/scripts/build-xray.sh
```

An optional first argument selects an output directory instead of app `jniLibs`.
The helper fetches and verifies the exact commit, uses the checked-in `go.sum`
with `-mod=readonly`, and builds `./main` using:

```sh
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -mod=readonly -trimpath -buildvcs=false -ldflags '-s -w' -o libxray.so ./main
CGO_ENABLED=0 GOOS=linux GOARCH=arm GOARM=7 go build -mod=readonly -trimpath -buildvcs=false -ldflags '-s -w' -o libxray.so ./main
```

The version is compiled into upstream `core/core.go`; linker version overrides
are unnecessary. `buildvcs=false` leaves upstream's build label `Custom` but
retains the real `26.11.9` version. This is an app-built upstream core, not an
unmodified official release binary.

Outputs are executable `libxray.so` under `arm64-v8a` and `armeabi-v7a`, not JNI
shared libraries. Each must have the correct ELF machine, static linkage, no
`PT_INTERP`, and no dynamic `NEEDED` dependencies. APK extraction supplies the
executable location, as for the existing Psiphon console clients. Existing
Aether, Psiphon, bridge and C++ runtime binaries are not replaced by this helper.

The helper also compiles the app-owned `xray_guard.c` with NDK clang for
`aarch64-linux-android24` and `armv7a-linux-androideabi24`, using `-fPIE -pie -O2`
and 16 KiB maximum ELF page alignment (`-Wl,-z,max-page-size=16384`).
It stages `libxray-guard.so` beside the core and checks ELF architecture/PIE.
The runner invokes the guard before Xray to tie the core lifetime to its parent.
All debug/signed/universal APK checks require both executables and existing libs.

A separate host-native Linux build runs `version`, accepts a synthetic loopback
SOCKS/blackhole config with `run -test`, and rejects an invalid protocol. This
checks native configuration parsing, not a real subscription or tunnel.

## Retained Aether binaries

Both workflows first run `bash android-app/scripts/stage-retained-aether.sh`,
not the expensive unchanged Rust/Tor source build. It downloads the project's
signed [v2.1.0 universal APK](https://github.com/velnox4827/saman-aether/releases/tag/v2.1.0),
checks SHA-256 `01be538eae2cc6a066acb9f64557c339907974a4398419e919b4073027ce8b12`
(from that release's `SHA256SUMS`, also matching GitHub's asset digest), then
verifies the APK signature and the unchanged certificate SHA-256
`03233edadf89ed27c7cf80452916a7bd25e8c74abe0f12d2eefcb8b290b48805`.
Only `libaether.so`, `libc++_shared.so` and `libpsiphon-tunnel-core.so` are
extracted for both ARM ABIs, with ELF-machine and nonempty checks. No old
HEV/bridge is copied. This guarantees byte-for-byte Aether 2.3.0 continuity
with the released app; its official source pin remains
`6175b67df370ab856bcee07fe85b524031903956`. The helper accepts an optional output
directory and `APKSIGNER` executable override for local verification. It requires
no signing secrets. HEV 2.20.0, the app bridge/guard, and Xray still build afresh.

## Android 7 compatibility limits

The official release provides Android ARM64 and AMD64 assets, **not Android
ARMv7**. Its Android builds use CGO and NDK API 24. Upstream also builds static
Linux ARMv7 and ARM64 executables; this app deliberately uses that portable
Linux path for both ARM ABIs instead of claiming an official Androidv7 asset.

Static linkage removes Bionic ABI requirements; it does **not** prove Android 7
compatibility. Linux build tags differ from Android tags. Go runtime/kernel
requirements, OEM seccomp/SELinux policy, ARMv7 CPU support, executable extraction,
DNS discovery and TLS trust behavior still require real API 24 ARM64/ARMv7 tests.
In particular, Linux pure-Go DNS must not be assumed to discover Android's system
DNS; verify explicit DNS/bootstrap behavior before claiming domain-based nodes
work. Hosted API 30/34 emulator launch smoke tests do not cover Android 7 tunnels.
No minSdk increase or compatibility guarantee is introduced by this build.

## Release safety

Version/package assertions require `2.2.0` / `20200` / minSdk `24` and preserve
signing-certificate continuity. `android-apk.yml` dispatch produces debug assets
and optionally signed release assets. `release.yml` dispatch builds signed
artifacts only, without tags or Releases writes. A separately approved push of
existing tag `v2.2.0` can create only a **draft** with `--verify-tag --draft`, never
`--latest`. These edits do not dispatch CI, tag, publish, or change signing secrets.

## Official references

- [Xray release/assets](https://github.com/XTLS/Xray-core/releases/tag/v26.11.9)
- [Pinned build matrix and flags](https://github.com/XTLS/Xray-core/blob/676eaf66f8f05d4d558ee7a74b4736623d3feeb9/.github/workflows/release.yml)
- [Pinned Go module requirement](https://github.com/XTLS/Xray-core/blob/676eaf66f8f05d4d558ee7a74b4736623d3feeb9/go.mod)
- [Pinned version implementation](https://github.com/XTLS/Xray-core/blob/676eaf66f8f05d4d558ee7a74b4736623d3feeb9/core/core.go)
- [HEV 2.20.0](https://github.com/heiher/hev-socks5-tunnel/tree/75dbc1e9ace6c70d3d1b94df7c283efe1de7effc)

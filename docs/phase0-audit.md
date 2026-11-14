# Phase 0 evidence and retained-core security review

## Baseline verified
- Source: `velnox4827/saman-aether`, tag `v2.1.0`, peeled commit `4f9e903120d01121ea8c57da0c318896e3baf1c9`.
- Original `~/saman-aether` fetch reports a missing Git object. It is retained without repair/deletion. Clean implementation checkout: `~/saman-tunnel-v220`.
- Native Kotlin 2.3.21 Android Views, AGP 8.13.2, Gradle 8.13, JNI C, Rust Aether, native HEV. No Flutter/Compose, Xray or sing-box in the baseline.
- Package `com.saman.tunnel`; versionCode 20100, versionName 2.1.0, minSdk24, targetSdk35.
- Baseline ARM64 release APK SHA256 `c8d7fc0595407b124b298c6eb5e125216e63aed04c9080705261cd4ebbf99a36` matches release SHA256SUMS; ZIP CRC test passes.
- `apksigner verify --print-certs` using existing Java17 succeeds. Certificate SHA256 `03233edadf89ed27c7cf80452916a7bd25e8c74abe0f12d2eefcb8b290b48805` matches release workflow's public certificate pin.
- Production signing material exists as repository Actions secrets (names only inspected); values not retrieved/printed. Authentication as repo owner succeeds per-call without changing the user's primary GitHub account.
- Device read `dumpsys package com.saman.tunnel` currently reports package not found. In-place upgrade is NOT yet demonstrated.

## Connection engines and transport dependencies
| Component | Baseline | Official stable checked | Action |
|---|---|---|---|
| Aether | 2.3.0, `6175b67df370ab856bcee07fe85b524031903956` | 2.4.0 | Retain 2.3.0 per user; keep Android7 |
| HEV tun2socks | 2.18.0 `d9dca26c7ad0e494492244f0309e80ee583e739e` | 2.20.0 `75dbc1e` | Upgrade with all upstream submodules |
| Psiphon console | CluvexStudio fork `83aa73b9b982e7421e00117f5b0c5aceb5dda452` | Psiphon-Labs 2.0.42, `c4a09096ab401b57f52e092c8f2f829c8fac7628` | Fork != upstream version; no blind replacement; retain pinned binaries pending compatibility test |
| WireGuard implementation | boringtun0.7.1 in Aether Cargo.lock | 0.7.1 | Current |
| Tor implementation | Arti/arti-client0.46.0, enabled `tor` feature | 0.48.0 | Coupled to retained Aether; independent upgrade not claimed |
| QUIC/MASQUE | vendored quiche0.29.3 | upstream0.30.1 | Coupled to retained Aether; do not replace vendored fork blindly |
| TLS/BoringSSL Rust bindings | boring/boring-sys5.2.0 | boring5.5.0 | Coupled to retained Aether |
| smoltcp | 0.14.0 | 0.14.0 | Current |
| Xray | Absent | 26.11.9 `676eaf6` | New isolated configuration module |
| sing-box | Absent | 1.14.4 | Not selected; no speculative second engine |

GOOL, GOOL Classic, WARP and MASQUE are Aether modes, not separately versioned engines. Tor is Arti, not a packaged tor daemon. Psiphon's Go binary also contains its own transitive libraries (including wireguard-go); changing those means rebuilding that specific upstream/fork, not replacing Aether's WireGuard component. The complete Aether dependency inventory is its pinned Cargo.lock. HEV itself pins hev-socks5-core, hev-task-system, lwIP and YAML as git submodules; the HEV version upgrade brings their matching revisions, not arbitrary heads.

## Aether 2.4.0 security changes (not applied)
Official release notes describe:
1. With `--team`, MASQUE TLS certificates are now checked against pinned Cloudflare keys by default. This addresses a middlebox impersonating a gateway in Zero Trust. Without `--team`, verification still remains off unless `--tls-verify` enables it. The app's generated v2.1 command lines do not set `--team`.
2. Invalid/unsupported `--upstream` now fails closed rather than connecting directly around the requested proxy. The app does not expose/set a custom `--upstream` in its command lines.
3. Proxy ports are reserved throughout startup/reconnect so another local process cannot take them while the core reconnects. This is relevant hardening even without Zero Trust.
4. Tor/Psiphon teardown and failure handling are stronger. The app already has an isolated core process lifecycle and VPN Binder supervision; this is not proof all upstream issues are neutralized.

The release notes do not assign these changes a CVE or critical severity. No critical exploitable flaw in the app's actual fixed command paths has been established by this audit; that is not a claim of absence. No upgrade/backport is made here. Official 2.4 Android binaries require API29; preserving the user's API24 requirement is the explicit decision.

## Integration boundary
Subscription URLs and user-issued configuration links are appropriate client inputs. Panel administrator credentials and bot tokens must never enter the APK. Full native payment/account APIs are out of scope pending an official authenticated customer API and separate approval. Real deployed Mirza/3x-ui versions, subscription header availability and the 48-hour test account remain to be verified with an actual user-issued subscription.

## Sources
- https://github.com/velnox4827/saman-aether/releases/tag/v2.1.0
- https://github.com/CluvexStudio/Aether/releases/tag/v2.4.0
- https://github.com/CluvexStudio/Aether/blob/6175b67df370ab856bcee07fe85b524031903956/aether/Cargo.lock
- https://github.com/CluvexStudio/Aether/blob/v2.3.0/psiphon-build.sh
- https://github.com/heiher/hev-socks5-tunnel/releases/tag/2.20.0
- https://github.com/Psiphon-Labs/psiphon-tunnel-core/releases/tag/v2.0.42
- https://crates.io/api/v1/crates/arti-client
- https://crates.io/api/v1/crates/boringtun
- https://crates.io/api/v1/crates/quiche
- https://crates.io/api/v1/crates/boring
- https://crates.io/api/v1/crates/smoltcp
- https://github.com/XTLS/Xray-core/releases/tag/v26.11.9
- https://github.com/SagerNet/sing-box/releases/tag/v1.14.4

## Test honesty
Native Xray26.11.9 Linux ARM64 release archive SHA256 `64ed6858982a7f5eb85d88b811089063bd4f17561f4c8f44e0f1dc8e6f6430be` verified against upstream .dgst; `xray version` executes on this Android15 phone. This alone does not verify Android7, ARMv7, VPN traffic or a real bot account. No APK is installed, no real bot/panel account is created, and no public release is published by this audit.

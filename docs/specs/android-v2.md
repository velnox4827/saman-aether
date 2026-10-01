# Saman Tunnel Android v2 — implementation spec

## 1. Overview
Replace the incorrect v1.10.0 interpretation with a corrected major Android release: GOOL remains available beside WireGuard in a single WireGuard-family chooser, and Psiphon is exposed only through official Aether orchestration with the matching official release's bundled Psiphon console binary. Aether remains the sole protocol engine/orchestrator.

## 2. Goals and non-goals
Goals:
- Main WireGuard tile opens a picker for WireGuard (`--wg`) and GOOL (`--gool`). Existing direct GOOL and Tor-through-GOOL are restored where already supported by Aether.
- Migrate only legacy state written by mistaken v1.10.0 logic back from WG to GOOL when the migration marker proves GOOL was formerly selected; do not blindly migrate all WG users.
- Add Psiphon-only as a user-facing mode through Aether’s supported `--psiphon-only` flag. The FFI briefly probes/binds the local listeners and drops them before the Psiphon-only branch; Aether then passes the normal SOCKS bind `127.0.0.1:1819` (and HTTP `127.0.0.1:1820`) into Psiphon’s own configuration. The standalone Aether defaults `:1821` are for `--psiphon` chain/reverse internals, not this mode. Thus Psiphon-only can own the expected local proxy ports without a listener collision. Do not offer chain/reverse in v2.0 until their multi-proxy/transport semantics are verified.
- Package official `pt/psiphon-tunnel-core` for both Android ABIs from the exact same Aether v2.1.0 official release archive used for `libaether.so`; verify archive checksum and executable architecture, place binary beside app's native library so Aether's documented `current_exe().parent()/pt` search resolves it.
- Bump app to v2.0.0 with monotonic versionCode; refresh docs, changelog, CI/release workflow and a user-ready Telegram draft generation.
- No bot message gets sent to Telegram; user will post provided message.

Non-goals:
- Do not build/maintain a second Aether core, add third-party protocol detection, or alter loopback SOCKS/HTTP endpoints 127.0.0.1:1819/1820.
- Do not expose Psiphon if archive verification/package validation or ABI runtime integration fails.
- Do not alter Termux product behavior beyond making shared docs accurately distinguish Android v2 from Termux.

## 3. Technical design
`MainActivity` WireGuard tile -> dialog -> selected canonical mode -> `AetherService` -> `NativeBridge` -> one Aether core. Psiphon modes use Aether's own CLI arguments; `--psiphon-bind` must be `127.0.0.1:1821` (Aether default) and Aether stays responsible for lifecycle/readiness/reconnect. For Android native FFI, `current_exe()` is not guaranteed to resolve a packaged `.so` as an executable path; verify this in source/build and explicitly set `AETHER_PSIPHON_BIN` to `applicationInfo.nativeLibraryDir/../?` only if that resolves correctly, otherwise copy binary at install/start into app-private files under `filesDir/pt/` and set env to that verified path using existing JNI start path. Never download runtime separately.

The official Aether v2.1.0 Android arm64 archive was inspected: it contains `aether`, `pt/lyrebird`, and `pt/psiphon-tunnel-core`; Aether source `psiphon.rs` explicitly searches next to current executable, `./pt`, and PATH, then spawns this external console client. Therefore this is Aether-supported integration, but binary packaging and path are required—flags alone are insufficient.

Build pipeline downloads pinned release archive(s), validates published SHA256 sidecar before extraction, extracts only Aether and `pt/psiphon-tunnel-core`, validates ELF ABI with `file`/readelf, sets executable permission, installs into app-private staging location at runtime or verified package path, and includes artifacts in debug/release APK. Check provenance/license: Aether release contains GNU GPLv3 client; confirm project license compatibility/distribution notices before publishing.

## 4. Interface contract
Modes: `WG`, `GOOL`, `PSIPHON_ONLY`; flags respectively `--wg`, `--gool`, `--psiphon-only`. In Psiphon-only mode, official Aether passes configured `:1819` and optional `:1820` directly to the Psiphon child; the `:1821` default is for chain/reverse modes. Readiness still requires runtime proof that the FFI-returned listener and Aether job state stay live through Psiphon child lifecycle.

## 5. Testing strategy
- Unit tests: picker dispatch mapping; all 5 modes map to exact flags; reverse Psiphon uses MASQUE/H2 only; legacy WG→GOOL migration guarded by a version/migration marker; existing SOCKS/HTTP binds unchanged.
- Build validation: source archive sidecar checksum; required binaries and ABI; APK contains required `pt/psiphon-tunnel-core` for arm64 and armv7, executable mode and correct path.
- CI: unit tests, lint before native builds; debug APK and signed release APK; signing continuity; checksum verify.
- Runtime test on Android device: start/stop WG, GOOL, Psiphon, permission denial, core exit, binary missing, ensure Psiphon readiness reflects Aether state. If device is unavailable, explicitly mark runtime scenario unverified and don't claim it passed.

## 6. Rollout and rollback
Publish v2.0.0 only after CI green, asset checksums/signature pass, and verified release is published. Keep v1.10.0 tag immutable. Roll back by installing previous signed v1.10.0 APK; do not move tags.

## 7. Open issues
- Verify official v2.1.0 release archive checksum sidecars and ABI support for bundled Psiphon on both arm64 and armv7.
- Runtime-test that Aether's FFI job remains alive while `psiphon-tunnel-core` owns the shared `:1819`/`:1820` listeners.
- Verify GPLv3 source/license notice obligations for redistributing included official Psiphon console binary.
# Changelog

## Post-v1.5.0 maintenance

- Modernized Saman Center to v2.1.0 with lazy command loading, read-only metadata commands, whitelist-parsed configuration, bounded logs, conservative repair, and a TTL-gated dashboard cache.
- Hardened Aether runner ownership, locking, PID publication, readiness detection, restart handling, configurable ports/timeouts, and fail-closed listener checks without stopping unrelated processes.
- Made installer publication and rollback failure-aware, added bounded network operations, and strengthened private verified script backups.
- Hardened Saman Share authentication, traversal and filename validation, upload limits, free-space reservation, collision handling, partial-file cleanup, direction modes, response headers, and connection deadlines.
- Added atomic non-destructive media output, validated download/SSH inputs, terminal-output sanitization, parallel bounded network probes, Android permission-denied socket-table fallbacks, and expanded shell/Python CI coverage.
- Replaced the outdated README with complete, equivalent English and Persian documentation for Saman Tunnel v1.5.0 and the canonical Saman/Termux architecture.
- Added the current Saman Center v2 implementation to the repository; `saman` is the canonical command and `saman2` remains a compatibility entry point.
- Kept `aether-control` as a validation and delegation wrapper instead of duplicating process control.
- Modernized the Termux installer with architecture detection, stable GitHub release discovery, trusted asset selection, mandatory SHA-256 verification, private backups, rollback, idempotent routing, and configuration preservation.
- Stopped creating obsolete independent `0-Aether-*` through `3-Aether-*` shortcuts; the centralized `Saman-Center` shortcut is installed instead.
- Hardened Aether PID identity checks, stale PID recovery, orphan detection and cleanup, restart timeouts, port-conflict reporting, bounded per-mode logs, and sanitized diagnostics.
- Added Termux shell tests, ShellCheck validation, and a dedicated GitHub Actions maintenance workflow.

## v1.5.0

Stable Android lifecycle, health, logging, diagnostics, and update-safety release.

- versionCode `150`; versionName `1.5.0`
- Built from commit `bcb96b530aa08f6faac7e60f06dc7b26cd64e37c`
- Aether Core v1.8.0 pinned at `a916ff6fbbb4ebafe8314c53cf3718eb51dcae53`
- Explicit startup, connection, mode-switch, stopping, stopped, degraded, and failed phases
- Confirmed native-process shutdown before restart and safer service/process interruption recovery
- Protocol-aware SOCKS5 health checks and local proxy restart handling
- Smart Reconnect for WireGuard, MASQUE H3, MASQUE H2, and GOOL
- Bounded app/core logs and sanitized Safe/Full diagnostics
- Stable Android release-tag filtering and trusted GitHub release URL validation
- Android backup disabled and cleartext traffic disabled
- Signed ARM64, ARM32, and Universal ARM APKs plus `SHA256SUMS`
- Existing signing certificate continuity verified with SHA-256 fingerprint `03233edadf89ed27c7cf80452916a7bd25e8c74abe0f12d2eefcb8b290b48805`

## v1.4.0

Stable Android upgrade to Aether Core v1.8.0.

- versionCode `140`
- Aether Core v1.8.0 pinned at `a916ff6fbbb4ebafe8314c53cf3718eb51dcae53`
- Inherits Aether v1.8.0 GOOL stability fixes
- Inherits improved WireGuard task/panic resilience
- Inherits upstream authentication fix
- Inherits optimized HTTP proxy handling
- Inherits reconnect zero-value guard
- Smart Reconnect rebased for WG / MASQUE H3 / MASQUE H2 / GOOL
- Cache thresholds remain `650 ms / 1800 ms / 2500 ms / 20 s`
- Fresh scan remains `balanced`
- Local SOCKS5/HTTP reusable listener patch rebased onto v1.8.0
- `--quick-reconnect` and `--reconnect-secs 1`
- Home Screen Widget retained
- Safe / Full diagnostics retained
- Stable APKs: ARM64 / ARM32 / Universal ARM

## Saman Aether Termux v1.4.0

Patched Termux + Termux:Widget release based on Aether Core v1.8.0.

- Prebuilt patched `saman-aether-core` for arm64, armv7 and x86_64
- Inherits upstream Aether v1.8.0 stability fixes
- Keeps separately installed upstream `aether` untouched
- Smart Reconnect for WG / MASQUE H3 / MASQUE H2 / GOOL
- WG cached RTT threshold `650 ms`
- MASQUE H3 cached verification threshold `1800 ms`
- MASQUE H2 cached verification threshold `2500 ms`
- Short-lived path/pair threshold `20 s`
- Fresh scans remain `balanced`
- Local SOCKS5/HTTP reusable address binding
- HTTP CONNECT `127.0.0.1:1820`
- SOCKS5 `127.0.0.1:1819`
- `--reconnect-secs 1`
- MASQUE H2 shortcut
- 250 ms log-based readiness detection
- Safe / Full diagnostics

## v1.3.7

Smart Reconnect and local-proxy restart Stable release.

- versionCode `138`
- Keeps Aether Core v1.7.0 pinned
- Keeps `--scan balanced`
- Keeps `--quick-reconnect`
- Keeps `--reconnect-secs 1`
- WireGuard cached RTT over `650 ms` triggers fresh balanced scan
- MASQUE H3 cached verify threshold: `1800 ms`
- MASQUE H2 cached verify threshold: `2500 ms`
- Short-lived cached WG/MASQUE paths are discarded and rescanned
- Short-lived GOOL outer+inner pairs are dropped before fresh scan
- Fresh balanced-scan results are not rejected by cache thresholds
- Local SOCKS5/HTTP listener restart uses reusable address binding
- Short local-port hand-off retry before reporting 1819/1820 conflict
- Stable APKs for `arm64-v8a`, `armeabi-v7a`, and Universal ARM
- Home Screen Widget, Safe diagnostics and Full history diagnostics retained

## v1.3.5

Stable maintenance and Multi-ABI release.

- Stable APKs for `arm64-v8a`, `armeabi-v7a`, and Universal ARM
- versionCode `136`
- Aether Core v1.7.0 remains pinned
- Faster app-side connection readiness polling with the original 120-second ceiling retained
- Adds `--reconnect-secs 1`
- Improved native-core stop and cleanup behavior
- Aether Core runs in isolated Android process `:aether_core`
- More informative connection phases while Connecting
- Recommended Safe diagnostics report with recent compact logs and known identifier redaction
- Optional Full history diagnostics
- Home Screen Widget retained for WG / MASQUE H3 / MASQUE H2 / GOOL
- SOCKS5 `127.0.0.1:1819`
- HTTP CONNECT `127.0.0.1:1820`

## v1.3.0

Home Screen Widget release.

- Adds a compact one-tap Android home-screen widget
- Tap while stopped to start the last selected mode
- Tap while active to stop Saman Tunnel
- Defaults to WireGuard when no previous mode exists
- Remembers WG, MASQUE H3, MASQUE H2 and GOOL
- Widget state indicators for off, working, connected, unstable and error
- Widget controls the existing AetherService; no second proxy core is created
- Keeps SOCKS5 `127.0.0.1:1819`
- Keeps HTTP CONNECT `127.0.0.1:1820`
- Keeps Aether Core v1.7.0
- Stable Android versionCode: 131


## v1.2.1

Stable UI polish release.

- Reworks the top status card so long runtime states never overflow the screen
- Clean layout for Connected, Connection unstable, Starting, Connecting, Switching mode, Stopping, Stopped and Error
- Connection details are displayed on a dedicated detail line
- Long error details may wrap inside the card instead of leaving the screen
- Keeps SOCKS5 `127.0.0.1:1819` and HTTP CONNECT `127.0.0.1:1820`
- Keeps Aether Core v1.7.0 and all v1.2.0 networking behavior unchanged


## v1.2.0

Stable release.

- Cleaner three-line connected status layout for WG, MASQUE H3/H2 and GOOL
- SOCKS5 on `127.0.0.1:1819`
- Native HTTP CONNECT on `127.0.0.1:1820`
- Automatic clean reset when switching modes
- `Logs` shows the last 40 lines
- `Save TXT` exports complete currently retained App/Core diagnostics
- Custom notification icon
- Adaptive launcher/App Info icon
- System Light/Dark theme
- Battery status shortcut
- Manual GitHub update checker
- Aether Core v1.7.0 pinned
## v1.1.5

Android App Info icon cache-busting patch.

- Keeps all Saman Tunnel v1.1 features unchanged
- Uses a brand-new application icon resource ID: `saman_app_icon_v115`
- Manifest now points directly to the selected Saman Tunnel logo
- Avoids reusing the cached `ic_launcher` resource ID on some Xiaomi/MIUI devices
- Keeps package name and update compatibility unchanged


## v1.1.3

Kotlin compilation patch.

- Keeps all Saman Tunnel v1.1 features
- Fixes Battery Settings Intent type inference error in MainActivity
- Uses explicit Android Intent values for battery/app-info settings
- Keeps the stable v1.1.2 Android build stack unchanged


## v1.1.2

Stable Android build-tooling fix.

- Keeps every Saman Tunnel v1.1 feature unchanged
- compileSdk 36
- targetSdk 35
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.3.21
- Migrates deprecated `kotlinOptions.jvmTarget` to `compilerOptions`
- Uses stable Android SDK Platform 36 and Build Tools 35.0.0
- Keeps the tested NDK 26.3.11579264 for Aether native builds


## v1.1.1

Build compatibility patch for the v1.1 feature release.

- Keeps all v1.1.0 features unchanged
- Uses stable Android SDK Platform 36 in GitHub Actions
- compileSdk changed from preview API 37 to stable API 36
- AGP 9.3 / Gradle 9.5 retained
- targetSdk remains 35
- Fixes GitHub Actions failure: `Failed to find package 'platforms;android-37'`


## v1.1.0

- Follow Android system Light/Dark theme
- New textless adaptive launcher icon
- MASQUE HTTP/3 and HTTP/2 selection
- Quick Last 20 / Last 40 log viewer
- Full diagnostics TXT export retained
- Battery optimization status/shortcut
- Manual GitHub Releases update checker
- Start-action debounce
- compileSdk 37
- Android Gradle Plugin 9.3
- Gradle 9.5
- targetSdk intentionally remains 35 for compatibility testing
- Aether Core remains pinned at v1.7.0 source commit


## v1.0.0

First public stable release of Saman Tunnel.

### Highlights

- Standalone Android application; Termux is not required
- Embedded Aether Core
- MASQUE mode
- WireGuard mode
- GOOL mode
- Local SOCKS5 proxy at `127.0.0.1:1819`
- Foreground Android service
- Compact one-screen user interface
- Saman Tunnel application icon and branding
- App and Aether Core version display
- Connection health monitoring
- Reduced false reconnect notifications
- Diagnostics export to TXT
- Android status-bar safe-area handling
- GitHub bug-report and feature-request templates
- Signed APK release workflow
- Full Termux + Termux:Widget documentation retained in the same repository

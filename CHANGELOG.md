# Changelog

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

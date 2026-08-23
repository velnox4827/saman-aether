# Changelog

## v1.2.0

Main-version update candidate.

- Auto-reset when switching MASQUE / WireGuard / GOOL
- Native HTTP CONNECT on `127.0.0.1:1820`
- SOCKS5 stays on `127.0.0.1:1819`
- Diagnostics simplified to `Logs` (last 40 lines) + `Save TXT`
- Custom monochrome notification icon
- Proper adaptive launcher/App Info icon resource
- Debug APK is signed with the normal project key for testing
- Public Release/tag waits for manual approval after testing


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

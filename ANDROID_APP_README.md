# Saman Tunnel Android

Saman Tunnel v1.9.1 is the current Android APK release. It uses the official CluvexStudio Aether v2.0.0 core for proxy modes and adds the official HEV tun2socks engine for Android TUN/VPN forwarding. No third-party protocol core is added.

کانال رسمی تلگرام: https://t.me/SamanTunnelOfficial

The Android app provides:

- Aether MASQUE H3/H2, WireGuard, GOOL, MIM, and Tor modes where supported by the official core.
- Optional Android VpnService mode: Android TUN → HEV → Aether SOCKS5.
- Per-app routing, diagnostics, connection health, dark/light UI, and quick-connect widget.
- No Psiphon, Xray, SSTP, or other protocol implementation in the APK.

## Download

Download the latest ABI APKs from the GitHub Releases page. Use `arm64-v8a` for most modern Android phones; use `armeabi-v7a` only for older 32-bit devices. Verify `SHA256SUMS` before installation.

## Build

```bash
git clone --recurse-submodules https://github.com/velnox4827/saman-aether.git
cd saman-aether/android-app
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The release workflow builds the official Aether core at its pinned upstream commit and HEV tun2socks from the pinned `heiher/hev-socks5-tunnel` commit. Aether is never patched or vendored.

کانال رسمی پروژه: https://t.me/SamanTunnelOfficial

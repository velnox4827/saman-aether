# Saman Tunnel Android

Saman Tunnel v1.9.2 is the current Android APK release. It uses the official CluvexStudio Aether v2.1.0 core for proxy modes and the official HEV tun2socks 2.18.0 engine for Android TUN/VPN forwarding. No third-party protocol core is added.

کانال رسمی تلگرام: https://t.me/SamanTunnelOfficial

The Android app provides:

- Aether MASQUE H3/H2 and MASQUE-in-MASQUE each with separate HTTP/2 and HTTP/3 choices, plus WireGuard, GOOL, and Tor modes where supported by the official core.
- Optional Android VpnService mode: Android TUN → HEV → Aether SOCKS5.
- Per-app routing, diagnostics, connection health, dark/light UI, and quick-connect widget.
- No Psiphon, Xray, SSTP, or other protocol implementation in the APK.

## قابلیت‌ها

- پنل اتصال با نمایش روشن حالت، مرحله و سلامت ارتباط؛
- منوی تنظیمات و راهنما برای مسیریابی برنامه‌ها، Diagnostics، باتری، آپدیت و About؛
- خروجی‌گرفتن از لاگ‌ها و گزارش عیب‌یابی؛
- ویجت اتصال سریع، حالت روشن/تاریک و لینک مستقیم گروه و کانال؛
- مسیر Android TUN با HEV tun2socks 2.18.0 و حالت‌های proxy با Aether رسمی v2.1.0؛
- دانلود مستقیم آخرین نسخه از GitHub در Settings → Check for updates؛
- انتخاب مستقل HTTP/2 و HTTP/3 برای MASQUE و MASQUE-in-MASQUE.

## Download

Download the latest ABI APKs from the GitHub Releases page, or use Settings → Check for updates inside the app to download the latest stable Universal ARM APK directly from GitHub. Use `arm64-v8a` for most modern Android phones; use `armeabi-v7a` only for older 32-bit devices. Verify `SHA256SUMS` before installation.

## Build

```bash
git clone --recurse-submodules https://github.com/velnox4827/saman-aether.git
cd saman-aether/android-app
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The release workflow builds the official Aether v2.1.0 core at its pinned upstream commit and HEV tun2socks 2.18.0 from the pinned `heiher/hev-socks5-tunnel` commit. Aether is never patched or vendored.

کانال رسمی پروژه: https://t.me/SamanTunnelOfficial

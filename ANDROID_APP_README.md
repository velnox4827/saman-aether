# Saman Tunnel Android

Saman Tunnel v2.0.1 is the current Android APK release. It uses the official CluvexStudio Aether v2.1.0 core for proxy modes and the official HEV tun2socks 2.18.0 engine for Android TUN/VPN forwarding. Psiphon-only is offered through official Aether with official bundled Psiphon console clients; no protocol core is patched, vendored, or added.

کانال رسمی تلگرام: https://t.me/SamanTunnelOfficial

The Android app provides:

- Aether MASQUE H3/H2 and MASQUE-in-MASQUE each with separate HTTP/2 and HTTP/3 choices, plus WireGuard and Tor modes where supported by the official core. GOOL is restored beside WireGuard in a single WireGuard-family picker; legacy GOOL settings migrate back only when the v1.10.0 migration marker proves GOOL was formerly selected.
- Optional Android VpnService mode: Android TUN → HEV → Aether SOCKS5.
- Per-app routing, diagnostics, connection health, dark/light UI, and quick-connect widget.
- Psiphon tunnel mode through official Aether `--psiphon --masque --h2`, with the official `pt/psiphon-tunnel-core` client for arm64 and armv7 packaged from checksum-verified official Aether v2.1.0 release archives. App-facing SOCKS/TUN/HEV stays on `127.0.0.1:1819`; the Psiphon-side SOCKS listener uses `127.0.0.1:1821`. VPN Aether → Psiphon end-to-end behavior is not verified on an Android device. No Xray, SSTP, or other protocol implementation is added.

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

The release workflow builds the official Aether v2.1.0 core at its pinned upstream commit and HEV tun2socks 2.18.0 from the pinned `heiher/hev-socks5-tunnel` commit. It also stages the official `pt/psiphon-tunnel-core` clients for both ABIs from the checksum-verified official Aether v2.1.0 archives and asserts their presence in every release APK. Aether is never patched or vendored.

کانال رسمی پروژه: https://t.me/SamanTunnelOfficial

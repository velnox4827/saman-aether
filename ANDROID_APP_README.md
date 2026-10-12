# Saman Tunnel Android

Saman Tunnel v2.1.0 is the current Android APK release. It bundles the official CluvexStudio Aether v2.3.0 core and the official HEV tun2socks 2.18.0 engine for Android TUN/VPN forwarding. No protocol core is patched or vendored.

کانال رسمی تلگرام: https://t.me/SamanTunnelOfficial · گروه پشتیبانی: https://t.me/SamanTunnel

## Connection modes

| Group | Mode | What it does |
|---|---|---|
| MASQUE | H3 (default) / H2 | WARP over MASQUE. H3 uses QUIC; H2 uses TCP for networks that block QUIC. |
| MASQUE | MiM H3 / H2 | Two MASQUE layers to change the exit address. |
| WireGuard | WireGuard | Plain WARP WireGuard. |
| GOOL | GOOL | New core GOOL: WireGuard carried inside MASQUE, foreign exit. |
| GOOL | GOOL Classic | Old WireGuard-in-WireGuard GOOL. |
| Psiphon | In tunnel | Psiphon carried by the WARP tunnel; exit is Psiphon. |
| Psiphon | Reverse | WARP is reached through Psiphon; use when WARP is blocked. |
| Psiphon | Only | Psiphon alone, no WARP. Simplest. |
| Tor | Tor / MASQUE -> Tor / Tor -> MASQUE | Tor alone, through MASQUE, or MASQUE carried through Tor. |

Psiphon settings: exit country (default Auto) and method Auto / CDN / Direct. They apply on the next connect. Local proxy: SOCKS5 `127.0.0.1:1819`, HTTP `127.0.0.1:1820`. In *Psiphon: in tunnel* and *reverse*, the internal WARP/Psiphon listener uses `127.0.0.1:1821`.

Optional Android VpnService mode: Android TUN -> HEV -> local SOCKS5. Per-app routing, quick-connect widget, dark/light theme, Persian (RTL) and English.

## Xiaomi / MIUI / HyperOS

If connecting fails with "Unable to start service ... process is bad", the phone blocks the app's service processes. Open App settings -> turn on **Autostart** and set Battery to **No restrictions**, then close and reopen the app. The app now shows this hint in a dialog.

## Debug

Settings -> Support -> Debug. Filter App / Core / Errors, copy, share, save TXT (safe report redacts identifiers), clear.

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

The release workflow builds the official Aether v2.3.0 core at its pinned upstream commit and HEV tun2socks 2.18.0 from the pinned `heiher/hev-socks5-tunnel` commit. It also stages the official `pt/psiphon-tunnel-core` clients for both ABIs from the checksum-verified official Aether v2.3.0 archives and asserts their presence in every release APK. Aether is never patched or vendored.

کانال رسمی پروژه: https://t.me/SamanTunnelOfficial
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

The release workflow builds the official Aether v2.3.0 core at its pinned upstream commit and HEV tun2socks 2.18.0 from the pinned `heiher/hev-socks5-tunnel` commit. It also stages the official `pt/psiphon-tunnel-core` clients for both ABIs from the checksum-verified official Aether v2.3.0 archives and asserts their presence in every release APK. Aether is never patched or vendored.

کانال رسمی پروژه: https://t.me/SamanTunnelOfficial

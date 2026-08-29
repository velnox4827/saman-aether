# Saman Tunnel

<p align="center">
  <img src="android-app/app/src/main/res/drawable-nodpi/saman_tunnel_logo.png" width="150" alt="Saman Tunnel logo">
</p>

<p align="center">
  <b>Standalone Aether Core frontend for Android — no Termux required</b><br>
  رابط مستقل هسته Aether برای اندروید — بدون نیاز به Termux
</p>

<p align="center">
  <a href="https://github.com/velnox4827/saman-aether/releases/latest"><b>Download latest Stable APK</b></a>
  ·
  <a href="docs/USAGE.fa.md">راهنمای فارسی</a>
  ·
  <a href="docs/USAGE.en.md">English guide</a>
  ·
  <a href="docs/TERMUX.fa.md">Termux فارسی</a>
  ·
  <a href="https://t.me/SamanTunnel"><b>Telegram Community</b></a>
</p>

---

## Saman Tunnel v1.4.0 — Stable

نسخه پایدار فعلی Saman Tunnel بر پایه **Aether Core v1.8.0** ساخته شده است.

### مهم‌ترین تغییرات v1.4.0

این نسخه اصلاحات upstream مربوط به Aether v1.8.0 را دریافت می‌کند:

- پایداری بهتر **GOOL** و رفع سناریوهای Crash مربوط به توقف Tunnel/SOCKS
- مقاوم‌تر شدن **WireGuard** در برابر خطای Task/Panic
- اصلاح upstream مربوط به Authentication
- بهینه‌تر شدن **HTTP proxy**
- جلوگیری از reconnect loop پرمصرف در مقدار reconnect صفر
- ارتقای Core از **v1.7.0 → v1.8.0**

قابلیت‌های اختصاصی Saman نیز روی Core جدید Rebase شده‌اند:

- **Smart Reconnect** برای WG / MASQUE H3 / MASQUE H2 / GOOL
- WG cached RTT بالاتر از `650ms` → Fresh Balanced Scan
- MASQUE H3 cached verify بالاتر از `1800ms` → Fresh Balanced Scan
- MASQUE H2 cached verify بالاتر از `2500ms` → Fresh Balanced Scan
- Cached path/pair کوتاه‌تر از `20s` کنار گذاشته می‌شود
- Fresh Scan همچنان `balanced` است و Turbo اجباری نشده
- `--quick-reconnect`
- `--reconnect-secs 1`
- Restart بهتر Local Proxy با reusable listener و hand-off پورت

### Local proxies

```text
SOCKS5       127.0.0.1:1819
HTTP CONNECT 127.0.0.1:1820
```

> Saman Tunnel خودش VPN سراسری Android نیست؛ Local Proxy در اختیار برنامه‌های دیگر می‌گذارد.

### Modeها

- MASQUE HTTP/3
- MASQUE HTTP/2
- WireGuard
- GOOL

### Home Screen Widget

- Tap در حالت خاموش → اتصال با آخرین Mode
- Tap در حالت فعال → STOP
- پیش‌فرض اولیه → WireGuard
- آخرین Mode بین WG / H3 / H2 / GOOL حفظ می‌شود
- Widget همان `AetherService` اصلی را کنترل می‌کند

### Diagnostics

- Logs → آخرین 40 خط
- Safe report → مناسب اشتراک‌گذاری
- Full history → برای عیب‌یابی کامل‌تر

### Stable APKs

- `arm64-v8a`
- `armeabi-v7a`
- `universal-arm`

حداقل Android: **7.0 / API 24**

دانلود Stable:
https://github.com/velnox4827/saman-aether/releases/latest

---

## Aether Core

Upstream:
https://github.com/CluvexStudio/Aether

Pinned v1.8.0 commit:

```text
a916ff6fbbb4ebafe8314c53cf3718eb51dcae53
```

Aether Core version: **v1.8.0**

Saman Tunnel یک پروژه مستقل/مشتق‌شده است و برنامه رسمی Aether نیست.

License: **GNU AGPL-3.0**

---

## Saman Aether — Termux + Termux:Widget v1.4.0

نسخه Termux نیز بر پایه **Aether Core v1.8.0** به‌روزرسانی شده است.

Installer یک Core Patch‌شده با نام `saman-aether-core` نصب می‌کند و Aether رسمیِ جداگانه را overwrite نمی‌کند.

### Termux features

- Aether Core v1.8.0
- Smart Reconnect برای WG / MASQUE H3 / MASQUE H2 / GOOL
- SOCKS5 `127.0.0.1:1819`
- HTTP CONNECT `127.0.0.1:1820`
- `balanced` scan
- `quick-reconnect`
- `reconnect-secs 1`
- readiness از روی Log هر 250ms
- Safe / Full diagnostics
- arm64 / armv7 / x86_64

### نصب / آپدیت Termux

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash
```

### Termux:Widget shortcuts

```text
0-STOP-Aether
1-Aether-MASQUE       # H3
2-Aether-WG
3-Aether-GOOL
4-Aether-MASQUE-H2
5-Aether-SAFE-LOG
```

راهنمای کامل:
[docs/TERMUX.fa.md](docs/TERMUX.fa.md)

English:
[docs/TERMUX.en.md](docs/TERMUX.en.md)

Termux release:
https://github.com/velnox4827/saman-aether/releases/tag/termux-v1.4.0

---

## English

### Current Stable: Saman Tunnel v1.4.0

Saman Tunnel v1.4.0 is based on **Aether Core v1.8.0**.

Highlights:

- Upstream Aether v1.8.0 stability fixes
- MASQUE H3 / MASQUE H2 / WireGuard / GOOL
- Smart Reconnect
- Balanced scan remains the default
- SOCKS5 `127.0.0.1:1819`
- HTTP CONNECT `127.0.0.1:1820`
- Android Home Screen Widget
- Safe / Full diagnostics
- ARM64 / ARM32 / Universal ARM Stable APKs
- Minimum Android 7 / API 24

The Termux edition is also v1.4.0 and uses a patched Aether v1.8.0 core with one-tap Termux:Widget shortcuts.

GitHub:
https://github.com/velnox4827/saman-aether

Telegram:
https://t.me/SamanTunnel

Issues:
https://github.com/velnox4827/saman-aether/issues

Upstream Aether:
https://github.com/CluvexStudio/Aether

License: **GNU AGPL-3.0**

# Saman Tunnel

<p align="center">
  <img src="android-app/app/src/main/res/drawable-nodpi/saman_tunnel_logo.png" width="150" alt="Saman Tunnel logo">
</p>

<p align="center">
  <b>Standalone Aether Core frontend for Android — no Termux required</b><br>
  رابط مستقل هسته Aether برای اندروید — بدون نیاز به Termux
</p>

<p align="center">
  <a href="https://github.com/velnox4827/saman-aether/releases/latest"><b>Download latest APK</b></a>
  ·
  <a href="docs/USAGE.fa.md">راهنمای فارسی</a>
  ·
  <a href="docs/USAGE.en.md">English guide</a>
  ·
  <a href="https://t.me/SamanTunnel"><b>Telegram Community</b></a>
  ·
  <a href="https://github.com/velnox4827/saman-aether/issues">Issues & Suggestions</a>
</p>

---

## Saman Tunnel v1.3.5 — Stable

نسخه پایدار فعلی پروژه با **Aether Core v1.7.0** و پشتیبانی Stable از **ARM64، ARM32 و Universal ARM**.

### ✨ مهم‌ترین قابلیت جدید: Home Screen Widget

نسخه `v1.3.5` همچنان Widget کوچک و جمع‌وجور برای صفحه اصلی Android اضافه می‌کند.

- یک Tap در حالت خاموش → اتصال با **آخرین Mode استفاده‌شده**
- یک Tap در حالت فعال → **STOP**
- اگر هنوز Mode انتخاب نشده باشد → پیش‌فرض **WireGuard**
- Widget بدون بازکردن صفحه اصلی برنامه، همان `AetherService` داخلی را کنترل می‌کند
- آخرین Mode بین `WG`، `MASQUE H3`، `MASQUE H2` و `GOOL` حفظ می‌شود

وضعیت Widget:

```text
○ WG    خاموش
… WG    در حال اتصال / توقف
● WG    متصل
▲ WG    اتصال ناپایدار
! WG    خطا
```

نام Mode نیز مطابق آخرین انتخاب به `WG`، `H3`، `H2` یا `GOOL` تغییر می‌کند.

### سایر قابلیت‌ها

- **MASQUE HTTP/3 (QUIC)** و **MASQUE HTTP/2**
- **WireGuard** و **GOOL**
- تعویض Mode با Reset خودکار اتصال قبلی
- **SOCKS5** روی `127.0.0.1:1819`
- **HTTP CONNECT** روی `127.0.0.1:1820`
- رابط Light / Dark مطابق Theme سیستم
- کارت Status مرتب برای Connected، Unstable، Starting، Connecting، Switching، Stopping، Stopped و Error
- **Logs** برای مشاهده آخرین 40 خط
- **Safe diagnostics report** برای اشتراک‌گذاری: لاگ‌های اخیر، جمع‌وجور و با Redact شدن شناسه‌های شناخته‌شده
- **Full history diagnostics** برای عیب‌یابی کامل‌تر؛ ممکن است شامل شناسه‌ها و آدرس‌های شبکه باشد
- نمایش مرحله اتصال هنگام Connecting
- وضعیت و میانبر Battery Optimization
- **Check update** از GitHub Releases
- آیکن اختصاصی Launcher / App Info / Notification
- `arm64-v8a` / `armeabi-v7a` / Universal ARM
- حداقل Android 7 / API 24
- `compileSdk 36`
- `targetSdk 35`

## فارسی

### Saman Tunnel چیست؟

Saman Tunnel یک برنامه مستقل Android است که هسته متن‌باز **Aether** را داخل خود برنامه اجرا می‌کند و Proxy محلی در اختیار برنامه‌های دیگر می‌گذارد:

```text
SOCKS5       127.0.0.1:1819
HTTP CONNECT 127.0.0.1:1820
```

> Saman Tunnel خودش VPN سراسری Android نیست. برای عبور ترافیک برنامه‌های دیگر باید خروجی SOCKS5 یا HTTP CONNECT آن را داخل یک برنامه سازگار با Proxy/VPN استفاده کنید.

### دانلود

آخرین نسخه Stable:

**https://github.com/velnox4827/saman-aether/releases/latest**

معماری‌های Stable:

- **arm64-v8a** — پیشنهاد اصلی برای بیشتر گوشی‌های جدید
- **armeabi-v7a** — برای دستگاه‌های ARM 32-bit قدیمی‌تر
- **universal-arm** — شامل ARM32 + ARM64؛ حجم بیشتر ولی مناسب وقتی معماری دستگاه را نمی‌دانید

حداقل Android: **7.0 (API 24)**

### استفاده سریع از برنامه

1. Saman Tunnel را باز کنید.
2. یکی از Modeها را انتخاب کنید:
   - MASQUE HTTP/3
   - MASQUE HTTP/2
   - WireGuard
   - GOOL
3. صبر کنید Status به `Connected` برسد.
4. از یکی از Proxyهای زیر در برنامه مقصد استفاده کنید:

```text
SOCKS5       127.0.0.1:1819
HTTP CONNECT 127.0.0.1:1820
```

5. اگر برنامه دوم یک VPN سراسری Android می‌سازد، **فقط Saman Tunnel را در آن برنامه Bypass / Exclude کنید** تا Loop ایجاد نشود.

### استفاده از Widget

1. حداقل یک بار برنامه Saman Tunnel را باز کنید.
2. Mode موردنظر خود را یک بار انتخاب کنید؛ مثلاً WG یا H3.
3. از صفحه Home گوشی، بخش **Widgets** را باز کنید.
4. Widget مربوط به **Saman Tunnel** را روی صفحه اصلی قرار دهید.
5. وقتی خاموش است، روی Widget بزنید تا آخرین Mode اجرا شود.
6. وقتی فعال است، دوباره روی Widget بزنید تا STOP شود.

Widget مستقیماً همان سرویس اصلی Saman Tunnel را کنترل می‌کند و Proxy جداگانه یا هسته دوم ایجاد نمی‌کند.


### Modeها

| Mode | توضیح |
|---|---|
| MASQUE HTTP/3 | MASQUE مبتنی بر QUIC/HTTP/3؛ مناسب شبکه‌هایی که QUIC روی آن‌ها خوب کار می‌کند |
| MASQUE HTTP/2 | جایگزین برای شبکه‌هایی که HTTP/3/QUIC محدود یا ناپایدار است |
| WireGuard | حالت ساده‌تر و معمولاً مناسب برای شروع |
| GOOL | تونل WireGuard داخل WireGuard؛ سنگین‌تر و وابسته‌تر به کیفیت شبکه |

عملکرد هر Mode به شبکه، اپراتور و شرایط فیلترینگ بستگی دارد.

### Diagnostics

- **Logs** → بررسی سریع Logهای اخیر
- **Safe report — recommended** → گزارش اخیر و کم‌حجم‌تر با Redact شدن شناسه‌های شناخته‌شده
- **Full history** → همه Logهای نگه‌داری‌شده برای عیب‌یابی پیشرفته

> Full history ممکن است شامل شناسه‌های Aether یا آدرس‌های شبکه باشد؛ قبل از انتشار عمومی فایل را بررسی کنید.

گزارش مشکل:

https://github.com/velnox4827/saman-aether/issues/new?template=bug_report.md

پیشنهاد قابلیت:

https://github.com/velnox4827/saman-aether/issues/new?template=feature_request.md

### 💬 Saman Tunnel • Community

https://t.me/SamanTunnel

برای آموزش، تست نسخه‌ها، گزارش تجربه روی شبکه‌های مختلف و گفتگو درباره پروژه.

### Battery Optimization

برای پایداری بهتر اتصال در پس‌زمینه، Battery Optimization را برای Saman Tunnel روی **Unrestricted / No restrictions** قرار دهید.

### Aether Core

Upstream:

https://github.com/CluvexStudio/Aether

Pinned commit:

```text
e05cbd78b8f17873abee553904a85610b88c0382
```

Aether Core version: **v1.7.0**

### مجوز و برند

Saman Tunnel یک پروژه مستقل است و برنامه رسمی Aether نیست.

License: **GNU AGPL-3.0**

---

## English

Saman Tunnel is a standalone Android frontend for **Aether Core v1.7.0**.

Local proxies:

```text
SOCKS5       127.0.0.1:1819
HTTP CONNECT 127.0.0.1:1820
```

### Current Stable: v1.3.5

- Compact Android home-screen widget
- Tap while stopped → start the last selected mode
- Tap while active → stop Saman Tunnel
- Defaults to WireGuard if no mode was previously selected
- Remembers WG / MASQUE H3 / MASQUE H2 / GOOL
- Shows OFF / working / connected / unstable / error state directly on the widget

The widget controls the same `AetherService`; it does not start a second proxy core.


### Main features

- MASQUE HTTP/3 and HTTP/2
- WireGuard and GOOL
- Local SOCKS5 + HTTP CONNECT
- One-tap Home Screen Widget
- Foreground Android service
- System Light/Dark theme
- Multi-line runtime status
- Last 40 log lines viewer
- Safe / Full diagnostics export
- Battery optimization shortcut
- Manual GitHub update checker
- Stable ARM64 / ARM32 / Universal ARM APKs
- Faster connection readiness detection
- Isolated Aether Core process
- Safe + Full diagnostics options
- arm64-v8a / armeabi-v7a / Universal ARM
- Minimum Android 7 / API 24

Download:

https://github.com/velnox4827/saman-aether/releases/latest

Telegram:

https://t.me/SamanTunnel

Issues:

https://github.com/velnox4827/saman-aether/issues

Upstream Aether:

https://github.com/CluvexStudio/Aether

License: **GNU AGPL-3.0**

---

## دو روش استفاده / Two ways to use this project

### 1) Saman Tunnel APK

روش پیشنهادی برای بیشتر کاربران:

- بدون Termux
- Home Screen Widget
- MASQUE H3 / H2
- WireGuard / GOOL
- SOCKS5 `127.0.0.1:1819`
- HTTP CONNECT `127.0.0.1:1820`
- Diagnostics
- Update checker

### 2) Saman Aether — Termux + Termux:Widget

نسخه Termux پروژه همچنان داخل همین Repository نگه‌داری می‌شود.

Shortcutها:

```text
0-STOP-Aether
1-Aether-MASQUE
2-Aether-WG
3-Aether-GOOL
```

نصب سریع:

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash
```

راهنمای فارسی:

[docs/TERMUX.fa.md](docs/TERMUX.fa.md)

English guide:

[docs/TERMUX.en.md](docs/TERMUX.en.md)

هر دو روش از Aether استفاده می‌کنند ولی مستقل از هم هستند.

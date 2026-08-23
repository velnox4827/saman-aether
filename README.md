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

## Saman Tunnel v1.2.1

نسخه پایدار فعلی پروژه با **Aether Core v1.7.0**.

### تغییرات مهم نسخه‌های 1.2.x

- رابط کاربری خودکار **Light / Dark** بر اساس تنظیمات Android
- آیکن اختصاصی Launcher، App Info و Notification
- انتخاب دو حالت **MASQUE HTTP/3 (QUIC)** و **MASQUE HTTP/2**
- حالت‌های **WireGuard** و **GOOL**
- تعویض Mode با Reset خودکار اتصال قبلی؛ در حالت عادی نیازی به زدن STOP قبل از تغییر Mode نیست
- خروجی محلی **SOCKS5** روی `127.0.0.1:1819`
- خروجی محلی **HTTP CONNECT** روی `127.0.0.1:1820`
- کارت Status مرتب و چندخطی برای همه وضعیت‌ها: Connected، Connection unstable، Starting، Connecting، Switching، Stopping، Stopped و Error
- نمایش سریع **40 خط آخر Log** از داخل برنامه
- **Save TXT** برای ذخیره گزارش کامل Logهای فعلی App و Aether Core
- نمایش وضعیت Battery Optimization و میانبر تنظیمات باتری
- **Check update** مستقیم از GitHub Releases
- جلوگیری از Startهای پشت‌سرهم هنگام Connecting
- Build برای **arm64-v8a** با حداقل Android 7 (API 24)
- `compileSdk 36` و `targetSdk 35`

> نسخه `v1.2.1` یک بهبود رابط کاربری است و رفتار شبکه نسخه `v1.2.0` را حفظ می‌کند؛ مهم‌ترین تغییر آن مرتب‌شدن همه وضعیت‌های اتصال در کارت بالای برنامه است تا متن‌های طولانی از صفحه بیرون نزنند.

## فارسی

### Saman Tunnel چیست؟

**Saman Tunnel** یک برنامه مستقل Android است که هسته متن‌باز **Aether** را داخل خود برنامه اجرا می‌کند و دو Proxy محلی در اختیار برنامه‌های دیگر قرار می‌دهد:

```text
SOCKS5       127.0.0.1:1819
HTTP CONNECT 127.0.0.1:1820
```

ویژگی‌ها:

- بدون Termux
- بدون Termux:Widget
- اجرای مستقیم MASQUE / WireGuard / GOOL
- MASQUE با دو انتخاب HTTP/3 و HTTP/2
- SOCKS5 و HTTP CONNECT مستقیم از Aether Core
- نمایش نسخه App و Aether Core
- سرویس Foreground برای ادامه کار در پس‌زمینه
- نمایش Status مرتب برای همه حالت‌های اتصال
- مشاهده سریع 40 خط آخر Log
- ذخیره Diagnostics کامل در فایل TXT
- مناسب برای استفاده به‌عنوان upstream Proxy در برنامه‌های دیگر

> Saman Tunnel خودش VPN سراسری Android نیست. برای عبور ترافیک کل گوشی باید خروجی SOCKS5 یا HTTP CONNECT آن را داخل یک برنامه سازگار با Proxy/VPN استفاده کنید.

### دانلود و نصب

آخرین نسخه Stable را از GitHub Releases دانلود کنید:

**https://github.com/velnox4827/saman-aether/releases/latest**

نسخه فعلی برای **arm64-v8a** ساخته می‌شود.

حداقل Android: **7.0 (API 24)**

### استفاده سریع

1. Saman Tunnel را باز کنید.
2. یکی از حالت‌های **MASQUE**، **WireGuard** یا **GOOL** را انتخاب کنید.
3. در MASQUE می‌توانید بین **HTTP/3 (QUIC)** و **HTTP/2** انتخاب کنید.
4. صبر کنید Status به `Connected` برسد.
5. بعد از اتصال این دو Proxy آماده هستند:

```text
SOCKS5       127.0.0.1:1819
HTTP CONNECT 127.0.0.1:1820
```

6. در برنامه مقصد، نوع Proxy موردنیاز را انتخاب و Host/Port را مطابق بالا وارد کنید.
7. اگر برنامه دوم VPN سراسری Android می‌سازد، **فقط Saman Tunnel را در آن برنامه Bypass/Exclude کنید** تا Loop ایجاد نشود.

راهنمای کامل فارسی: [docs/USAGE.fa.md](docs/USAGE.fa.md)

### حالت‌ها

| Mode | توضیح کوتاه |
|---|---|
| MASQUE HTTP/3 | حالت پیش‌فرض MASQUE مبتنی بر QUIC/HTTP/3؛ مناسب شبکه‌هایی که QUIC روی آن‌ها خوب کار می‌کند |
| MASQUE HTTP/2 | جایگزین MASQUE برای شبکه‌هایی که HTTP/3/QUIC محدود یا ناپایدار است |
| WireGuard | حالت ساده‌تر و معمولاً سریع‌تر؛ انتخاب مناسب برای شروع تست |
| GOOL | تونل WireGuard داخل WireGuard؛ سنگین‌تر و وابسته‌تر به کیفیت شبکه |

عملکرد هر Mode به اپراتور، فیلترینگ، کیفیت مسیر و شبکه کاربر بستگی دارد.

### Status و وضعیت اتصال

نمونه وضعیت متصل:

```text
Mode: WG
Status: Connected
SOCKS5 :1819 + HTTP :1820
```

نمونه وضعیت ناپایدار:

```text
Mode: WG
Status: Connection unstable
Checking SOCKS5 127.0.0.1:1819
```

در v1.2.1 همه وضعیت‌ها شامل Connected، Connection unstable، Starting، Connecting، Switching، Stopping، Stopped و Error با چیدمان مرتب داخل کارت نمایش داده می‌شوند.

### Diagnostics و گزارش مشکل

- **Logs**: نمایش سریع آخرین **40 خط** Log
- **Save TXT**: ذخیره گزارش کامل Logهای فعلی App و Aether Core

فایل گزارش می‌تواند شامل IP، Endpoint و جزئیات فنی اتصال باشد؛ قبل از انتشار عمومی آن را بررسی کنید.

**گزارش مشکل:**  
https://github.com/velnox4827/saman-aether/issues/new?template=bug_report.md

**پیشنهاد قابلیت:**  
https://github.com/velnox4827/saman-aether/issues/new?template=feature_request.md

### 💬 گروه تلگرام

**Saman Tunnel • Community**

https://t.me/SamanTunnel

برای آموزش، تست نسخه‌ها، پرسش، گزارش تجربه روی اپراتورهای مختلف و گفتگو درباره پروژه.

### Battery Optimization

برای پایداری اتصال در پس‌زمینه، Battery Optimization را برای **Saman Tunnel** روی **Unrestricted / No restrictions** قرار دهید.

### پروژه Aether

Saman Tunnel از هسته رسمی و متن‌باز Aether استفاده می‌کند:

https://github.com/CluvexStudio/Aether

Build اندروید هسته به Commit زیر Pin شده است:

```text
e05cbd78b8f17873abee553904a85610b88c0382
```

این Build در حال حاضر **Aether Core v1.7.0** را ارائه می‌دهد.

### نام و برند

**Saman Tunnel** یک پروژه مستقل است و پروژه رسمی Aether نیست.

### مجوز

این پروژه تحت **GNU AGPL-3.0** منتشر می‌شود. متن مجوز در فایل [LICENSE](LICENSE) قرار دارد.

---

## English

**Saman Tunnel** is a standalone Android frontend that embeds **Aether Core v1.7.0** and exposes:

```text
SOCKS5       127.0.0.1:1819
HTTP CONNECT 127.0.0.1:1820
```

Main features:

- MASQUE HTTP/3 (QUIC) and HTTP/2
- WireGuard and GOOL
- Automatic clean reset when switching modes
- Local SOCKS5 + HTTP CONNECT
- Foreground Android service
- System Light/Dark theme
- Clean multi-line status card for all runtime states
- Last 40 log lines viewer
- Full TXT diagnostics export
- Battery optimization shortcut
- Manual GitHub update checker
- arm64-v8a
- Minimum Android 7 / API 24

Saman Tunnel is not a full-device Android VPN by itself. Use its local proxy endpoints from a compatible proxy/VPN client.

If another VPN app consumes Saman Tunnel's proxy, bypass/exclude **Saman Tunnel itself** to avoid routing loops.

Download latest Stable APK:

**https://github.com/velnox4827/saman-aether/releases/latest**

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

روش ساده‌تر و پیشنهادی:

- بدون Termux
- بدون Termux:Widget
- MASQUE HTTP/3 / HTTP/2
- WireGuard / GOOL
- SOCKS5 روی `127.0.0.1:1819`
- HTTP CONNECT روی `127.0.0.1:1820`
- Diagnostics و Update checker داخل برنامه

راهنمای فارسی: [docs/USAGE.fa.md](docs/USAGE.fa.md)

### 2) Saman Aether — Termux + Termux:Widget

نسخه Termux پروژه **حذف نشده** و همچنان داخل همین Repository نگه‌داری می‌شود.

Shortcutهای Termux:Widget:

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

راهنمای کامل فارسی:

**[docs/TERMUX.fa.md](docs/TERMUX.fa.md)**

English guide:

**[docs/TERMUX.en.md](docs/TERMUX.en.md)**

فایل‌های این روش:

```text
install.sh
aether-shortcut-runner
shortcuts/
```

هر دو روش از Aether استفاده می‌کنند ولی مستقل از هم هستند.


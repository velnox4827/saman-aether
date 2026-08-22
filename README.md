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
  <a href="https://github.com/velnox4827/saman-aether/issues">Issues & Suggestions</a>
</p>

---

## فارسی

### Saman Tunnel چیست؟

**Saman Tunnel** یک برنامه مستقل Android است که هسته متن‌باز **Aether** را داخل خود برنامه اجرا می‌کند و یک SOCKS5 محلی می‌سازد.

- بدون Termux
- بدون Termux:Widget
- اجرای مستقیم MASQUE / WireGuard / GOOL
- SOCKS5 محلی: `127.0.0.1:1819`
- نمایش نسخه App و Aether Core
- سرویس Foreground برای ادامه کار در پس‌زمینه
- ذخیره Diagnostics در فایل TXT
- مناسب برای استفاده به‌عنوان upstream SOCKS5 در برنامه‌های دیگر

> Saman Tunnel خودش VPN سراسری Android نیست. خروجی برنامه یک SOCKS5 محلی است. برای عبور ترافیک کل گوشی باید از یک برنامه سازگار با SOCKS5 یا زنجیره Proxy/VPN استفاده کنید.

### دانلود و نصب

به صفحه **Releases** بروید و جدیدترین فایل APK را دانلود کنید:

**https://github.com/velnox4827/saman-aether/releases/latest**

نسخه فعلی فقط برای **arm64-v8a** ساخته می‌شود.

حداقل Android: **7.0 (API 24)**

### استفاده سریع

1. Saman Tunnel را باز کنید.
2. یکی از حالت‌های **MASQUE**، **WireGuard** یا **GOOL** را انتخاب کنید.
3. صبر کنید Status به `Connected` برسد.
4. SOCKS5 روی آدرس زیر آماده است:

```text
127.0.0.1:1819
```

5. در برنامه‌ای که قرار است از این Proxy استفاده کند، نوع Proxy را **SOCKS5** و Host/Port را مطابق بالا تنظیم کنید.
6. اگر برنامه دوم VPN سراسری Android می‌سازد، **فقط Saman Tunnel را در آن برنامه Bypass/Exclude کنید** تا Loop ایجاد نشود.

راهنمای کامل فارسی: [docs/USAGE.fa.md](docs/USAGE.fa.md)

### حالت‌ها

| Mode | توضیح کوتاه |
|---|---|
| MASQUE | تونل مدرن مبتنی بر ترافیک وب؛ انتخاب مناسب برای شروع |
| WireGuard | ساده‌تر و معمولاً سریع‌تر |
| GOOL | WireGuard داخل WireGuard؛ سنگین‌تر و وابسته‌تر به کیفیت شبکه |

### Diagnostics

اگر مشکلی دیدید:

1. وارد بخش **Diagnostics** شوید.
2. **Save TXT** را بزنید.
3. فایل `Saman-Tunnel-diagnostics.txt` را ذخیره کنید.
4. قبل از انتشار عمومی، فایل را بررسی کنید؛ Log می‌تواند شامل IP، Endpoint و اطلاعات فنی اتصال باشد.
5. در GitHub یک Issue بسازید و اطلاعات لازم را بنویسید.

**گزارش مشکل:**  
https://github.com/velnox4827/saman-aether/issues/new?template=bug_report.md

**پیشنهاد قابلیت:**  
https://github.com/velnox4827/saman-aether/issues/new?template=feature_request.md

### نکته Battery Optimization

اگر Android برنامه را در پس‌زمینه می‌بندد، Battery Optimization را برای **Saman Tunnel** روی **Unrestricted / No restrictions** قرار دهید. اگر از یک VPN/Proxy دیگر برای مصرف SOCKS5 استفاده می‌کنید، بهتر است همان برنامه هم محدودیت باتری نداشته باشد.

### پروژه Aether

Saman Tunnel از هسته رسمی و متن‌باز Aether استفاده می‌کند:

https://github.com/CluvexStudio/Aether

نسخه عمومی `v1.0.0` هسته را از Commit زیر Build می‌کند تا Build قابل تکرار باشد:

```text
e05cbd78b8f17873abee553904a85610b88c0382
```

این Build در حال حاضر Aether Core `v1.7.0` را ارائه می‌دهد.

### نام و برند

**Saman Tunnel** یک پروژه مستقل است و پروژه رسمی Aether نیست. نام و لوگوی Saman Tunnel مستقل از برند Aether طراحی شده‌اند.

### مجوز

این پروژه با توجه به استفاده از Aether Core تحت **GNU AGPL-3.0** منتشر می‌شود. متن مجوز در فایل [LICENSE](LICENSE) قرار دارد.

---

## English

**Saman Tunnel** is a standalone Android frontend that embeds the open-source **Aether Core** and exposes a local SOCKS5 proxy at:

```text
127.0.0.1:1819
```

No Termux is required.

Main features:

- MASQUE, WireGuard and GOOL modes
- Local SOCKS5 proxy
- Foreground Android service
- App/Core version display
- TXT diagnostics export
- Built-in connection status
- Designed for use as an upstream SOCKS5 proxy

Download the latest APK from:

**https://github.com/velnox4827/saman-aether/releases/latest**

Full English guide: [docs/USAGE.en.md](docs/USAGE.en.md)

For bugs and feature requests, please use **GitHub Issues**:

https://github.com/velnox4827/saman-aether/issues

Saman Tunnel is an independent project and is not the official Aether application.

Upstream Aether:

https://github.com/CluvexStudio/Aether

License: **GNU AGPL-3.0**

---

## دو روش استفاده / Two ways to use this project

این Repository عمداً **هر دو روش** را نگه می‌دارد:

### 1) Saman Tunnel APK

روش ساده‌تر و پیشنهادی برای بیشتر کاربران:

- بدون Termux
- بدون Termux:Widget
- اپ مستقل Android
- MASQUE / WireGuard / GOOL
- SOCKS5 روی `127.0.0.1:1819`

راهنمای فارسی: [docs/USAGE.fa.md](docs/USAGE.fa.md)

### 2) Saman Aether — Termux + Termux:Widget

نسخه‌ی قبلی پروژه **حذف نشده** و همچنان داخل همین Repository نگه‌داری می‌شود.

این روش Aether رسمی را داخل Termux نصب/آپدیت می‌کند و چهار Shortcut برای **Termux:Widget** می‌سازد:

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

راهنمای کامل فارسی Termux + Widget:

**[docs/TERMUX.fa.md](docs/TERMUX.fa.md)**

English Termux guide:

**[docs/TERMUX.en.md](docs/TERMUX.en.md)**

فایل‌های این روش همچنان در ریشه پروژه هستند:

```text
install.sh
aether-shortcut-runner
shortcuts/
```

هر دو روش از Aether استفاده می‌کنند، ولی مستقل از هم هستند؛ کاربر می‌تواند APK مستقل را استفاده کند یا روش Termux/Widget را انتخاب کند.

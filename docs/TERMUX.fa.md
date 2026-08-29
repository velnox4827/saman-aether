# راهنمای Saman Aether — Termux + Termux:Widget v1.4.0

این نسخه، روش Termux پروژه Saman Tunnel است و مستقل از APK اندروید اجرا می‌شود.

از v1.4.0 به بعد، Installer نسخه Termux دیگر مجبور نیست باینری رسمی خام Aether را برای قابلیت‌های سامان استفاده کند. یک باینری Patch‌شده با نام زیر نصب می‌شود:

```text
$PREFIX/bin/saman-aether-core
```

این فایل کنار `aether` رسمی نصب می‌شود و آن را overwrite نمی‌کند.

## تغییرات v1.4.0

- Smart Reconnect برای WireGuard، MASQUE H3، MASQUE H2 و GOOL
- Scan پیش‌فرض همچنان `balanced`
- `--quick-reconnect` همچنان فعال
- `--reconnect-secs 1`
- SOCKS5 روی `127.0.0.1:1819`
- HTTP CONNECT روی `127.0.0.1:1820`
- Listenerهای محلی Patch شده‌اند تا Restart بعد از Stop تمیزتر انجام شود
- قبل از Start، فقط Listenerهای واقعی 1819/1820 بررسی می‌شوند؛ TIME_WAIT به‌عنوان Proxy فعال در نظر گرفته نمی‌شود
- readiness هر 250ms از Log خود Aether بررسی می‌شود و اتصال تستی به SOCKS ایجاد نمی‌کند
- MASQUE H2 به Termux:Widget اضافه شده
- Log فعلی و Previous برای هر Mode نگه‌داری می‌شود
- Diagnostics در دو حالت Safe و Full
- نمایش مرحله اتصال در Termux

## بهبودهای Aether v1.8.0

این نسخه علاوه بر Patchهای Saman، اصلاحات upstream v1.8.0 را هم دارد؛ از جمله پایداری بهتر GOOL، مقاومت بیشتر WireGuard در برابر خطای Task/Panic، اصلاح Authentication، بهینه‌سازی HTTP proxy و جلوگیری از reconnect loop در مقدار صفر.

## Smart Reconnect

### WireGuard

Cached endpoint:

```text
RTT <= 650ms
→ Quick Reconnect

RTT > 650ms
→ Fresh Balanced Scan
```

اگر Cached tunnel در کمتر از 20 ثانیه از کار بیفتد، مسیر Cache‌شده کنار گذاشته می‌شود.

### MASQUE H3

```text
Cached verify <= 1800ms
→ reuse

Cached verify > 1800ms
→ Fresh Balanced Scan
```

### MASQUE H2

```text
Cached verify <= 2500ms
→ reuse

Cached verify > 2500ms
→ Fresh Balanced Scan
```

### GOOL

اگر Pair مربوط به Outer + Inner در کمتر از 20 ثانیه از کار بیفتد، Pair کنار گذاشته می‌شود و Fresh Balanced Scan انجام می‌شود.

> این آستانه‌ها فقط برای تصمیم‌گیری درباره Cache هستند. Endpointهایی که در Fresh Balanced Scan پیدا می‌شوند صرفاً به دلیل این آستانه‌ها رد نمی‌شوند.

## پیش‌نیازها

- Termux
- Termux:Widget

بهتر است هر دو از یک منبع سازگار نصب شده باشند.

## نصب / آپدیت

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash
```

Installer معماری دستگاه را تشخیص می‌دهد و یکی از Coreهای آماده را دانلود می‌کند:

```text
arm64
armv7
x86_64
```

فایل با SHA256 بررسی می‌شود.

## Shortcutهای Termux:Widget

```text
0-STOP-Aether
1-Aether-MASQUE
2-Aether-WG
3-Aether-GOOL
4-Aether-MASQUE-H2
5-Aether-SAFE-LOG
```

`1-Aether-MASQUE` برای سازگاری با نسخه‌های قدیمی نام قبلی را نگه داشته و MASQUE H3 است.

## Proxyها

```text
SOCKS5
127.0.0.1:1819

HTTP CONNECT
127.0.0.1:1820
```

Saman Aether خودش VPN سراسری Android نیست؛ Local Proxy می‌سازد.

## تنظیمات Modeها

### MASQUE H3

```text
--masque -4
--bind 127.0.0.1:1819
--http-proxy 127.0.0.1:1820
--reconnect-secs 1
--scan balanced
--noize firewall
--quick-reconnect
```

### MASQUE H2

```text
--masque --h2 -4
--bind 127.0.0.1:1819
--http-proxy 127.0.0.1:1820
--reconnect-secs 1
--scan balanced
--noize firewall
--quick-reconnect
```

### WireGuard

```text
--wg -4
--bind 127.0.0.1:1819
--http-proxy 127.0.0.1:1820
--reconnect-secs 1
--scan balanced
--noize balanced
--keepalive 5
--quick-reconnect
```

### GOOL

```text
--gool -4
--bind 127.0.0.1:1819
--http-proxy 127.0.0.1:1820
--reconnect-secs 1
--scan balanced
--noize balanced
--keepalive 5
--quick-reconnect
```

## Diagnostics

گزارش پیشنهادی برای اشتراک‌گذاری:

```bash
saman-aether-diagnostics safe
```

یا Shortcut:

```text
5-Aether-SAFE-LOG
```

گزارش کامل:

```bash
saman-aether-diagnostics full
```

Safe report فقط قسمت‌های اخیر Log را جمع می‌کند و بعضی شناسه‌های شناخته‌شده را Redact می‌کند. Full history برای عیب‌یابی کامل است و قبل از انتشار عمومی باید بررسی شود.

اگر `termux-setup-storage` قبلاً اجرا شده باشد، فایل Diagnostics در Downloads ذخیره می‌شود؛ در غیر این صورت داخل `$HOME` قرار می‌گیرد.

## Logها

```text
~/aether-wg.log
~/aether-wg.previous.log

~/aether-gool.log
~/aether-gool.previous.log

~/aether-masque-h3.log
~/aether-masque-h3.previous.log

~/aether-masque-h2.log
~/aether-masque-h2.previous.log
```

## Restart و Port Conflict

قبل از Start، Runner تا حدود 2 ثانیه برای آزادشدن Listener قبلی صبر می‌کند.

بررسی پورت با `ss -ltn` انجام می‌شود و به SOCKS وصل نمی‌شود؛ بنابراین readiness/port check خودش `early eof` مصنوعی تولید نمی‌کند.

اگر برنامه دیگری واقعاً روی 1819 یا 1820 Listener داشته باشد، Start متوقف می‌شود و پورت درگیر نمایش داده می‌شود.

## حذف نسخه Termux سامان

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash -s -- uninstall
```

این دستور `saman-aether-core`، Runner، Diagnostics و Shortcutهای سامان را حذف می‌کند.

اگر Aether رسمی را جداگانه نصب کرده باشید، دست‌نخورده باقی می‌ماند.

## Source / License

Core این نسخه از Aether Core v1.8.0 و Patchهای موجود در همین Repository ساخته می‌شود.

Upstream:

https://github.com/CluvexStudio/Aether

Repository:

https://github.com/velnox4827/saman-aether

License: GNU AGPL-3.0

## گروه تلگرام

https://t.me/SamanTunnel

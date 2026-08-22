# راهنمای Saman Aether با Termux + Termux:Widget

این راهنما مربوط به **روش Termux** پروژه است. این روش مستقل از APK برنامه Saman Tunnel است و برای کسانی است که می‌خواهند Aether رسمی را داخل Termux اجرا کنند و از میانبرهای Termux:Widget استفاده کنند.

## پیش‌نیازها

روی Android نصب کنید:

1. **Termux**
2. **Termux:Widget**

بهتر است هر دو را از یک منبع سازگار نصب کنید تا مشکل Signature پیش نیاید.

## نصب

Termux را باز کنید و اجرا کنید:

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash
```

Installer:

- پکیج‌های لازم را نصب می‌کند.
- Aether رسمی را نصب یا آپدیت می‌کند.
- Runner سامان را نصب می‌کند.
- Shortcutهای Termux:Widget را می‌سازد.

بعد از پایان نصب باید این Shortcutها ساخته شوند:

```text
0-STOP-Aether
1-Aether-MASQUE
2-Aether-WG
3-Aether-GOOL
```

## اضافه کردن Termux:Widget

1. به Home Screen گوشی بروید.
2. روی فضای خالی چند ثانیه نگه دارید.
3. **Widgets** را باز کنید.
4. **Termux:Widget** را پیدا کنید.
5. Widget را روی صفحه قرار دهید.
6. در صورت نیاز Widget را Refresh کنید.

بعد باید Shortcutهای بالا نمایش داده شوند.

## عملکرد Shortcutها

| Shortcut | عملکرد |
|---|---|
| `0-STOP-Aether` | توقف Aether |
| `1-Aether-MASQUE` | اجرای MASQUE |
| `2-Aether-WG` | اجرای WireGuard |
| `3-Aether-GOOL` | اجرای GOOL |

## SOCKS5

بعد از اتصال موفق:

```text
Protocol: SOCKS5
Host: 127.0.0.1
Port: 1819
```

## تنظیمات فعلی Shortcutها

### MASQUE

```text
--masque -4 --bind 127.0.0.1:1819 --scan balanced --noize firewall --quick-reconnect
```

### WireGuard

```text
--wg -4 --bind 127.0.0.1:1819 --scan balanced --noize balanced --keepalive 5 --quick-reconnect
```

### GOOL

```text
--gool -4 --bind 127.0.0.1:1819 --scan balanced --noize balanced --keepalive 5 --quick-reconnect
```

## آپدیت

برای آپدیت Aether و Refresh شدن Shortcutها همان دستور نصب را دوباره اجرا کنید:

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash
```

## حذف Shortcutهای سامان

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash -s -- uninstall
```

این کار Shortcutها و Runner سامان را حذف می‌کند، ولی Aether اصلی را حذف نمی‌کند.

## فایل‌های پروژه مربوط به Termux

```text
install.sh
aether-shortcut-runner
shortcuts/
├── 0-STOP-Aether
├── 1-Aether-MASQUE
├── 2-Aether-WG
└── 3-Aether-GOOL
```

## گزارش مشکل یا پیشنهاد

اگر مشکل مربوط به Saman Aether / Termux / Widget بود، در GitHub Issue باز کنید:

https://github.com/velnox4827/saman-aether/issues

در عنوان یا متن Issue بنویسید که از **Termux/Widget version** استفاده می‌کنید تا با نسخه APK اشتباه نشود.

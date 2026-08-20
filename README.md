# Saman Aether 🚀

<p align="center">
  <b>Easy Aether setup for Android + Termux:Widget shortcuts</b><br>
  نصب آسان Aether روی اندروید به همراه میانبرهای آماده برای Termux:Widget
</p>

<p align="center">
  <a href="https://github.com/velnox4827/saman-aether">
    <img alt="GitHub repo" src="https://img.shields.io/badge/GitHub-saman--aether-black?logo=github">
  </a>
  <a href="https://github.com/CluvexStudio/Aether">
    <img alt="Aether" src="https://img.shields.io/badge/Powered%20by-Aether-blue">
  </a>
  <img alt="Android" src="https://img.shields.io/badge/Platform-Android-green?logo=android">
  <img alt="Termux" src="https://img.shields.io/badge/Termux-supported-000000?logo=gnubash">
</p>

---

## 🇮🇷 فارسی

### معرفی

**Saman Aether** یک Installer/Helper ساده برای اجرای پروژه رسمی **Aether** روی Android از طریق **Termux** است.

هدف پروژه این است که بعد از نصب اولیه، برای اجرای حالت‌های مختلف Aether لازم نباشد هر بار دستورهای طولانی داخل Termux وارد کنید. میانبرهای آماده در **Termux:Widget** ساخته می‌شوند و می‌توانید مستقیماً از صفحه اصلی گوشی Aether را اجرا یا متوقف کنید.

این پروژه خودِ Aether را جایگزین نمی‌کند و از پروژه رسمی Aether استفاده می‌کند:

**Official Aether:**  
https://github.com/CluvexStudio/Aether

---

### ✨ امکانات

- نصب یا به‌روزرسانی Aether با استفاده از Installer رسمی
- ساخت خودکار میانبرهای Termux:Widget
- اجرای سریع حالت‌های:
  - MASQUE
  - WireGuard (WG)
  - GOOL
- توقف Aether با یک میانبر
- SOCKS5 محلی روی:
  - `127.0.0.1:1819`
- نمایش IP خروجی و کشور بعد از اتصال
- مانیتور کردن تغییر IP هنگام Reconnect
- ذخیره Log جداگانه برای هر Mode
- Backup گرفتن از Shortcutهای قبلی قبل از جایگزینی
- امکان Update با اجرای دوباره همان دستور نصب
- امکان حذف Shortcutها بدون حذف Aether اصلی

---

## 📱 پیش‌نیازها

روی گوشی Android این دو برنامه را نصب کنید:

1. **Termux**
2. **Termux:Widget**

> پیشنهاد می‌شود Termux و Termux:Widget را از یک منبع یکسان نصب کنید تا با خطای ناسازگاری Signature مواجه نشوید.

برای نصب خود Saman Aether نیازی به Downloads folder یا اجرای `termux-setup-storage` نیست.

---

## 🚀 نصب سریع

Termux را باز کنید و این دستور را اجرا کنید:

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash
```

Installer به‌صورت خودکار:

1. پکیج‌های لازم را نصب می‌کند.
2. Installer رسمی Aether را دریافت می‌کند.
3. اگر Aether نصب نباشد، آن را نصب می‌کند.
4. اگر Aether از قبل نصب باشد، آن را Update می‌کند.
5. Shortcutهای Saman Aether را در Termux ایجاد می‌کند.

در پایان باید پیامی شبیه این ببینید:

```text
INSTALL COMPLETE
```

و این Shortcutها ساخته می‌شوند:

```text
0-STOP-Aether
1-Aether-MASQUE
2-Aether-WG
3-Aether-GOOL
```

---

## 🧩 اضافه کردن Termux:Widget به صفحه اصلی

بعد از اتمام نصب:

1. از Termux خارج شوید.
2. روی یک قسمت خالی از Home Screen چند ثانیه نگه دارید.
3. وارد بخش **Widgets** شوید.
4. **Termux:Widget** را پیدا کنید.
5. ویجت را روی صفحه اصلی قرار دهید.
6. اگر Shortcutها بلافاصله نمایش داده نشدند، Widget را Refresh کنید.
7. در صورت نیاز یک بار Termux را باز و دوباره به Home Screen برگردید.

بعد باید این گزینه‌ها را ببینید:

```text
0-STOP-Aether
1-Aether-MASQUE
2-Aether-WG
3-Aether-GOOL
```

---

## ▶️ کاربرد Shortcutها

| Shortcut | عملکرد |
|---|---|
| `0-STOP-Aether` | متوقف کردن Aether |
| `1-Aether-MASQUE` | اجرای Aether در حالت MASQUE |
| `2-Aether-WG` | اجرای Aether در حالت WireGuard |
| `3-Aether-GOOL` | اجرای Aether در حالت GOOL |

برای اجرا فقط روی Mode موردنظر در Widget بزنید.

---

## 🌐 SOCKS5 Proxy

بعد از اتصال موفق Aether، SOCKS5 محلی روی این آدرس در دسترس است:

```text
Host: 127.0.0.1
Port: 1819
Protocol: SOCKS5
```

یا به شکل کوتاه:

```text
127.0.0.1:1819
```

می‌توانید این Proxy را در برنامه‌هایی که از SOCKS5 پشتیبانی می‌کنند استفاده کنید.

---

## 🔄 به‌روزرسانی

برای Update کردن Aether و Refresh شدن Shortcutها، دوباره همان دستور نصب را اجرا کنید:

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash
```

اگر Aether از قبل نصب باشد، Installer از حالت Update استفاده می‌کند.

---

## 🗑 حذف Shortcutهای Saman Aether

برای حذف Shortcutها و Runner:

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash -s -- uninstall
```

این دستور:

- Shortcutهای Saman Aether را حذف می‌کند.
- Runner پروژه را حذف می‌کند.
- **Aether اصلی را حذف نمی‌کند.**

---

## 📝 Logها

Log هر Mode در Home ترموکس ذخیره می‌شود:

```text
~/aether-masque.log
~/aether-wg.log
~/aether-gool.log
```

مثلاً برای مشاهده زنده Log حالت GOOL:

```bash
tail -f ~/aether-gool.log
```

برای MASQUE:

```bash
tail -f ~/aether-masque.log
```

برای WireGuard:

```bash
tail -f ~/aether-wg.log
```

---

## 💾 Backup

قبل از جایگزین شدن Shortcutهای قبلی، Installer از فایل‌های موجود Backup می‌گیرد.

Backupها در این مسیر ذخیره می‌شوند:

```text
~/.saman-aether-backups/
```

هر نصب/آپدیت Backup جدیدی با Timestamp خودش می‌سازد.

---

## 🔍 بررسی وضعیت Aether

برای دیدن Process فعال:

```bash
pgrep -a aether
```

برای توقف دستی Aether:

```bash
pkill -x aether
```

در حالت عادی بهتر است از Shortcut زیر استفاده کنید:

```text
0-STOP-Aether
```

---

## 🛠 رفع مشکلات رایج

### Widget خالی است

اگر Termux:Widget را اضافه کرده‌اید ولی Shortcutها دیده نمی‌شوند:

- Widget را Refresh کنید.
- یک بار Termux را باز کنید.
- Widget را حذف و دوباره اضافه کنید.
- مطمئن شوید Termux:Widget از همان خانواده/منبع سازگار با Termux نصب شده است.

---

### Aether اجرا شده ولی اتصال ندارم

ابتدا بررسی کنید Process فعال باشد:

```bash
pgrep -a aether
```

سپس Log Mode موردنظر را ببینید:

```bash
tail -n 100 ~/aether-gool.log
```

یا:

```bash
tail -n 100 ~/aether-wg.log
```

یا:

```bash
tail -n 100 ~/aether-masque.log
```

اگر Aether Connected شده باشد، SOCKS5 باید روی این آدرس در دسترس باشد:

```text
127.0.0.1:1819
```

همچنین تنظیمات برنامه‌ای که از SOCKS5 استفاده می‌کند را بررسی کنید.

---

### Port 1819 درگیر است

ابتدا Aether را متوقف کنید:

```bash
pkill -x aether
```

سپس Mode موردنظر را دوباره از Widget اجرا کنید.

---

### Shortcutها بعد از Update قدیمی هستند

دستور نصب را دوباره اجرا کنید:

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash
```

بعد Termux:Widget را Refresh کنید.

---

## 🔐 نکته امنیتی

قبل از اجرای هر دستور `curl | bash` می‌توانید محتوای Installer را ببینید:

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh
```

سورس Installer و Shortcutها در همین Repository قابل بررسی است.

---

## 📁 ساختار پروژه

```text
saman-aether/
├── README.md
├── install.sh
├── aether-shortcut-runner
└── shortcuts/
    ├── 0-STOP-Aether
    ├── 1-Aether-MASQUE
    ├── 2-Aether-WG
    └── 3-Aether-GOOL
```

---

# 🇬🇧 English

## Overview

**Saman Aether** is a small installer/helper that makes it easier to run the official **Aether** project on Android through **Termux**.

Instead of typing long commands every time, the installer creates ready-to-use **Termux:Widget shortcuts** for starting and stopping Aether modes directly from the Android home screen.

This project does not replace Aether. It uses the official Aether project:

**Official Aether:**  
https://github.com/CluvexStudio/Aether

---

## ✨ Features

- Install or update Aether using the official installer
- Automatically create Termux:Widget shortcuts
- Quick access to:
  - MASQUE
  - WireGuard (WG)
  - GOOL
- One-tap Aether stop shortcut
- Local SOCKS5 proxy on `127.0.0.1:1819`
- Exit IP and country display after connection
- Exit IP change monitoring after reconnects
- Separate log files for each mode
- Backup existing shortcuts before replacement
- Update by simply running the installer again
- Remove Saman Aether shortcuts without removing the official Aether binary

---

## 📱 Requirements

Install these Android apps first:

1. **Termux**
2. **Termux:Widget**

> Installing Termux and Termux:Widget from the same compatible source is recommended to avoid Android package-signature conflicts.

Saman Aether itself does not require a Downloads folder or `termux-setup-storage`.

---

## 🚀 Quick Install

Open Termux and run:

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash
```

The installer will:

1. Install required packages.
2. Download the official Aether installer.
3. Install Aether if it is not already installed.
4. Update Aether if it already exists.
5. Install the Saman Aether widget shortcuts.

When complete, you should see:

```text
INSTALL COMPLETE
```

The following shortcuts will be created:

```text
0-STOP-Aether
1-Aether-MASQUE
2-Aether-WG
3-Aether-GOOL
```

---

## 🧩 Add Termux:Widget

After installation:

1. Return to the Android home screen.
2. Long-press an empty area.
3. Open **Widgets**.
4. Find **Termux:Widget**.
5. Add the widget to your home screen.
6. Refresh it if the shortcuts are not immediately visible.
7. If necessary, open Termux once and return to the home screen.

You should see:

```text
0-STOP-Aether
1-Aether-MASQUE
2-Aether-WG
3-Aether-GOOL
```

---

## ▶️ Shortcut Functions

| Shortcut | Function |
|---|---|
| `0-STOP-Aether` | Stop Aether |
| `1-Aether-MASQUE` | Start Aether in MASQUE mode |
| `2-Aether-WG` | Start Aether in WireGuard mode |
| `3-Aether-GOOL` | Start Aether in GOOL mode |

---

## 🌐 SOCKS5 Proxy

After Aether connects successfully:

```text
Host: 127.0.0.1
Port: 1819
Protocol: SOCKS5
```

Short form:

```text
127.0.0.1:1819
```

Use this address in any application that supports a SOCKS5 proxy.

---

## 🔄 Update

Run the installer again:

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash
```

If Aether is already installed, the installer uses the Aether update flow and refreshes the Saman Aether shortcuts.

---

## 🗑 Remove Saman Aether Shortcuts

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash -s -- uninstall
```

This removes:

- Saman Aether shortcuts
- Saman Aether runner

It **does not remove the official Aether binary**.

---

## 📝 Logs

Logs are stored in the Termux home directory:

```text
~/aether-masque.log
~/aether-wg.log
~/aether-gool.log
```

Example:

```bash
tail -f ~/aether-gool.log
```

---

## 💾 Backups

Existing Saman Aether shortcut files are backed up before replacement.

Backups are stored in:

```text
~/.saman-aether-backups/
```

Each install/update creates a timestamped backup directory.

---

## 🔍 Check Aether Status

Check the running process:

```bash
pgrep -a aether
```

Stop Aether manually:

```bash
pkill -x aether
```

Normally, using the widget shortcut is recommended:

```text
0-STOP-Aether
```

---

## 🛠 Troubleshooting

### Widget is empty

Try the following:

- Refresh Termux:Widget.
- Open Termux once.
- Remove and add the widget again.
- Make sure Termux and Termux:Widget are installed from compatible sources.

### Aether starts but there is no connection

Check the process:

```bash
pgrep -a aether
```

Inspect the relevant log:

```bash
tail -n 100 ~/aether-gool.log
```

or:

```bash
tail -n 100 ~/aether-wg.log
```

or:

```bash
tail -n 100 ~/aether-masque.log
```

When Aether is connected, the local SOCKS5 proxy should be available at:

```text
127.0.0.1:1819
```

Also verify the SOCKS5 configuration in the client application.

### Port 1819 is already in use

Stop Aether:

```bash
pkill -x aether
```

Then start your preferred mode again from the widget.

---

## 🔐 Security Note

Before running any `curl | bash` command, you can inspect the installer:

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh
```

The installer and shortcut source files are available in this repository for review.

---

## 📁 Project Structure

```text
saman-aether/
├── README.md
├── install.sh
├── aether-shortcut-runner
└── shortcuts/
    ├── 0-STOP-Aether
    ├── 1-Aether-MASQUE
    ├── 2-Aether-WG
    └── 3-Aether-GOOL
```

---

## 🔗 Links

**Saman Aether**  
https://github.com/velnox4827/saman-aether

**Official Aether**  
https://github.com/CluvexStudio/Aether

---

## ❤️ Credits

Thanks to:

- Aether developers
- Termux developers
- Termux:Widget developers
- Everyone testing and contributing to this helper

---

## ⭐ Support

If Saman Aether is useful to you, consider starring the repository.

Issues, suggestions, bug reports and contributions are welcome.

# راهنمای استفاده از Saman Tunnel

## 1. نصب

از بخش Releases جدیدترین APK را دانلود و نصب کنید:

https://github.com/velnox4827/saman-aether/releases/latest

نسخه فعلی برای دستگاه‌های `arm64-v8a` ساخته می‌شود و حداقل Android 7 نیاز دارد.

## 2. انتخاب Mode

بعد از باز کردن برنامه یکی از سه گزینه را انتخاب کنید:

### MASQUE

گزینه مناسب برای شروع. MASQUE تلاش می‌کند تونل را شبیه ترافیک معمول وب برقرار کند.

### WireGuard

معمولاً کم‌هزینه‌تر و سریع‌تر است. اگر شبکه اجازه عبور WireGuard را بدهد، انتخاب خوبی است.

### GOOL

دو لایه WireGuard را به‌صورت tunnel-in-tunnel استفاده می‌کند. معمولاً سنگین‌تر است و در بعضی شبکه‌ها ممکن است مرحله Inner Tunnel به Timeout بخورد.

## 3. منتظر Connected بمانید

بعد از انتخاب Mode وضعیت برنامه ابتدا Connecting خواهد شد.

وقتی مشاهده کردید:

```text
Status: Connected — SOCKS5 127.0.0.1:1819
```

Proxy آماده استفاده است.

## 4. تنظیم SOCKS5 در برنامه دیگر

تنظیمات Proxy:

```text
Protocol: SOCKS5
Host: 127.0.0.1
Port: 1819
```

می‌توانید از دکمه **Copy SOCKS5** هم استفاده کنید.

## 5. جلوگیری از Loop

اگر برنامه دیگری روی Android یک VPN سراسری ایجاد می‌کند و قرار است از SOCKS5 سامان تونل استفاده کند:

**Saman Tunnel را در همان VPN App از تونل خارج / Bypass / Exclude کنید.**

در غیر این صورت ممکن است ترافیک Saman Tunnel دوباره وارد همان VPN شود و Loop ایجاد شود.

## 6. STOP

برای بستن هسته و SOCKS5 دکمه **STOP** را بزنید.

## 7. Battery Optimization

برای اینکه Android سرویس را در پس‌زمینه نبندد:

- Settings گوشی
- Apps
- Saman Tunnel
- Battery
- انتخاب `Unrestricted` یا `No restrictions`

اگر یک برنامه VPN/Proxy دیگر همزمان استفاده می‌کنید، برای آن برنامه هم محدودیت باتری را بردارید.

## 8. Diagnostics و گزارش مشکل

اگر اتصال برقرار نشد، قطع شد یا رفتار غیرعادی دیدید:

1. در Saman Tunnel روی **Save TXT** بزنید.
2. فایل `Saman-Tunnel-diagnostics.txt` را ذخیره کنید.
3. فایل را قبل از ارسال عمومی بررسی کنید؛ ممکن است شامل IP، Endpoint و جزئیات اتصال باشد.
4. وارد GitHub Issues شوید:
   https://github.com/velnox4827/saman-aether/issues
5. قالب **Bug report** را انتخاب کنید.
6. نسخه App، نسخه Aether Core، مدل گوشی، نسخه Android، Mode و مراحل ایجاد مشکل را بنویسید.
7. در صورت تمایل فایل Diagnostics را هم ضمیمه کنید.

## 9. پیشنهاد قابلیت

برای پیشنهاد تغییر یا قابلیت جدید:

https://github.com/velnox4827/saman-aether/issues/new?template=feature_request.md

## 10. اگر یک Mode جواب نداد

ترتیب ساده پیشنهادی:

1. MASQUE
2. WireGuard
3. GOOL

عملکرد Modeها به اپراتور، Wi-Fi، وضعیت UDP و شرایط شبکه بستگی دارد؛ ممکن است بهترین گزینه روی دو شبکه متفاوت یکسان نباشد.


## گروه تلگرام

برای گفتگو، آموزش، تست نسخه‌ها و پشتیبانی عمومی:

**Saman Tunnel • Community**

https://t.me/SamanTunnel

برای Bug Report و Feature Request بهتر است از GitHub Issues استفاده شود:
https://github.com/velnox4827/saman-aether/issues

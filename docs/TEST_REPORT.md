# گزارش آزمون همگام‌سازی Aether — 2026-09-14

دستگاه: Android 15 / Termux، معماری arm64

## نتیجهٔ static و regression

- `bash -n` برای installer، runner، کنترلر، Center، library و moduleها: PASS
- ShellCheck با severity warning: PASS
- تمام `termux/tests/test-*.sh`: PASS
- تست‌های پنل/پایداری persistence، capability، آرگومان، Tor، redraw، EOF/Ctrl-C، lifecycle و updater: PASS
- `git diff --check`: PASS

## upstream واقعی

- executable: `$PREFIX/bin/aether`، ELF arm64، بدون کپی در repository
- version: `aether 2.0.0`
- modes: MASQUE H3، MASQUE H2، WireGuard، GOOL/WARP-in-WARP، MASQUE-in-MASQUE
- scan: turbo، balanced، thorough، stealth، ironclad
- Noize: off، light، firewall، balanced، gfw، aggressive
- Tor: در help و markerهای Arti binary تأیید شد؛ Tor-inside، reverse و only در UI و adapter هستند
- Psiphon: در help و strings نصب فعلی یافت نشد؛ پیاده‌سازی یا menu جعلی اضافه نشد

## شبکه

- direct connectivity: HTTP 200 از Cloudflare trace
- MASQUE smoke با پورت‌های موقت: provisioning رسمی identity انجام شد و تا مهلت ۱۲ ثانیه به scan/ready نرسید؛ با timeout متوقف شد و listener موقتی مشاهده نشد. این PASS اتصال نیست و به‌صورت TIMEOUT ثبت می‌شود.
- Tor-only smoke با پورت موقت: bootstrap واقعی Tor به ۴۵٪ رسید و در مهلت ۸ ثانیه کامل نشد؛ با timeout متوقف شد و listener موقتی مشاهده نشد. این PASS اتصال نیست و به‌صورت TIMEOUT ثبت می‌شود.
- GOOL، MIM، WireGuard و Tor خروجی کامل egress را نگرفتند؛ به‌دلیل timeout/هزینهٔ provision و نبودن endpoint/credential اختصاصی، تست عملی آن‌ها BLOCKED است و نتیجه جعل نشده است.

هیچ token، private key، bridge line، account identifier یا IP عمومی در این گزارش ذخیره نشده است.

# ماتریس همگام‌سازی Saman با Aether رسمی

این ماتریس در دستگاه توسعه با `aether 2.0.0` و خروجی واقعی `aether --help` بازبینی شده است. Saman هنگام ورود به پنل همین executable را دوباره شناسایی می‌کند؛ بنابراین این سند توصیف نسخهٔ آزمایش‌شده است، نه pin کردن هسته.

| قابلیت upstream | flag / کلید | جایگاه در Saman | وضعیت آزمایش |
|---|---|---|---|
| MASQUE HTTP/3 | `--masque --h3` | Connection transports / MASQUE Settings | ساخت آرایهٔ آرگومان + تست fixture |
| MASQUE HTTP/2 | `--masque --h2` | Connection transports / MASQUE Settings | ساخت آرایهٔ آرگومان + تست fixture |
| WireGuard | `--wg` | Connection transports / WireGuard Settings | dispatch و تست fixture |
| GOOL / WARP-in-WARP | `--gool`, `--wiw-*`, `--wiw-scan` | Connection transports / GOOL Settings | dispatch و تست fixture |
| MASQUE-in-MASQUE | `--mim`, `--mim-*`, `--mim-scan` | Connection transports / MIM Settings | dispatch و تست fixture |
| Scan | `--scan` | Scan Mode | مقادیر از help زنده |
| Noize | `--noize` | Obfuscation / Noise | مقادیر از help زنده |
| IP family | `-4`, `-6`, `--dual` | Network / IP | ساخت آرگومان |
| SOCKS و HTTP CONNECT | `--bind`, `--http-proxy` | Network / Proxy | پورت‌های محلی و fallback بررسی شد |
| DNS / upstream proxy | `--dns`, `--upstream` | Network / DNS/Proxy | ساخت آرگومان |
| Routing | `--route-block`, `--route-direct`, `--routes` | Network / Routing | ساخت آرگومان |
| Reconnect | `--quick-reconnect`, `--no-quick-reconnect`, `--reconnect-secs` | Network / Advanced | capability-gated |
| MASQUE transport options | `--ech`, `--fragment*`, `--h2-peer`, `--no-quic-v2`, `--startup-secs`, `--validate-secs` | MASQUE Settings | capability-gated |
| WireGuard options | `--keepalive`, `--no-profile-retry`, `--peer`, `--wg-peer` | WireGuard Settings | capability-gated |
| Zero Trust / gateway | `--team`, `--access-*`, `--gateway` | Advanced / Access | capability-gated؛ secrets در خلاصه redacted |
| Identity paths | `--config`, `--wg-config`, `--masque-config` | Advanced / Identity | capability-gated |
| Tor inside | `--tor`, `--tor-bind` | Connection Mode / Tor Settings | در binary با Arti markers تأیید؛ lifecycle live blocked بدون اجرای tunnel |
| Tor reverse | `--tor-reverse`, `--tor-bind` | Tor Routing / Tor Settings | constraint MASQUE H2 تأیید شد |
| Tor only | `--tor-only`, `--bind` | Tor Routing / Tor Settings | argument constraint تأیید شد |
| Tor bridges/PT | `--tor-bridges`, `--no-tor-bridges`, `--tor-bridge`, `--tor-pt`, `--tor-pt-dir` | Tor Settings | validation و redaction تست شد |
| Psiphon | —؛ در help و binary نصب‌شده نشانه‌ای پیدا نشد | نمایش به‌صورت unavailable؛ بدون menu جعلی | تأیید منفی از help/strings |

## قواعد نگهداری

- نبودن flag در help زنده یعنی Saman آن گزینه را به آرگومان تبدیل نمی‌کند.
- نبودن Tor feature باعث می‌شود Tor در پنل unavailable شود؛ صرفاً وجود نام flag در help کافی نیست.
- فایل‌های identity، کلید، token و bridge متعلق به Aether هستند و در repository کپی نمی‌شوند.
- updater Saman فقط مسیر رسمی upstream موجود در installation را به‌روزرسانی می‌کند و پس از آن version/help/capability را دوباره می‌خواند.

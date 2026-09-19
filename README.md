# سامان برای Termux — رابط رسمی Aether

<div dir="rtl">

**Saman** یک رابط و مدیر چرخهٔ اجرا برای نسخهٔ رسمی و بدون‌تغییر [Aether](https://github.com/CluvexStudio/Aether) در Termux است. سامان هستهٔ شبکهٔ جداگانه، fork یا patch اختصاصی Aether نمی‌سازد؛ انتخاب‌ها را به آرگومان‌های پشتیبانی‌شدهٔ همان فایل اجرایی رسمی تبدیل می‌کند، اجرای متعلق به سامان را مدیریت می‌کند و وضعیت و لاگ را نشان می‌دهد.

> نکتهٔ مهم: نسخهٔ Termux یک **پروکسی محلی** می‌سازد، نه VPN سراسری اندروید. فقط برنامه‌ای که صریحاً از SOCKS5/HTTP CONNECT استفاده کند از این مسیر عبور می‌کند. انتخاب Tor نیز به‌تنهایی همهٔ ترافیک گوشی را از Tor عبور نمی‌دهد و تضمین ناشناس‌بودن نیست.

## شروع سریع در Termux

Termux را از [F-Droid](https://f-droid.org/packages/com.termux/) یا [مخزن رسمی Termux](https://github.com/termux/termux-app) نصب کنید؛ نسخهٔ قدیمی Play Store توصیه نمی‌شود.

### ۱) پیش‌نیازها و Aether رسمی

```bash
pkg update
pkg install -y bash curl coreutils jq tar procps grep sed iproute2

curl --proto '=https' --tlsv1.2 -fsSL \
  https://raw.githubusercontent.com/CluvexStudio/Aether/main/aether.sh \
  -o "$TMPDIR/aether.sh"
bash "$TMPDIR/aether.sh" install
aether --version
```

این همان نصب‌کنندهٔ رسمی upstream است؛ معماری گوشی را تشخیص می‌دهد، فایل release مناسب Android را می‌گیرد و checksum منتشرشده را بررسی می‌کند. انتشار رسمی Aether v2.0.0 برای Android/Termux با قابلیت Tor ساخته شده است.

### ۲) نصب یا تعمیر Saman

```bash
curl --proto '=https' --tlsv1.2 -fsSL \
  https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh \
  -o "$TMPDIR/saman-install.sh"
bash "$TMPDIR/saman-install.sh" install
```

### ۳) اجرا

```bash
saman
```

فرمان‌های بررسی اولیه:

```bash
saman status
saman doctor --verbose
saman aether capabilities
saman aether config
```

`termux-setup-storage` برای اجرای Aether یا سامان لازم نیست؛ فقط برای دسترسی اختیاری به فضای اشتراکی Android استفاده می‌شود. Termux:Widget نیز فقط برای میان‌بر صفحهٔ اصلی اختیاری است.

## رفتار منو

- هر صفحه پیش از نمایش دوباره پاک می‌شود؛ صفحه‌های قبلی روی ترمینال انباشته نمی‌شوند.
- همهٔ انتخاب‌های اصلی شماره دارند و با کیبورد Android/Termux کار می‌کنند.
- ورودی خالی یا نامعتبر هیچ گزینه‌ای را اجرا نمی‌کند و به‌عنوان انتخاب پیش‌فرض پذیرفته نمی‌شود.
- گزینهٔ **Back** فقط به صفحهٔ والد برمی‌گردد.
- گزینهٔ **Exit Saman** رابط سامان را می‌بندد؛ اتصال در حال اجرا را متوقف نمی‌کند.
- توقف اتصال فقط با گزینه یا فرمان صریح **Stop** انجام می‌شود.
- علامت `[x]` انتخاب ذخیره‌شده را نشان می‌دهد. «انتخاب ذخیره‌شده» با «حالت واقعاً در حال اجرا» جداگانه نمایش داده می‌شود.
- پیام موفقیت یا خطا تا تأیید کاربر باقی می‌ماند و سپس منو بازطراحی می‌شود.
- خروجی تفصیلی سرویس در لاگ نوشته می‌شود تا خروجی پس‌زمینه منو را خراب نکند.

تنظیمات رابط Aether در این فایل خصوصی ذخیره می‌شود:

```text
~/.config/saman/aether/settings.conf
```

این فایل با whitelist خوانده می‌شود و به‌عنوان کد shell اجرا نمی‌شود. identity، کلیدها و فایل‌های پیکربندی اصلی Aether همچنان متعلق به خود Aether هستند.

## استفادهٔ روزمره

### منوی تعاملی

```bash
saman
# سپس Aether / Connection را انتخاب کنید
```

پنل Aether بخش‌های جدا برای این کارها دارد:

- اتصال/شروع؛
- توقف، راه‌اندازی مجدد و وضعیت؛
- انتخاب transportهای پشتیبانی‌شده؛
- حالت‌ها و تنظیمات Tor؛
- Psiphon اختیاری و جدا از هستهٔ Aether؛
- preset، scan، obfuscation، IP، DNS، proxy و routing؛
- به‌روزرسانی Saman و هستهٔ رسمی Aether؛
- لاگ، راهنما و diagnostics.

فهرست دقیق گزینه‌ها با خروجی زندهٔ `aether --help` محدود می‌شود. اگر نسخهٔ نصب‌شده قابلیتی را نداشته باشد، سامان آن را اجرا نمی‌کند و وضعیت unavailable نشان می‌دهد.

### فرمان‌های مستقیم

```bash
saman aether status
saman aether start h3
saman aether start h2
saman aether start wg
saman aether start gool
saman aether start mim
saman aether restart h3
saman aether stop
saman aether test
saman aether logs
saman aether diagnostics safe
saman aether diagnostics full

saman aether panel
saman aether config
saman aether capabilities
saman aether args
saman aether validate
```

فرمان‌های سازگاری نیز به همان مسیر مرکزی واگذار می‌شوند:

```bash
saman2 aether status
aether-control status
aether-control start h3
```

شروع تکراری نمونهٔ دوم نمی‌سازد. Stop/Restart فقط پردازش و runner متعلق به سامان را پس از بررسی هویت اجرایی هدف می‌گیرد و پردازش‌های نامرتبط را با `pkill` یا الگوی گسترده متوقف نمی‌کند.

## transportها و profileها

Saman فقط گزینه‌هایی را نمایش می‌دهد که هستهٔ رسمی نصب‌شده واقعاً اعلام می‌کند. Aether v2.0.0 این transportها را ارائه می‌کند:

| گزینه | آرگومان upstream | توضیح کوتاه |
|---|---|---|
| MASQUE H3 | `--masque --h3` | HTTP/3/QUIC؛ انتخاب معمول |
| MASQUE H2 | `--masque --h2` | HTTP/2/TCP برای شبکه‌ای که QUIC را می‌بندد |
| WireGuard | `--wg` | WireGuard مستقیم |
| GOOL | `--gool` | WARP-in-WARP |
| MIM | `--mim` | MASQUE-in-MASQUE |

حالت‌های scan رسمی عبارت‌اند از `turbo`، `balanced`، `thorough`، `stealth` و `ironclad`. profileهای Noize رسمی نیز از `aether --help` خوانده می‌شوند. endpoint دستی، allow/block و bypass/direct فقط در صورت پشتیبانی upstream اضافه می‌شوند؛ سامان flag ساختگی تولید نمی‌کند.

پروکسی‌های عادی در حالت غیر-Tor:

```text
SOCKS5        127.0.0.1:1819
HTTP CONNECT  127.0.0.1:1820
```

نمونهٔ آزمون SOCKS با DNS سمت پروکسی:

```bash
curl --socks5-hostname 127.0.0.1:1819 \
  https://www.cloudflare.com/cdn-cgi/trace
```

وجود listener به‌تنهایی موفقیت اینترنت را ثابت نمی‌کند؛ وضعیت READY پس از سیگنال readiness هسته و بررسی endpoint مربوط گزارش می‌شود.

## Tor رسمی Aether

Aether v2.0.0 از [Arti](https://gitlab.torproject.org/tpo/core/arti) استفاده می‌کند و releaseهای رسمی Android آن با feature رسمی `tor` ساخته شده‌اند. سامان این قابلیت را پیاده‌سازی مجدد نمی‌کند و فقط گزینه‌های upstream را در دسترس می‌گذارد.

| حالت در Saman | flag رسمی | مسیر | خروجی |
|---|---|---|---|
| Tor خاموش | بدون flag Tor | شما ←→ WARP | خروجی WARP |
| Tor داخل تونل | `--tor` | شما → WARP → Tor → اینترنت | خروجی Tor |
| تونل داخل Tor | `--tor-reverse` | شما → Tor → WARP → اینترنت | خروجی WARP |
| فقط Tor | `--tor-only` | شما → Tor → اینترنت | خروجی Tor |

نکات مهم:

- `--tor` با transportهای رسمی Aether کار می‌کند. SOCKS روی `1819` خروجی WARP را نگه می‌دارد و SOCKS دوم روی `1820` خروجی Tor است.
- چون `1820` در حالت Tor به SOCKS مخصوص Tor تعلق دارد، سامان هم‌زمان HTTP CONNECT را روی همان پورت فعال نمی‌کند.
- `--tor-reverse` به‌دلیل TCP-only بودن Tor از MASQUE روی HTTP/2 استفاده می‌کند و با WireGuard/GOOL/MIM سازگار نیست.
- `--tor-only` تونل WARP نمی‌سازد و SOCKS Tor را روی bind اصلی (پیش‌فرض `127.0.0.1:1819`) ارائه می‌کند.
- نام میزبان را با `socks5h://` یا `--socks5-hostname` به Tor بدهید؛ حل DNS محلی می‌تواند مقصد را بیرون از Tor افشا کند. Tor در این مسیر UDP حمل نمی‌کند.
- bootstrap نخست ممکن است طولانی باشد. سامان قبل از پیام آماده، منتظر readiness واقعی Tor می‌ماند و شکست bootstrap را در لاگ نشان می‌دهد.

آزمون Tor-inside پس از READY:

```bash
curl --socks5-hostname 127.0.0.1:1820 \
  https://check.torproject.org/api/ip
```

آزمون `tor-only` با bind پیش‌فرض:

```bash
curl --socks5-hostname 127.0.0.1:1819 \
  https://check.torproject.org/api/ip
```

### bridge و pluggable transport

Aether می‌تواند ابتدا Tor مستقیم را امتحان و در صورت مسدودبودن bridge را از BridgeDB بگیرد. در پنل می‌توان رفتار خودکار، اجبار bridge یا غیرفعال‌کردن bridge را فقط با flagهای رسمی انتخاب کرد.

انتشار Android arm64 نسخهٔ v2.0.0 پوشهٔ `pt/` و فایل `pt/lyrebird` را همراه خود دارد. updater سامان فایل اجرایی و پوشهٔ PT همان archive رسمی را با checksum نصب و در شکست rollback می‌کند. transportهای دیگری فقط وقتی استفاده می‌شوند که upstream آن‌ها را در release آینده همراه کند یا فایل اجرایی سازگار از قبل روی دستگاه موجود و صریحاً انتخاب شده باشد.

bridge خصوصی، credential یا token را در issue، لاگ، screenshot یا commit قرار ندهید. سامان مقدار bridge خصوصی را در خلاصهٔ تنظیمات و خروجی آرگومان‌ها نمایش نمی‌دهد. فایل تنظیمات خصوصی را عمومی نکنید.

## وضعیت و لاگ‌ها

```bash
saman aether status
saman logs aether --lines 80
saman aether logs
saman aether diagnostics safe
```

لاگ‌ها براساس حالت جدا و محدود می‌شوند؛ برای Tor نیز لاگ همان اجرای رسمی Aether شامل درصد bootstrap و خطای bridge/PT است. منوی log فقط خروجی ذخیره‌شده را می‌خواند و آن را پاک نمی‌کند.

پیش از اشتراک diagnostics—even در حالت safe—آن را بازبینی کنید. گزارش full ممکن است آدرس‌ها و شناسه‌های بیشتری داشته باشد. کلید خصوصی، bridge خصوصی، credential و state اجرایی نباید commit شوند.

## به‌روزرسانی جداگانه

### به‌روزرسانی Saman بدون تغییر Aether

```bash
curl --proto '=https' --tlsv1.2 -fsSL \
  https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh \
  -o "$TMPDIR/saman-install.sh"
bash "$TMPDIR/saman-install.sh" check
bash "$TMPDIR/saman-install.sh" update
```

نصب‌کننده ابتدا فایل‌های مدیریت‌شدهٔ سامان را در `~/.saman-aether-backups/` پشتیبان می‌گیرد، نسخهٔ جدید را stage و syntax-check می‌کند و در شکست rollback انجام می‌دهد. فایل رسمی `aether`، identity، تنظیمات کاربر و لاگ‌ها حذف نمی‌شوند.

### بررسی یا به‌روزرسانی Aether رسمی

```bash
saman aether update --check
saman aether update
```

این مسیر release پایدار `CluvexStudio/Aether` و asset دقیق معماری Android را می‌گیرد، وجود checksum را اجباری می‌کند، archive را پیش از استخراج بررسی می‌کند، فایل اجرایی و `pt/` را stage و validate می‌کند و سپس جایگزینی اتمیک و قابل‌بازیابی انجام می‌دهد. این عملیات Saman را به‌روزرسانی نمی‌کند.

برای جلوگیری از خراب‌شدن سرویس، هنگام اجرای واقعی update ابتدا اتصال متعلق به سامان را با `saman aether stop` متوقف کنید. هرگز برای به‌روزرسانی از `force-push`، release ناشناس یا binary بدون checksum استفاده نکنید.

### Psiphon رسمی، جدا از Aether

Psiphon قابلیت داخلی `aether 2.0.0` نیست. بررسی source فعلی AetherST نیز هیچ وابستگی یا هستهٔ Psiphon نشان نداد؛ AetherST فقط Aether Core رسمی را اجرا می‌کند. بنابراین سامان Psiphon را به‌درستی به‌عنوان یک integration مستقل از [Psiphon-Labs/psiphon-tunnel-core](https://github.com/Psiphon-Labs/psiphon-tunnel-core) مدیریت می‌کند، نه یک «حالت Aether».

```bash
# وضعیت و بررسی source رسمیِ موجود
saman psiphon status
saman psiphon update --check

# build/update از checkout رسمی تمیزِ ~/psiphon-tunnel-core
saman psiphon update

# پس از دریافت JSON رسمی client (شامل SponsorId و PropagationChannelId)
saman psiphon config /path/to/official-client.json
saman psiphon start
```

Console Client رسمی بدون config/Server Entry معتبر نمی‌تواند به شبکهٔ Psiphon وصل شود؛ سامان credential، server list یا config ساختگی تولید نمی‌کند. برای زنجیرهٔ ترکیبی، **Psiphon از طریق Aether** ساخته می‌شود: config مشتق‌شدهٔ خصوصی با `UpstreamProxyURL=socks5://127.0.0.1:1819` ایجاد می‌شود، بدون تغییر فایل اصلی شما:

```bash
saman psiphon chain
saman psiphon start ~/.config/saman/psiphon/aether-chain.json
```

مسیر ترافیک در این حالت `app → Psiphon local proxy → Aether SOCKS → Internet` است. این زنجیره فقط وقتی قابل‌اجراست که Aether متعلق به سامان روی SOCKS loopback آماده و config رسمی Psiphon فراهم باشد. Psiphon و Aether جداگانه update می‌شوند.

## رفع اشکال

### منو پاک نمی‌شود یا کاراکترها خراب‌اند

```bash
export TERM=xterm-256color
saman
```

در non-TTY یا وقتی `TERM` موجود نیست، سامان escape sequence پاک‌سازی صفحه را چاپ نمی‌کند. اگر کلیدهای جهت کار نکردند از شماره‌ها استفاده کنید. ورودی خالی نباید گزینه‌ای را اجرا کند.

### Aether پیدا نمی‌شود

```bash
command -v aether
aether --version
aether --help
bash "$TMPDIR/aether.sh" install
```

Saman بدون executable رسمی Aether هستهٔ جایگزین یا جعلی نمی‌سازد.

### Tor unavailable است

```bash
saman aether capabilities
aether --version
```

صرف دیده‌شدن `--tor` در help کافی نیست، چون help در build بدون feature نیز می‌تواند موجود باشد. سامان شواهد build واقعی Tor را نیز بررسی می‌کند. از release رسمی Android که با `--features tor` ساخته شده استفاده کنید و سپس `saman aether update` را برای نصب archive رسمی و PT اجرا کنید.

### bootstrap Tor متوقف می‌شود

- لاگ Tor را از منوی Logs ببینید؛ درصد و آخرین خطا را بررسی کنید.
- ساعت Android، DNS و دسترسی شبکه را بررسی کنید.
- bridge را روی automatic یا forced بگذارید؛ bridge خصوصی ساختگی وارد نکنید.
- وجود PT رسمی را بررسی کنید و در صورت نیاز updater رسمی را دوباره اجرا کنید.
- `--tor` معمولاً روی شبکه‌ای که Tor مستقیم را می‌بندد مفیدتر است، چون Tor داخل تونل WARP حمل می‌شود.

### تداخل پورت

```bash
ss -ltn
saman aether status
```

پردازشی که `127.0.0.1:1819` یا `:1820` را گرفته شناسایی و فقط در صورت تعلق به خودتان متوقف کنید. Saman پردازش نامرتبط را برای آزادکردن پورت نمی‌کشد و شروع را با خطای عملی متوقف می‌کند.

### PID مانده یا شروع تکراری

```bash
saman aether status
saman repair --dry-run
```

Saman هویت runner/core متعلق به خودش را بررسی می‌کند. فایل PID نامعتبر نباید باعث توقف پردازش دیگری شود. از `pkill aether` استفاده نکنید.

### اتصال روی Android در پس‌زمینه قطع می‌شود

Battery optimization را برای Termux محدود نکنید و اجازهٔ foreground/background لازم را بدهید. wake lock فقط برای اجرای طولانی اختیاری است. «Force stop» Termux همهٔ پردازش‌های آن را می‌بندد.

## یادداشت تغییرات Termux

این به‌روزرسانی (Saman Termux 1.8.2 / Center 2.1.2):

- منوهای Aether را به جریان تکرارشونده و غیرrecursive با یک مالک ورودی تبدیل می‌کند؛
- صفحهٔ قبلی را در TTY پاک و پیام عملیات را تا تأیید کاربر حفظ می‌کند؛
- انتخاب عددی، ورودی نامعتبر، EOF/Ctrl-C و ترمینال باریک را پوشش می‌دهد؛
- انتخاب ذخیره‌شده را با `[x]` و حالت واقعاً در حال اجرا را جدا نشان می‌دهد؛
- start/stop/restart را به runner و PID متعلق به سامان محدود می‌کند و خروجی پس‌زمینه را به لاگ می‌فرستد؛
- قابلیت‌های رسمی Tor در Aether v2.0.0، تنظیمات bridge/PT، readiness و خطاهای پورت/bootstrap را اضافه می‌کند؛
- updater هستهٔ رسمی را برای binary و `pt/` با checksum، staging، backup، جایگزینی اتمیک و rollback سخت‌گیرانه می‌کند؛
- تست‌های regression منو، persistence، dispatch، lifecycle، Tor و مسیرهای شکست updater را اضافه می‌کند.
- گزینه‌های باقی‌ماندهٔ واقعی upstream مانند gateway، Zero Trust، مسیرهای identity،
  `--no-quick-reconnect` و اسکن GOOL/MIM را فقط در صورت وجود flag زنده به آرایهٔ
  اجرای رسمی اضافه می‌کند. ماتریس کامل در [docs/UPSTREAM_AETHER_MATRIX.md](docs/UPSTREAM_AETHER_MATRIX.md) است.
- Psiphon به‌عنوان adapter جداگانهٔ official Console Client اضافه شده است؛ Aether core
  patch نمی‌شود و config/credential ساختگی تولید نمی‌شود.

## معماری و امنیت

```text
saman / Termux:Widget
  -> ~/.local/share/saman-center-v2/saman2
  -> modules/aether.sh + modules/aether-panel.sh
  -> ~/.aether-shortcut-runner
  -> $PREFIX/bin/aether  (official upstream, unmodified)
  -> loopback proxy endpoints
```

`$PREFIX/bin/saman-aether-core` فقط alias سازگاری به همان executable رسمی است؛ binary کپی‌شده یا patched دیگری نیست. Saman فایل رسمی Aether را fork نمی‌کند و منطق Tor/WARP را دوباره پیاده‌سازی نمی‌کند.

پروکسی‌ها authentication ندارند؛ bind را روی loopback نگه دارید. آن‌ها را بدون firewall و درک ریسک روی `0.0.0.0` منتشر نکنید. کلیدها، tokenها، bridge خصوصی، فایل identity، config شخصی و لاگ runtime را commit نکنید.

## برنامهٔ اندروید

این مخزن برنامهٔ جداگانهٔ Saman Tunnel برای Android را نیز نگه می‌دارد. انتشار پایدار فعلی **v1.8.0** است و APKهای آن در [Releases](https://github.com/velnox4827/saman-aether/releases/tag/v1.8.0) قرار دارند:

- هستهٔ رسمی و بدون‌تغییر **Aether v2.0.0** با feature رسمی Tor در build گنجانده شده است.
- حالت‌های MASQUE H3/H2، WireGuard، GOOL، MASQUE-in-MASQUE، Tor-only، transport→Tor و Tor→MASQUE H2 در رابط برنامه موجودند.
- Psiphon در برنامهٔ اندروید فعال نیست؛ سرویس رسمی Psiphon به پیکربندی و مجوز توزیع اختصاصی Psiphon نیاز دارد و نمی‌توان آن را به‌عنوان حالت هستهٔ Aether نمایش داد.
- برای انتخاب APK مناسب و بررسی امضا به [ANDROID_APP_README.md](ANDROID_APP_README.md) مراجعه کنید.

</div>

---

<a id="english"></a>
## English (short reference)

Saman for Termux is a menu, configuration, logging, update, and lifecycle wrapper around the unmodified official [Aether](https://github.com/CluvexStudio/Aether) executable. Termux is the primary workflow documented here; the separate Android application is documented in [ANDROID_APP_README.md](ANDROID_APP_README.md) and its current stable release is [Saman Tunnel v1.8.0](https://github.com/velnox4827/saman-aether/releases/tag/v1.8.0), built directly from unmodified official Aether v2.0.0 with Tor enabled.

Quick start:

```bash
pkg update
pkg install -y bash curl coreutils jq tar procps grep sed iproute2
curl -fsSL https://raw.githubusercontent.com/CluvexStudio/Aether/main/aether.sh -o "$TMPDIR/aether.sh"
bash "$TMPDIR/aether.sh" install
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh -o "$TMPDIR/saman-install.sh"
bash "$TMPDIR/saman-install.sh" install
saman
```

Core commands:

```bash
saman status
saman doctor --verbose
saman aether status
saman aether panel
saman aether stop
saman aether update --check
saman aether update
```

The normal endpoints are SOCKS5 `127.0.0.1:1819` and HTTP CONNECT `127.0.0.1:1820`. Tor support comes only from official Tor-enabled Aether builds:

- `--tor`: WARP SOCKS on 1819 and Tor SOCKS on 1820;
- `--tor-reverse`: MASQUE/H2 through Tor, final WARP SOCKS on 1819;
- `--tor-only`: plain Tor SOCKS on the configured main bind (1819 by default).

Use `socks5h`/proxy-side hostname resolution for Tor. Tor carries TCP, not UDP. Selecting Tor does not route all Android traffic and is not an anonymity guarantee. Saman never kills unrelated processes, never patches the upstream core, and keeps detailed service output in bounded logs.

### Psiphon (separate official integration)

Psiphon is not an Aether v2.0.0 mode, and the inspected AetherST source contains no Psiphon core. Saman therefore controls the official [Psiphon Tunnel Core Console Client](https://github.com/Psiphon-Labs/psiphon-tunnel-core) separately:

```bash
saman psiphon status
saman psiphon update --check
saman psiphon update
saman psiphon config /path/to/official-client.json
saman psiphon start
```

The official client needs a real client JSON with `SponsorId` and `PropagationChannelId`; Saman never invents a server list or credentials. `saman psiphon chain` derives a private config with Aether's loopback SOCKS endpoint as Psiphon's upstream, leaving the original JSON untouched. The resulting path is `app → Psiphon → Aether → Internet`. Psiphon updates and Aether updates remain independent.

License: [GNU AGPL-3.0](LICENSE).

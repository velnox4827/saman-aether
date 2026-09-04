# Saman Tunnel

<p align="center">
  <img src="android-app/app/src/main/res/drawable-nodpi/saman_tunnel_logo.png" width="150" alt="Saman Tunnel logo">
</p>

<p align="center"><strong>Stable Android local-tunnel frontend and centralized Saman/Aether toolkit for Termux</strong></p>

**Language:** [English](#english) | [فارسی](#فارسی)

**Current stable Android release:** [Saman Tunnel v1.5.0](https://github.com/velnox4827/saman-aether/releases/tag/v1.5.0) · **Aether Core:** v1.8.0 at `a916ff6fbbb4ebafe8314c53cf3718eb51dcae53` · **License:** [GNU AGPL-3.0](LICENSE)

> Saman Tunnel is an independent derivative project, not the official Aether application. It provides local proxies; it is not an Android `VpnService` and does not create a device-wide VPN by itself.

---

<a id="english"></a>
## English

### Overview

Saman Tunnel runs a patched, pinned Aether Core inside an isolated Android application process and exposes local proxies to compatible applications. The same repository also maintains the current Termux integration: Saman Center is the central control surface for Aether and the wider Saman toolset.

Main use cases:

- run MASQUE, WireGuard, or GOOL through local SOCKS5/HTTP CONNECT endpoints;
- exclude only Saman Tunnel from an outer Android VPN when that VPN supports per-app bypass;
- control the patched Aether Core from one canonical `saman` command in Termux;
- inspect connection phases, health, bounded logs, and sanitized diagnostics;
- retain compatible entry points while avoiding duplicate PID, port, and process logic.

### Features and architecture

#### Android application

- Package: `com.saman.tunnel`; minimum Android 7 / API 24, target API 35, compile SDK 36. This includes Android 15 compatibility.
- `MainActivity` is the UI; `AetherService` is a non-exported foreground service in the isolated `:aether_core` process; JNI (`libsamanbridge.so`) starts the embedded Rust `libaether.so`.
- The service uses the Android 14+ `specialUse` foreground-service type and posts an ongoing notification. Notification permission should be granted on recent Android versions.
- Start flow: validate the selected mode, enter `STARTING`, wait up to two seconds for ports `1819`/`1820` to be handed off, launch the native job, then report live connection phases while waiting up to 120 seconds for a protocol-valid SOCKS5 greeting.
- Runtime states are `STOPPED`, `STARTING`, `CONNECTING`, `CONNECTED`, `DEGRADED`, `STOPPING`, and `FAILED`. Health checks run every two seconds; three consecutive SOCKS5 failures mark the connection unstable, and recovery returns it to connected.
- Stop flow: claim and cancel the native job, publish `STOPPING` and then `STOPPED`, remove the foreground notification, stop the service, and terminate the isolated core process. A mode switch waits for confirmed shutdown before starting the new mode.
- Active starts return `START_REDELIVER_INTENT`; duplicate starts are ignored. Service destruction cancels the current native job, keeps a meaningful failure state when applicable, and otherwise records a stopped state. The widget and UI share the same service and persisted state—no second core is created.
- Smart Reconnect is implemented in the patched Aether Core. Cached WG, MASQUE H3/H2, and GOOL paths are validated; unsuitable or short-lived paths trigger a fresh balanced scan.
- The app is a local-proxy frontend, not a `VpnService`. Configure a SOCKS5/HTTP-aware client, or exclude Saman Tunnel in an outer VPN that supports app bypass. Never bind the local proxies to a public interface.

#### Logging, diagnostics, security, and updates

- App logs are bounded at 1 MiB and core logs at 2 MiB, with one previous core log. Safe diagnostics keep bounded recent sessions; both Safe and Full reports run the diagnostics sanitizer.
- Diagnostics redact authorization headers, cookies, tokens, passwords, API keys, private keys, credential-bearing URLs, selected device identifiers, and IPv6 identifiers. Review every report before sharing it.
- Android backup is disabled (`android:allowBackup="false"`) and cleartext traffic is disabled (`android:usesCleartextTraffic="false"`). The service and state receiver are not exported.
- The update check is manual. It accepts stable Android tags such as `v1.5.0`, rejects the Termux release stream and foreign hosts, and opens only trusted GitHub Releases URLs. It does not silently install an APK.

#### Aether Core and supported modes

Aether Core v1.8.0 is pinned to commit `a916ff6fbbb4ebafe8314c53cf3718eb51dcae53`. The build applies the reviewed Smart Reconnect and reusable-local-listener patches before compilation.

| Mode | Current invocation behavior |
|---|---|
| MASQUE H3 | `--masque`, IPv4, balanced scan, firewall noize, quick reconnect |
| MASQUE H2 | `--masque --h2` where supported, with the same H3 scan/reconnect policy |
| WireGuard | `--wg`, IPv4, balanced scan/noize, keepalive 5, quick reconnect |
| GOOL | `--gool`, IPv4, balanced scan/noize, keepalive 5, quick reconnect |

All modes use `--reconnect-secs 1` and expose:

```text
SOCKS5       127.0.0.1:1819
HTTP CONNECT 127.0.0.1:1820
```

Smart Reconnect cache thresholds are 650 ms for WG RTT, 1800 ms for MASQUE H3 verification, 2500 ms for MASQUE H2 verification, and 20 seconds for short-lived paths/pairs. These limits validate cached routes; fresh scans remain `balanced`.

#### Current Termux/Saman architecture

The canonical path is centralized:

```text
Termux:Widget Saman-Center
        -> ~/bin/saman
        -> ~/.local/share/saman-center-v2/saman2
        -> modules/aether.sh
        -> ~/.aether-shortcut-runner
        -> $PREFIX/bin/saman-aether-core
        -> 127.0.0.1:1819 / 127.0.0.1:1820
```

- `saman` is the canonical command.
- `saman2` remains an upgrade-compatible alias to the same Saman Center implementation.
- `aether-control` is a lightweight compatibility wrapper: it validates arguments and delegates to `saman aether ...`.
- `saman-aether-core` is the patched core. A separately installed upstream `$PREFIX/bin/aether` is retained and is not overwritten.
- `saman-aether-diagnostics` generates bounded, sanitized runtime reports.
- `.aether-shortcut-runner` owns the mode-specific Aether runtime arguments, readiness wait, smart reconnect environment, wake lock, log rotation, and isolated process lifecycle.
- Saman Center also routes optional Saman modules such as SDM/download, media, sharing, phone, backup, and diagnostics. Missing optional tools do not change the canonical Aether path.
- The installer creates centralized `Saman-Center`/`Saman-Center-v2` shortcuts. It does **not** recreate obsolete independent `0-Aether-*` through `3-Aether-*` shortcuts. Existing legacy copies are left untouched for backward compatibility.

### Android installation

1. Download only from the [v1.5.0 GitHub release](https://github.com/velnox4827/saman-aether/releases/tag/v1.5.0).
2. Download the matching APK and `SHA256SUMS`; verify the checksum before installation.
3. Allow your browser or file manager to install unknown apps only for this installation, then revoke that permission if it is no longer needed.
4. Install the APK, open Saman Tunnel, grant notification permission, choose a mode, and wait for `Connected`.
5. Configure the client application to use `127.0.0.1:1819` (SOCKS5) or `127.0.0.1:1820` (HTTP CONNECT).

For Android 15 and vendor firmware, allow foreground/background operation and set battery use to unrestricted if the service is stopped while the screen is off. Do not use “Force stop”; Android prevents background restart after a force stop until the app is opened again.

#### APK variants

- `Saman-Tunnel-v1.5.0-arm64-v8a.apk` — recommended for almost all current 64-bit ARM phones and tablets; smallest suitable package.
- `Saman-Tunnel-v1.5.0-armeabi-v7a.apk` — for older 32-bit ARM devices only.
- `Saman-Tunnel-v1.5.0-universal-arm.apk` — contains both ARM ABIs; choose it when the device ABI is unknown or one file must support both generations. It is larger.

The Android app does not publish an x86/x86_64 APK.

#### Upgrade from an older Android version

- Do not uninstall first if you want an in-place upgrade.
- Verify the new APK, then install it over the existing `com.saman.tunnel` package.
- Android accepts the update only when the package name and signing identity match. A “package conflicts” or “app not installed” error can indicate a different signer; back up any non-secret settings you need before uninstalling a mismatched build.
- Stop an active tunnel before upgrading. After the update, open the app once and verify the displayed version and local-proxy health.

### Termux installation and upgrade

Prerequisites:

- a current Termux build from F-Droid or the official Termux GitHub project—not the obsolete Play Store build;
- network access to `api.github.com`, `github.com`, and `raw.githubusercontent.com`;
- Termux:Widget for the optional home-screen shortcut; Termux:API is optional for battery/notification integrations;
- `termux-setup-storage` only for shared-storage exports. Core operation does not require shared storage.

Install or repair idempotently:

```bash
curl --proto '=https' --tlsv1.2 -fsSL \
  https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh \
  -o "$TMPDIR/saman-install.sh"
bash "$TMPDIR/saman-install.sh" install
```

Check available versions or upgrade:

```bash
bash "$TMPDIR/saman-install.sh" check
bash "$TMPDIR/saman-install.sh" update
```

The installer detects `arm64`, `armv7`, or `x86_64`; queries non-draft/non-prerelease GitHub releases; selects the exact architecture archive; requires its `.sha256` asset; fails closed on mismatch; stages source and core before changing live files; creates a private timestamped backup under `~/.saman-aether-backups/`; and rolls back if installation or verification fails. User configuration, logs, upstream `aether`, and unrelated shortcuts are preserved.

After installation, refresh Termux:Widget. On Android 15, set Termux battery use to unrestricted for long sessions and allow notifications/wake-lock behavior as needed. Android may kill Termux under memory or battery pressure; Saman detects stale PID files and a single orphaned canonical core, while explicit start cleans canonical orphan processes before binding ports.

### Usage

```bash
saman status
saman doctor --verbose
saman repair --dry-run
saman logs aether --lines 80
saman config show
saman config validate
saman config set NETWORK_TIMEOUT 8
saman refresh
saman backup
saman share
saman aether status
saman aether start h3
saman aether start h2
saman aether start wg
saman aether start gool
saman aether restart wg
saman aether stop
saman aether test
saman aether diagnostics safe
saman aether diagnostics full
```

Saman Center 2.1 loads only the module required by the selected command, so
`version` and `help` remain read-only and fast. `status` takes one process and
socket snapshot; `doctor` distinguishes required failures from optional-tool
warnings; `repair --dry-run` previews only conservative runtime cleanup; and
`logs` is noninteractive and line-bounded.

Numeric settings live in `~/.config/saman-center-v2/settings.conf`. The file is
whitelist-parsed and is never sourced as shell code. Use `saman config show` to
see effective values, `saman config validate` to report invalid values or port
collisions, and `saman config set KEY VALUE` for an atomic update. Supported
keys cover the Aether/Share ports, Share size/free-space/connection limits,
network and Termux API deadlines, dashboard cache TTL, bounded log sizes,
Aether readiness/port hand-off/log-guard timing, and Smart Reconnect thresholds.
Unknown custom lines are preserved but never evaluated.

Compatibility commands remain valid:

```bash
saman2 aether status
aether-control start h3
aether-control status
aether-control diagnostics safe
```

Only one canonical `saman-aether-core` instance should run. Mode switching stops exact core executable PIDs, waits up to five seconds, force-stops only a still-matching canonical PID if necessary, checks ports, and starts the requested mode. Current per-mode logs are limited to 5 MiB; the previous log is limited to 1 MiB.

### Backup and restore policy

- Android application data is intentionally excluded from Android backup.
- Every Termux install/update/uninstall first snapshots managed files privately under `~/.saman-aether-backups/<timestamp>/` with restrictive permissions. Automatic rollback restores the exact managed targets on failure.
- `saman backup` creates a source/script snapshot and deliberately excludes cookies, SSH keys, private keys, personal downloads, and private configuration.
- Do not move installer backups containing local scripts to public/shared storage without reviewing them. Restore manually only from a trusted timestamped backup while Aether is stopped.

### Verification

Verify all release APKs from the directory containing the downloaded files:

```bash
sha256sum -c SHA256SUMS
```

Expected v1.5.0 hashes:

```text
a618e3c9f356d8165afec7a33b237caadc894eb8fc818f85abef762ea7f7b74b  Saman-Tunnel-v1.5.0-arm64-v8a.apk
16282ebbc1e824dcc2b3e4b0f1722f7d0038989f83812bab067c76a970156512  Saman-Tunnel-v1.5.0-armeabi-v7a.apk
9a82aaafa4e9af7a9b3f4e2e25a3cec81cad2e5198068750da0c16c140d4843e  Saman-Tunnel-v1.5.0-universal-arm.apk
```

With Android Build Tools installed, verify signing continuity:

```bash
apksigner verify --verbose --print-certs Saman-Tunnel-v1.5.0-arm64-v8a.apk
```

Expected signer certificate SHA-256:

```text
03233edadf89ed27c7cf80452916a7bd25e8c74abe0f12d2eefcb8b290b48805
```

A checksum proves file integrity; the certificate fingerprint proves continuity with the Saman Tunnel release identity. Verify both, and use only HTTPS GitHub release URLs.

### Build from source

The verified v1.5.0 build stack is:

- Aether Core v1.8.0 commit `a916ff6fbbb4ebafe8314c53cf3718eb51dcae53`;
- Rust `1.89.0` and cargo-ndk `4.1.2`;
- Android NDK `26.3.11579264`, CMake `3.22.1`, platform 36, Build Tools `35.0.0`;
- Java/Temurin 17, Gradle `8.13`, Android Gradle Plugin `8.13.2`, Kotlin `2.3.21`.

Reproduce the workflow rather than building against an arbitrary Aether checkout:

```bash
git clone https://github.com/velnox4827/saman-aether.git
cd saman-aether
git switch --detach v1.5.0
# Follow .github/workflows/android-apk.yml to fetch the pinned Aether commit,
# apply both patches, and build libaether.so for arm64-v8a and armeabi-v7a.
cd android-app
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

A release build requires the existing private signing identity through the four `SAMAN_*` Gradle properties. Never place keystores or passwords in the repository or command history. Unsigned/local builds cannot update an installed official APK.

For the patched Termux executable, `.github/workflows/termux-core.yml` builds Android targets with cargo-ndk and packages the executable as `saman-aether-core` for arm64, armv7, and x86_64.

### CI and GitHub Actions

- Work is developed on `main`, `release/**`, and focused maintenance branches; immutable public tags identify releases.
- `android-apk.yml` runs Android unit tests and lint, builds three debug APK variants, and on manual dispatch restores the signing key from GitHub Secrets, builds release APKs, verifies package/version/native libraries, checks the expected certificate, and uploads APKs plus `SHA256SUMS` as workflow artifacts.
- `release.yml` responds to `v*` tags and creates public Android releases. Existing tags and v1.5.0 artifacts must never be moved or replaced.
- `termux-core.yml` builds patched Termux cores for three architectures and publishes non-latest `termux-v*` releases with per-archive SHA-256 files.
- `termux-maintenance.yml` runs syntax/routing/installer policy tests and ShellCheck for the canonical Termux implementation.
- Signing material exists only in GitHub Secrets during signed workflow jobs. Pull requests and normal pushes produce no official signed release.

### Security

Never expose, log, upload, or commit signing keystores, signing passwords, private keys, API/OAuth keys, Telegram bot tokens, GitHub tokens, cookies, `rpc.secret`, credentials, or private configuration. Keep signing files in private application storage; do not copy them to shared storage. Before committing, inspect `git status`, staged diffs, untracked files, and workflow changes. Diagnostics are sanitized but must still be reviewed before sharing.

The local proxies listen only on loopback. Do not change them to `0.0.0.0` unless you understand and secure the exposure. Download updates only from this repository’s HTTPS Releases page, verify SHA-256 and signer continuity, and never run an installer copied from an untrusted mirror.

### Troubleshooting

- **Aether does not start:** run `saman doctor --verbose`, then `saman aether status`. Confirm `$PREFIX/bin/saman-aether-core` and `~/.aether-shortcut-runner` are executable and GitHub dependencies are reachable.
- **Stale PID or orphan:** `saman aether status` validates the PID against the exact patched core executable and can recover one orphan. `saman aether stop` cleans matching canonical core processes; it does not kill arbitrary processes from an untrusted PID file.
- **Port conflict:** stop other proxies using `127.0.0.1:1819` or `:1820`; inspect with `ss -ltnp`. Saman waits for port hand-off and fails safely rather than starting a duplicate listener.
- **Android service/VPN issue:** remember that Saman Tunnel is not a `VpnService`. Grant notifications, allow foreground/background execution, remove restrictive battery policy, and configure the outer VPN’s per-app bypass correctly.
- **Termux is killed:** install a current Termux build, disable battery optimization for Termux, allow background activity, and use a wake lock for long Aether sessions. Open Termux again and check status before restarting.
- **Update/download failure:** check DNS/TLS and connectivity to `api.github.com`, `github.com`, and `raw.githubusercontent.com`; retry `install.sh check`. The installer refuses missing or mismatched checksums.
- **APK will not install/update:** verify the ABI, free space, package name, checksum, and signer. A signing mismatch requires removing the differently signed package; official signing material must not be replaced.
- **GitHub/API connectivity:** try the direct [Releases page](https://github.com/velnox4827/saman-aether/releases) in a browser. Proxy GitHub through the local SOCKS endpoint only after Aether is healthy.
- **Logs and diagnostics:** use `saman aether logs`, inspect `~/aether-<mode>.log`, or generate `saman aether diagnostics safe`. Use `full` only when more retained history is necessary; it remains sanitized but is larger.

### Project structure

```text
.github/workflows/                 Android, release, Termux core, maintenance CI
android-app/                       Android UI/service/JNI project
  aether-patches/                  Smart Reconnect and listener patches
  app/src/main/java/com/saman/tunnel/
termux/saman-center-v2/            Canonical modular Saman Center implementation
termux/aether-control              Compatibility delegator
termux/tests/                      Termux shell and routing tests
install.sh                         Idempotent Termux install/update/rollback
.aether-shortcut-runner (installed) <- repository: aether-shortcut-runner
saman-aether-diagnostics           Sanitized Termux diagnostics
shortcuts/                         Retained legacy compatibility sources; not newly installed
CHANGELOG.md                       Release and maintenance history
```

### Releases and changelog

- [Saman Tunnel v1.5.0](https://github.com/velnox4827/saman-aether/releases/tag/v1.5.0)
- [CHANGELOG.md](CHANGELOG.md)
- [All GitHub Releases](https://github.com/velnox4827/saman-aether/releases)
- [Upstream Aether](https://github.com/CluvexStudio/Aether)
- [Issues](https://github.com/velnox4827/saman-aether/issues)

---

<a id="فارسی"></a>
## فارسی

<div dir="rtl">

### معرفی

Saman Tunnel یک نسخهٔ پچ‌شده و ثابت از Aether Core را در یک پردازش ایزولهٔ برنامهٔ اندروید اجرا می‌کند و پروکسی‌های محلی را در اختیار برنامه‌های سازگار می‌گذارد. همین مخزن، یکپارچه‌سازی فعلی Termux را نیز نگه‌داری می‌کند: Saman Center سطح کنترل مرکزی Aether و مجموعه‌ابزار Saman است.

کاربردهای اصلی:

- اجرای MASQUE، WireGuard یا GOOL از طریق درگاه‌های محلی SOCKS5 و HTTP CONNECT؛
- خارج‌کردن فقط Saman Tunnel از VPN اصلی اندروید، در صورتی که آن VPN قابلیت bypass بر اساس برنامه را داشته باشد؛
- کنترل Aether Core پچ‌شده از طریق یک فرمان مرجع و مرکزی به نام `saman` در Termux؛
- مشاهدهٔ مراحل اتصال، سلامت، لاگ‌های محدودشده و گزارش‌های عیب‌یابی پاک‌سازی‌شده؛
- حفظ ورودی‌های سازگار قدیمی، بدون تکرار منطق PID، پورت و پردازش.

### امکانات و معماری

#### برنامهٔ اندروید

- نام بسته `com.saman.tunnel` است؛ حداقل نسخه Android 7 / API 24، target API 35 و compile SDK 36 است. بنابراین Android 15 پشتیبانی می‌شود.
- `MainActivity` رابط کاربری است؛ `AetherService` یک سرویس foreground غیرقابل‌دسترسی از بیرون است که در پردازش ایزولهٔ `:aether_core` اجرا می‌شود؛ JNI با کتابخانهٔ `libsamanbridge.so`، هستهٔ Rust یعنی `libaether.so` را راه‌اندازی می‌کند.
- سرویس در Android 14 و بالاتر از نوع foreground service با `specialUse` استفاده می‌کند و اعلان دائمی نمایش می‌دهد. در نسخه‌های جدید اندروید باید مجوز اعلان داده شود.
- روند شروع: حالت انتخاب‌شده اعتبارسنجی می‌شود، وضعیت وارد `STARTING` می‌شود، تا دو ثانیه برای آزادشدن پورت‌های `1819` و `1820` صبر می‌شود، job بومی اجرا می‌شود و تا سقف ۱۲۰ ثانیه، مراحل زندهٔ اتصال تا دریافت پاسخ معتبر پروتکل SOCKS5 گزارش می‌شوند.
- وضعیت‌های اجرایی عبارت‌اند از `STOPPED`، `STARTING`، `CONNECTING`، `CONNECTED`، `DEGRADED`، `STOPPING` و `FAILED`. بررسی سلامت هر دو ثانیه انجام می‌شود؛ سه خطای متوالی SOCKS5 اتصال را ناپایدار اعلام می‌کند و پس از بازیابی، وضعیت دوباره متصل می‌شود.
- روند توقف: job بومی گرفته و لغو می‌شود، ابتدا `STOPPING` و سپس `STOPPED` ثبت می‌شود، اعلان foreground حذف می‌گردد، سرویس متوقف و پردازش ایزولهٔ هسته پایان داده می‌شود. هنگام تغییر حالت، برنامه تا توقف تأییدشده صبر می‌کند و بعد حالت جدید را اجرا می‌کند.
- شروع فعال از `START_REDELIVER_INTENT` استفاده می‌کند و درخواست شروع تکراری نادیده گرفته می‌شود. هنگام ازبین‌رفتن سرویس، job بومی جاری لغو می‌شود؛ وضعیت خطای معنادار در صورت لزوم حفظ می‌شود و در غیر این صورت وضعیت توقف ثبت می‌گردد. ویجت و رابط اصلی همان سرویس و state ذخیره‌شده را کنترل می‌کنند و هستهٔ دومی ساخته نمی‌شود.
- Smart Reconnect در Aether Core پچ‌شده پیاده‌سازی شده است. مسیرهای کش‌شدهٔ WG، MASQUE H3/H2 و GOOL بررسی می‌شوند و مسیر نامناسب یا کوتاه‌عمر باعث اسکن متوازن تازه می‌شود.
- برنامه یک رابط پروکسی محلی است، نه `VpnService`. یک برنامهٔ سازگار با SOCKS5/HTTP تنظیم کنید یا در VPN اصلی، Saman Tunnel را در فهرست bypass قرار دهید. پروکسی محلی را هرگز روی رابط عمومی bind نکنید.

#### لاگ، عیب‌یابی، امنیت و به‌روزرسانی

- لاگ برنامه به ۱ MiB و لاگ هسته به ۲ MiB محدود است و یک لاگ قبلی هسته نگه‌داری می‌شود. گزارش Safe فقط sessionهای اخیر و محدود را نگه می‌دارد؛ هر دو گزارش Safe و Full از sanitizer عبور می‌کنند.
- گزارش‌های عیب‌یابی، هدرهای authorization، کوکی‌ها، توکن‌ها، گذرواژه‌ها، API keyها، کلیدهای خصوصی، URLهای دارای اعتبارنامه، برخی شناسه‌های دستگاه و شناسه‌های IPv6 را حذف یا پنهان می‌کنند. پیش از اشتراک‌گذاری هر گزارش، آن را بازبینی کنید.
- پشتیبان‌گیری اندروید غیرفعال است (`android:allowBackup="false"`) و ترافیک cleartext نیز غیرفعال است (`android:usesCleartextTraffic="false"`). سرویس و گیرندهٔ state از بیرون export نشده‌اند.
- بررسی آپدیت دستی است. فقط tagهای پایدار اندروید مانند `v1.5.0` پذیرفته می‌شوند؛ جریان انتشار Termux و میزبان‌های دیگر رد می‌شوند و فقط صفحهٔ قابل‌اعتماد GitHub Releases باز می‌شود. APK به‌صورت مخفی یا خودکار نصب نمی‌شود.

#### Aether Core و حالت‌های پشتیبانی‌شده

Aether Core v1.8.0 روی commit زیر ثابت شده است: `a916ff6fbbb4ebafe8314c53cf3718eb51dcae53`. فرایند build پیش از کامپایل، پچ‌های بازبینی‌شدهٔ Smart Reconnect و listener محلی قابل‌استفادهٔ مجدد را اعمال می‌کند.

| حالت | رفتار فعلی هنگام اجرا |
|---|---|
| MASQUE H3 | `--masque`، IPv4، اسکن balanced، noize از نوع firewall و quick reconnect |
| MASQUE H2 | در صورت پشتیبانی `--masque --h2`، با همان سیاست اسکن و reconnect حالت H3 |
| WireGuard | `--wg`، IPv4، اسکن/noize متوازن، keepalive برابر ۵ و quick reconnect |
| GOOL | `--gool`، IPv4، اسکن/noize متوازن، keepalive برابر ۵ و quick reconnect |

همهٔ حالت‌ها از `--reconnect-secs 1` استفاده می‌کنند و این درگاه‌ها را ارائه می‌دهند:

```text
SOCKS5       127.0.0.1:1819
HTTP CONNECT 127.0.0.1:1820
```

حدهای کش Smart Reconnect عبارت‌اند از ۶۵۰ ms برای RTT در WG، مقدار ۱۸۰۰ ms برای بررسی MASQUE H3، مقدار ۲۵۰۰ ms برای MASQUE H2 و ۲۰ ثانیه برای مسیر یا جفت کوتاه‌عمر. این حدود فقط برای اعتبارسنجی مسیرهای کش‌شده‌اند و اسکن تازه همچنان `balanced` باقی می‌ماند.

#### معماری فعلی Termux/Saman

مسیر مرجع و مرکزی به شکل زیر است:

```text
Termux:Widget Saman-Center
        -> ~/bin/saman
        -> ~/.local/share/saman-center-v2/saman2
        -> modules/aether.sh
        -> ~/.aether-shortcut-runner
        -> $PREFIX/bin/saman-aether-core
        -> 127.0.0.1:1819 / 127.0.0.1:1820
```

- `saman` فرمان مرجع است.
- `saman2` برای سازگاری هنگام ارتقا باقی مانده و به همان Saman Center اشاره می‌کند.
- `aether-control` یک wrapper سبک سازگاری است؛ آرگومان‌ها را بررسی می‌کند و به `saman aether ...` واگذار می‌نماید.
- `saman-aether-core` هستهٔ پچ‌شده است. اگر `$PREFIX/bin/aether` رسمی جداگانه نصب شده باشد، حفظ می‌شود و overwrite نخواهد شد.
- `saman-aether-diagnostics` گزارش اجرایی محدود و پاک‌سازی‌شده تولید می‌کند.
- `.aether-shortcut-runner` مالک آرگومان‌های هر حالت، انتظار برای readiness، متغیرهای Smart Reconnect، wake lock، چرخش لاگ و چرخهٔ پردازش ایزولهٔ Aether است.
- Saman Center ماژول‌های اختیاری Saman مانند SDM/download، رسانه، اشتراک‌گذاری، تلفن، پشتیبان و عیب‌یابی را نیز مسیریابی می‌کند. نبود ابزار اختیاری، مسیر مرجع Aether را تغییر نمی‌دهد.
- نصب‌کننده shortcutهای مرکزی `Saman-Center` و `Saman-Center-v2` را می‌سازد و shortcutهای مستقل و منسوخ `0-Aether-*` تا `3-Aether-*` را **دوباره ایجاد نمی‌کند**. نسخه‌های قدیمی موجود برای سازگاری حذف نمی‌شوند.

### نصب در اندروید

۱. فقط از [صفحهٔ انتشار v1.5.0 در GitHub](https://github.com/velnox4827/saman-aether/releases/tag/v1.5.0) دانلود کنید.
۲. APK مناسب و فایل `SHA256SUMS` را بگیرید و پیش از نصب checksum را بررسی کنید.
۳. فقط برای همین نصب، اجازهٔ نصب از منبع ناشناس را به مرورگر یا فایل‌منیجر بدهید و اگر دیگر لازم نیست، بعداً آن را لغو کنید.
۴. APK را نصب و Saman Tunnel را باز کنید؛ مجوز اعلان را بدهید، حالت را انتخاب کنید و تا نمایش `Connected` صبر کنید.
۵. برنامهٔ مصرف‌کننده را روی `127.0.0.1:1819` برای SOCKS5 یا `127.0.0.1:1820` برای HTTP CONNECT تنظیم کنید.

در Android 15 و رابط‌های سفارشی سازندگان، اگر سرویس با خاموش‌شدن صفحه متوقف می‌شود، اجرای foreground/background را مجاز و مصرف باتری را روی unrestricted قرار دهید. از «Force stop» استفاده نکنید؛ پس از Force stop، اندروید تا زمانی که برنامه دوباره باز نشود اجازهٔ راه‌اندازی پس‌زمینه نمی‌دهد.

#### گونه‌های APK

- `Saman-Tunnel-v1.5.0-arm64-v8a.apk` — پیشنهادشده برای تقریباً همهٔ گوشی‌ها و تبلت‌های ARM 64 بیتی امروزی؛ کوچک‌ترین بستهٔ مناسب.
- `Saman-Tunnel-v1.5.0-armeabi-v7a.apk` — فقط برای دستگاه‌های قدیمی ARM 32 بیتی.
- `Saman-Tunnel-v1.5.0-universal-arm.apk` — شامل هر دو ABI از نوع ARM؛ وقتی ABI دستگاه مشخص نیست یا یک فایل باید هر دو نسل را پوشش دهد انتخاب کنید. حجم آن بیشتر است.

برای برنامهٔ اندروید APK مخصوص x86/x86_64 منتشر نمی‌شود.

#### ارتقا از نسخهٔ قدیمی اندروید

- برای ارتقای درجا، ابتدا نسخهٔ قبلی را حذف نکنید.
- APK جدید را اعتبارسنجی کنید و سپس روی بستهٔ موجود `com.saman.tunnel` نصب نمایید.
- اندروید فقط وقتی ارتقا را می‌پذیرد که نام بسته و هویت امضا یکسان باشد. خطای «package conflicts» یا «app not installed» می‌تواند نشان‌دهندهٔ signer متفاوت باشد؛ پیش از حذف نسخهٔ ناسازگار، تنظیمات غیرمحرمانهٔ لازم را پشتیبان بگیرید.
- پیش از ارتقا تونل فعال را متوقف کنید. پس از نصب، برنامه را یک‌بار باز و نسخهٔ نمایش‌داده‌شده و سلامت پروکسی محلی را بررسی کنید.

### نصب و ارتقا در Termux

پیش‌نیازها:

- نسخهٔ جدید Termux از F-Droid یا پروژهٔ رسمی Termux در GitHub؛ از نسخهٔ منسوخ Play Store استفاده نکنید؛
- دسترسی شبکه به `api.github.com`، `github.com` و `raw.githubusercontent.com`؛
- Termux:Widget برای shortcut اختیاری صفحهٔ اصلی؛ Termux:API فقط برای یکپارچه‌سازی اختیاری باتری/اعلان؛
- اجرای `termux-setup-storage` فقط برای export به فضای اشتراکی لازم است. اجرای هسته به فضای اشتراکی نیاز ندارد.

نصب تمیز یا تعمیر idempotent:

```bash
curl --proto '=https' --tlsv1.2 -fsSL \
  https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh \
  -o "$TMPDIR/saman-install.sh"
bash "$TMPDIR/saman-install.sh" install
```

بررسی نسخه‌ها یا ارتقا:

```bash
bash "$TMPDIR/saman-install.sh" check
bash "$TMPDIR/saman-install.sh" update
```

نصب‌کننده معماری `arm64`، `armv7` یا `x86_64` را تشخیص می‌دهد؛ releaseهای non-draft و non-prerelease را از GitHub می‌گیرد؛ archive دقیق معماری را انتخاب می‌کند؛ وجود فایل `.sha256` را اجباری می‌داند؛ در mismatch بدون تغییر امن متوقف می‌شود؛ سورس و هسته را پیش از تغییر فایل‌های فعال stage می‌کند؛ یک پشتیبان خصوصی زمان‌دار در `~/.saman-aether-backups/` می‌سازد و در صورت شکست نصب یا verification، rollback انجام می‌دهد. تنظیمات کاربر، لاگ‌ها، `aether` رسمی و shortcutهای نامرتبط حفظ می‌شوند.

پس از نصب، Termux:Widget را refresh کنید. در Android 15 برای session طولانی، مصرف باتری Termux را unrestricted کنید و در صورت نیاز اعلان و wake lock را مجاز نگه دارید. اندروید ممکن است Termux را زیر فشار حافظه یا باتری ببندد؛ Saman فایل PID مانده و یک پردازش orphan از هستهٔ مرجع را تشخیص می‌دهد و شروع صریح، orphanهای همان هسته را پیش از bind پورت پاک می‌کند.

### استفاده

```bash
saman status
saman doctor --verbose
saman repair --dry-run
saman logs aether --lines 80
saman config show
saman config validate
saman config set NETWORK_TIMEOUT 8
saman refresh
saman backup
saman share
saman aether status
saman aether start h3
saman aether start h2
saman aether start wg
saman aether start gool
saman aether restart wg
saman aether stop
saman aether test
saman aether diagnostics safe
saman aether diagnostics full
```

Saman Center 2.1 فقط ماژول لازم برای فرمان انتخاب‌شده را بارگذاری می‌کند؛ بنابراین
`version` و `help` سریع و بدون تغییر وضعیت هستند. فرمان `status` فقط یک snapshot
از پردازش‌ها و socketها می‌گیرد، `doctor` شکست dependencyهای ضروری را از هشدار
ابزارهای اختیاری جدا می‌کند، `repair --dry-run` فقط پاک‌سازی محافظه‌کارانهٔ runtime
را پیش‌نمایش می‌کند و خروجی `logs` غیرتعاملی و محدود به تعداد خط است.

تنظیمات عددی در `~/.config/saman-center-v2/settings.conf` قرار دارند. این فایل فقط
با whitelist خوانده می‌شود و هرگز به‌عنوان کد shell اجرا نمی‌شود. برای دیدن مقدارهای
موثر از `saman config show`، برای تشخیص مقدار نامعتبر یا تداخل پورت از
`saman config validate` و برای به‌روزرسانی اتمیک از `saman config set KEY VALUE`
استفاده کنید. کلیدهای پشتیبانی‌شده پورت‌های Aether/Share، محدودیت حجم/فضای آزاد/
اتصال Share، deadline شبکه و Termux API، TTL کش dashboard، اندازهٔ محدود لاگ،
زمان‌بندی readiness/تحویل پورت/محافظ لاگ Aether و آستانه‌های Smart Reconnect را
پوشش می‌دهند. خط‌های سفارشی ناشناخته حفظ می‌شوند، اما هرگز ارزیابی نمی‌شوند.

فرمان‌های سازگاری نیز معتبرند:

```bash
saman2 aether status
aether-control start h3
aether-control status
aether-control diagnostics safe
```

فقط یک نمونهٔ مرجع `saman-aether-core` باید فعال باشد. هنگام تغییر حالت، PIDها با executable دقیق هسته تطبیق داده می‌شوند، تا پنج ثانیه برای توقف صبر می‌شود و فقط PIDای که هنوز متعلق به همان هسته است در صورت ضرورت force-stop می‌شود؛ سپس پورت‌ها بررسی و حالت جدید اجرا می‌شود. لاگ جاری هر حالت به ۵ MiB و لاگ قبلی به ۱ MiB محدود است.

### سیاست پشتیبان و بازیابی

- دادهٔ برنامهٔ اندروید عمداً از Android backup خارج شده است.
- هر عملیات install/update/uninstall در Termux ابتدا فایل‌های مدیریت‌شده را با دسترسی محدود در `~/.saman-aether-backups/<timestamp>/` ثبت می‌کند. rollback خودکار در صورت شکست، targetهای مدیریت‌شده را دقیقاً برمی‌گرداند.
- فرمان `saman backup` یک snapshot از سورس/اسکریپت‌ها می‌سازد و عمداً کوکی، کلید SSH، کلید خصوصی، دانلود شخصی و تنظیمات خصوصی را کنار می‌گذارد.
- پشتیبان‌های نصب‌کننده را بدون بازبینی به فضای عمومی/اشتراکی منتقل نکنید. بازیابی دستی فقط از پشتیبان زمان‌دار قابل‌اعتماد و در حالی انجام شود که Aether متوقف است.

### اعتبارسنجی

در پوشه‌ای که APKها و فایل checksum قرار دارند، همهٔ فایل‌ها را بررسی کنید:

```bash
sha256sum -c SHA256SUMS
```

هش‌های مورد انتظار v1.5.0:

```text
a618e3c9f356d8165afec7a33b237caadc894eb8fc818f85abef762ea7f7b74b  Saman-Tunnel-v1.5.0-arm64-v8a.apk
16282ebbc1e824dcc2b3e4b0f1722f7d0038989f83812bab067c76a970156512  Saman-Tunnel-v1.5.0-armeabi-v7a.apk
9a82aaafa4e9af7a9b3f4e2e25a3cec81cad2e5198068750da0c16c140d4843e  Saman-Tunnel-v1.5.0-universal-arm.apk
```

اگر Android Build Tools نصب است، پیوستگی امضا را بررسی کنید:

```bash
apksigner verify --verbose --print-certs Saman-Tunnel-v1.5.0-arm64-v8a.apk
```

SHA-256 مورد انتظار گواهی signer:

```text
03233edadf89ed27c7cf80452916a7bd25e8c74abe0f12d2eefcb8b290b48805
```

Checksum سلامت فایل را ثابت می‌کند و fingerprint گواهی، پیوستگی هویت انتشار Saman Tunnel را. هر دو را بررسی کنید و فقط از URLهای HTTPS در GitHub Releases استفاده نمایید.

### ساخت از سورس

ابزارهای تأییدشده برای build نسخهٔ v1.5.0:

- Aether Core v1.8.0 روی commit `a916ff6fbbb4ebafe8314c53cf3718eb51dcae53`؛
- Rust `1.89.0` و cargo-ndk `4.1.2`؛
- Android NDK `26.3.11579264`، CMake `3.22.1`، platform 36 و Build Tools `35.0.0`؛
- Java/Temurin 17، Gradle `8.13`، Android Gradle Plugin `8.13.2` و Kotlin `2.3.21`.

به‌جای build از checkout دلخواه Aether، workflow تأییدشده را بازتولید کنید:

```bash
git clone https://github.com/velnox4827/saman-aether.git
cd saman-aether
git switch --detach v1.5.0
# مطابق .github/workflows/android-apk.yml، commit ثابت Aether را دریافت کنید،
# هر دو patch را اعمال کنید و libaether.so را برای arm64-v8a و armeabi-v7a بسازید.
cd android-app
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

build انتشار به هویت خصوصی و موجود امضا از طریق چهار property گرادل با نام `SAMAN_*` نیاز دارد. keystore یا گذرواژه را هرگز در مخزن یا history فرمان قرار ندهید. build محلی/بدون امضا نمی‌تواند APK رسمی نصب‌شده را ارتقا دهد.

برای executable پچ‌شدهٔ Termux، فایل `.github/workflows/termux-core.yml` targetهای اندروید را با cargo-ndk می‌سازد و executable را با نام `saman-aether-core` برای arm64، armv7 و x86_64 بسته‌بندی می‌کند.

### CI و GitHub Actions

- توسعه روی `main`، شاخه‌های `release/**` و شاخه‌های maintenance متمرکز انجام می‌شود؛ tagهای عمومی و immutable نسخه‌های انتشار را مشخص می‌کنند.
- `android-apk.yml` تست واحد و lint اندروید را اجرا می‌کند، سه گونهٔ debug APK می‌سازد و در اجرای دستی، کلید امضا را از GitHub Secrets بازیابی می‌کند، APKهای release را می‌سازد، package/version/libraryهای بومی و گواهی مورد انتظار را بررسی می‌کند و APKها همراه `SHA256SUMS` را به‌عنوان artifact قرار می‌دهد.
- `release.yml` به tagهای `v*` پاسخ می‌دهد و انتشار عمومی اندروید را می‌سازد. tagهای موجود و artifactهای v1.5.0 هرگز نباید جابه‌جا یا جایگزین شوند.
- `termux-core.yml` هسته‌های پچ‌شدهٔ Termux را برای سه معماری می‌سازد و releaseهای غیر latest از نوع `termux-v*` را همراه فایل SHA-256 هر archive منتشر می‌کند.
- `termux-maintenance.yml` تست syntax، routing و سیاست نصب‌کننده را به‌همراه ShellCheck روی پیاده‌سازی مرجع Termux اجرا می‌کند.
- اطلاعات امضا فقط هنگام job امضاشده و از GitHub Secrets استفاده می‌شود. pull request و push عادی، انتشار رسمی امضاشده تولید نمی‌کنند.

### امنیت

keystore امضا، گذرواژهٔ امضا، کلید خصوصی، API/OAuth key، توکن ربات Telegram، GitHub token، کوکی، `rpc.secret`، credential و تنظیمات خصوصی را هرگز نمایش، لاگ، آپلود یا commit نکنید. فایل امضا را در فضای خصوصی برنامه نگه دارید و به shared storage انتقال ندهید. پیش از commit، `git status`، diff مرحله‌بندی‌شده، فایل‌های untracked و تغییر workflowها را بررسی کنید. گزارش‌ها پاک‌سازی می‌شوند، اما پیش از اشتراک‌گذاری همچنان باید بازبینی شوند.

پروکسی‌ها فقط روی loopback گوش می‌دهند. آن‌ها را بدون درک و ایمن‌سازی ریسک، به `0.0.0.0` تغییر ندهید. آپدیت را فقط از صفحهٔ HTTPS Releases همین مخزن بگیرید، SHA-256 و پیوستگی signer را بررسی کنید و نصب‌کنندهٔ mirror ناشناس را اجرا نکنید.

### رفع مشکلات

- **Aether شروع نمی‌شود:** `saman doctor --verbose` و سپس `saman aether status` را اجرا کنید. executable بودن `$PREFIX/bin/saman-aether-core` و `~/.aether-shortcut-runner` و دسترسی به GitHub را بررسی نمایید.
- **PID مانده یا پردازش orphan:** `saman aether status`، PID را با executable دقیق هستهٔ پچ‌شده می‌سنجد و می‌تواند یک orphan را بازیابی کند. `saman aether stop` پردازش‌های مرجع مطابق را پاک می‌کند و بر اساس PID نامعتبر، پردازش دلخواه را نمی‌کشد.
- **تداخل پورت:** پروکسی دیگری را که از `127.0.0.1:1819` یا `:1820` استفاده می‌کند متوقف و با `ss -ltnp` بررسی کنید. Saman برای hand-off صبر می‌کند و به‌جای اجرای listener دوم، امن متوقف می‌شود.
- **مشکل سرویس اندروید/VPN:** Saman Tunnel یک `VpnService` نیست. مجوز اعلان و اجرای foreground/background را بدهید، محدودیت باتری را بردارید و bypass بر اساس برنامه در VPN اصلی را درست تنظیم کنید.
- **Termux توسط اندروید بسته می‌شود:** نسخهٔ جدید Termux نصب کنید، battery optimization را برای آن غیرفعال نمایید، فعالیت پس‌زمینه را مجاز کنید و برای session طولانی wake lock داشته باشید. Termux را دوباره باز و پیش از restart وضعیت را بررسی کنید.
- **شکست آپدیت/دانلود:** DNS/TLS و اتصال به `api.github.com`، `github.com` و `raw.githubusercontent.com` را بررسی و `install.sh check` را تکرار کنید. نصب‌کننده checksum گمشده یا نامطابق را نمی‌پذیرد.
- **APK نصب یا ارتقا نمی‌شود:** ABI، فضای خالی، نام بسته، checksum و signer را بررسی کنید. mismatch امضا نیازمند حذف بسته‌ای است که با امضای متفاوت نصب شده؛ هویت رسمی امضا نباید جایگزین شود.
- **اتصال GitHub/API:** صفحهٔ مستقیم [Releases](https://github.com/velnox4827/saman-aether/releases) را در مرورگر باز کنید. فقط پس از سالم‌شدن Aether، GitHub را از SOCKS محلی عبور دهید.
- **لاگ و عیب‌یابی:** از `saman aether logs` استفاده کنید، فایل `~/aether-<mode>.log` را ببینید یا `saman aether diagnostics safe` بسازید. فقط وقتی history بیشتری لازم است از `full` استفاده کنید؛ آن هم پاک‌سازی می‌شود ولی بزرگ‌تر است.

### ساختار پروژه

```text
.github/workflows/                 CI اندروید، انتشار، هسته Termux و maintenance
android-app/                       پروژه رابط/سرویس/JNI اندروید
  aether-patches/                  پچ‌های Smart Reconnect و listener
  app/src/main/java/com/saman/tunnel/
termux/saman-center-v2/            پیاده‌سازی ماژولار و مرجع Saman Center
termux/aether-control              delegator سازگاری
termux/tests/                      تست‌های shell و routing در Termux
install.sh                         نصب/ارتقا/rollback به‌صورت idempotent
.aether-shortcut-runner (نصب‌شده)  <- فایل مخزن: aether-shortcut-runner
saman-aether-diagnostics           عیب‌یابی پاک‌سازی‌شدهٔ Termux
shortcuts/                         سورس سازگاری قدیمی؛ در نصب جدید ساخته نمی‌شود
CHANGELOG.md                       تاریخچهٔ انتشار و maintenance
```

### نسخه‌ها و تغییرات

- [Saman Tunnel v1.5.0](https://github.com/velnox4827/saman-aether/releases/tag/v1.5.0)
- [CHANGELOG.md](CHANGELOG.md)
- [همهٔ نسخه‌ها در GitHub Releases](https://github.com/velnox4827/saman-aether/releases)
- [Aether اصلی](https://github.com/CluvexStudio/Aether)
- [Issues](https://github.com/velnox4827/saman-aether/issues)

</div>

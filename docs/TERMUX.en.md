# Saman Aether — Termux + Termux:Widget v1.4.0

The Termux edition is independent from the standalone Saman Tunnel APK.

Starting with v1.4.0, the installer uses a patched Aether executable named:

```text
$PREFIX/bin/saman-aether-core
```

It is installed alongside an upstream `aether` binary and does not overwrite it.

## v1.4.0 highlights

- Smart Reconnect for WG, MASQUE H3, MASQUE H2 and GOOL
- Default scan remains `balanced`
- `--quick-reconnect`
- `--reconnect-secs 1`
- SOCKS5 `127.0.0.1:1819`
- HTTP CONNECT `127.0.0.1:1820`
- Reusable local SOCKS5/HTTP listener binding for cleaner restarts
- Active-listener port hand-off check before start
- 250 ms log-based readiness detection without synthetic SOCKS `early eof`
- MASQUE H2 Termux:Widget shortcut
- Current + previous per-mode logs
- Safe / Full diagnostics

## Aether v1.8.0 upstream improvements

This edition also inherits upstream v1.8.0 stability work, including GOOL crash fixes, better WireGuard task/panic resilience, authentication fixes, more efficient HTTP proxy handling, and protection against zero-value reconnect busy loops.

## Smart Reconnect thresholds

- WG cached RTT: 650 ms
- MASQUE H3 cached verify: 1800 ms
- MASQUE H2 cached verify: 2500 ms
- Short-lived path/pair: 20 seconds

These thresholds affect cached-path reuse only. Fresh balanced-scan results are not rejected simply for exceeding them.

## Install / update

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash
```

Prebuilt patched cores are provided for arm64, armv7 and x86_64 and are SHA256 verified.

## Termux:Widget shortcuts

```text
0-STOP-Aether
1-Aether-MASQUE
2-Aether-WG
3-Aether-GOOL
4-Aether-MASQUE-H2
5-Aether-SAFE-LOG
```

`1-Aether-MASQUE` keeps the old filename for compatibility and runs MASQUE H3.

## Local proxies

```text
SOCKS5       127.0.0.1:1819
HTTP CONNECT 127.0.0.1:1820
```

Saman Aether exposes local proxies; it is not itself a device-wide Android VPN.

## Diagnostics

```bash
saman-aether-diagnostics safe
saman-aether-diagnostics full
```

Safe mode limits log history and redacts selected known identifiers. Full mode preserves retained logs and should be reviewed before public sharing.

## Remove

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash -s -- uninstall
```

This removes the Saman patched core, runner, diagnostics tool and shortcuts. A separately installed upstream `aether` binary is not removed.

## Source

Upstream Aether:

https://github.com/CluvexStudio/Aether

Saman repository:

https://github.com/velnox4827/saman-aether

License: GNU AGPL-3.0

# Saman Aether — Termux + Termux:Widget Guide

This guide covers the original **Termux-based** workflow. It is kept alongside the standalone Saman Tunnel APK.

## Requirements

Install:

1. Termux
2. Termux:Widget

Using compatible builds from the same source is recommended.

## Install

Open Termux and run:

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash
```

The installer installs/updates official Aether and creates:

```text
0-STOP-Aether
1-Aether-MASQUE
2-Aether-WG
3-Aether-GOOL
```

Add **Termux:Widget** to the Android home screen and refresh it if necessary.

## Local SOCKS5

```text
Protocol: SOCKS5
Host: 127.0.0.1
Port: 1819
```

## Current shortcut arguments

MASQUE:

```text
--masque -4 --bind 127.0.0.1:1819 --scan balanced --noize firewall --quick-reconnect
```

WireGuard:

```text
--wg -4 --bind 127.0.0.1:1819 --scan balanced --noize balanced --keepalive 5 --quick-reconnect
```

GOOL:

```text
--gool -4 --bind 127.0.0.1:1819 --scan balanced --noize balanced --keepalive 5 --quick-reconnect
```

## Update

Run the same installer command again.

## Remove Saman shortcuts

```bash
curl -fsSL https://raw.githubusercontent.com/velnox4827/saman-aether/main/install.sh | bash -s -- uninstall
```

This removes the Saman runner and widget shortcuts, but does not uninstall upstream Aether.

## Issues and suggestions

https://github.com/velnox4827/saman-aether/issues

Please mention **Termux/Widget version** in the issue so it is not confused with the standalone APK.


## Telegram Community

For general Termux/Widget discussion:

https://t.me/SamanTunnel

Please mention that you are using the **Termux/Widget version**.

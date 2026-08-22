# Saman Tunnel User Guide

## Install

Download the latest APK from GitHub Releases:

https://github.com/velnox4827/saman-aether/releases/latest

Current builds target `arm64-v8a` and require Android 7 or newer.

## Start a tunnel

Open Saman Tunnel and select one mode:

- **MASQUE** — a good default starting point.
- **WireGuard** — typically lower overhead and fast when the network allows it.
- **GOOL** — WireGuard-in-WireGuard; heavier and more network-dependent.

Wait until the application shows:

```text
Status: Connected — SOCKS5 127.0.0.1:1819
```

## Use the SOCKS5 proxy

Configure the downstream application with:

```text
Protocol: SOCKS5
Host: 127.0.0.1
Port: 1819
```

If that downstream application creates an Android system VPN, **exclude/bypass Saman Tunnel itself** from that VPN to prevent a routing loop.

## Battery optimization

If Android kills the tunnel in the background, set Saman Tunnel battery usage to **Unrestricted / No restrictions**. Consider doing the same for the downstream VPN/proxy application.

## Diagnostics

Use **Save TXT** to export:

```text
Saman-Tunnel-diagnostics.txt
```

Review the file before posting it publicly because logs may contain IP addresses, endpoints, and technical connection details.

Report bugs here:

https://github.com/velnox4827/saman-aether/issues/new?template=bug_report.md

Suggest features here:

https://github.com/velnox4827/saman-aether/issues/new?template=feature_request.md


## Telegram Community

General discussion, testing and community help:

**Saman Tunnel • Community**

https://t.me/SamanTunnel

For trackable bugs and feature requests, please use GitHub Issues:
https://github.com/velnox4827/saman-aether/issues

# Saman Tunnel Android prototype

This is a minimal Android client that embeds the open-source Aether core as a native shared library and exposes a local SOCKS5 proxy at:

- `127.0.0.1:1819`

It does **not** require Termux at runtime.

## Why this solves the bypass problem

The Aether core runs inside the Android app's UID. If another VPN app supports per-app bypass/exclusion, exclude **Saman Tunnel** instead of excluding Termux. Termux can then remain routed through your other VPN/proxy configuration.

## Modes included

- MASQUE
- WireGuard
- GOOL
- Stop

The arguments match the current Saman Aether Termux runner:

- MASQUE: IPv4, balanced scan, firewall noize, quick reconnect
- WG: IPv4, balanced scan/noize, keepalive 5, quick reconnect
- GOOL: IPv4, balanced scan/noize, keepalive 5, quick reconnect

## Build from GitHub Actions

Copy this package into the root of the `saman-aether` repository so the paths are:

- `android-app/...`
- `.github/workflows/android-apk.yml`

Then push to GitHub and open **Actions → Build Saman Tunnel APK → Run workflow**.

The workflow builds the upstream Aether Rust `cdylib` for `arm64-v8a`, links it into the app, builds the APK, and uploads `app-debug.apk` as a workflow artifact.

## First test

1. Install the APK.
2. Open Saman Tunnel.
3. Tap WireGuard first.
4. Wait until the status says `Connected — SOCKS5 127.0.0.1:1819`.
5. Configure a SOCKS5-aware client to use `127.0.0.1:1819`.
6. In the outer VPN app, bypass/exclude only `Saman Tunnel`.
7. Do not bypass Termux.

## Prototype limits

- Current build is arm64 only.
- No exit-IP/country display yet.
- No in-app log viewer yet.
- No automatic updater yet.
- This is a prototype and should be tested before broad distribution.

## Upstream license and trademark

The Aether core is from `CluvexStudio/Aether` and is licensed under AGPL-3.0. Its trademark policy states derivative products should use different branding unless permission is granted. For that reason this prototype is named **Saman Tunnel**, not Aether.

If the APK is distributed, preserve the AGPL obligations and provide the corresponding source code for the version distributed.

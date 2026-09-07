# Saman Tunnel 1.7.5 debug

The failed v1.7.4 run (34118427762, commit 64c37ec) did not reach APK assembly.
Kotlin compilation failed on the split character literal in Xiaomi battery
status parsing. The supplied Termux Gradle log is a separate missing-SDK error.

The v1.7.3 device report showed GOOL cancellation returning while ports 1819 and
1820 remained bound. The pinned Aether GOOL function detached its outer tunnel,
inner tunnel and proxy tasks when FFI cancellation dropped the owning future.
The new patch gives those tasks cancellation guards, including partial startup.
Android now waits for both native completion and closed listeners before Stop.
The dedicated core process is then recycled, including its process-wide Rust
runtime and any detached clients. The UI process remains alive.

VPN state is sent to the main-process receiver instead of writing preferences
from a separate process. Repeated starts preserve connected notifications.
Stop cancels pending startup, closes HEV and TUN, and notification Stop stops
both services. Revoke and worker loss also stop the core. Cleanup and routing
changes are serialized off the main thread, with a bounded process fallback.
VPN errors survive teardown; a healthy proxy alone is not shown as connected VPN.
Denying consent or switching to Proxy cancels pending permission work.

Routing rejects an empty effective allow list after apps have been uninstalled.
The widget opens VPN consent through the app for a VPN connection. Notification
text includes protocol and Proxy/VPN. Diagnostics include both states.

Debug installs as **Saman Tunnel Debug** (`com.saman.tunnel.debug`) using a
fixed public development key. This establishes a separate debug update stream;
configure connection and routing once. Stop the older app before connecting,
as both versions use the same local ports. Production signing stays separate.

## Termux

```sh
cd "$HOME/saman-aether"
git switch vpn-debug-hev
git pull --ff-only origin vpn-debug-hev
bash termux/build-debug.sh
```

The helper finds a run for the exact current commit, waits for its result,
verifies SHA-256 checksums and selects the APK matching Android's ABI.
Use `--push` for an already-committed clean local change or `--build` for a new
manual debug run. Missing authentication is handled with `gh auth login`.
No local Android SDK or NDK is needed for this workflow. It uses GitHub Actions.

## Device acceptance tests (still required)

- Samsung SM-A566B, Android 16: connect GOOL VPN, transfer traffic, Stop, then
  reconnect 10 times. Both notifications and the Android VPN indicator must
  disappear after Stop; no port-in-use error or force-stop should be needed.
- Repeat with WG, MASQUE H3 and H2 in both connection modes on an available
  network. A server or carrier rejecting a transport is not proof of an app bug.
- Stop during scan, SOCKS wait and VPN preparation. Grant a stale consent dialog
  after Stop and verify it cannot restart VPN. Deny consent and explicitly retry.
- Tap a connected mode repeatedly: it must not disconnect or show Preparing.
- Switch routing while connected, then Stop immediately: no delayed restart.
- Select only an app, uninstall it, reconnect: report the routing error without
  silently routing all apps. Test the bypass and all-apps modes separately.
- Start from the widget with VPN selected; check protocol and VPN/Proxy status.
- Switch to another VPN and return; Saman must release its TUN and core.
- Install a later debug build over this debug app: preserve settings without
  conflicting with the production package. This key is for development only.

A successful CI run verifies build, lint, unit tests, packaging and signatures.
It does not replace device/network tests or promise connection speed.

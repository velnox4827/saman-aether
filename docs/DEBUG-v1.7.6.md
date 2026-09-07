# Saman Tunnel 1.7.6 debug

## Confirmed reconnect failure

The supplied v1.7.5 device log contains two instances of the same sequence:
WireGuard detects a stale peer, closes the local proxy and starts reconnecting.
The native job remains alive and begins validating another endpoint. The VPN
watchdog then cancels that job after three missed local SOCKS5 probes, reporting
`VPN proxy unavailable`. The log records normal cancellation, not an Android
process kill. It does not establish that battery management caused the drop.

The VPN now retains TUN and HEV while the core is reconnecting. Three consecutive
probe failures change its status to reconnecting; proxy recovery restores the
connected status. There is no arbitrary reconnection deadline that cancels the
native retry loop.

A private, opaque Binder connection observes the already-started core across
the two Android service processes. It uses no local Binder cast and no automatic
service creation. The VPN still closes on actual core loss, HEV worker exit,
permission revocation or explicit Stop. Bindings are released during shutdown.
Android documents started-and-bound services and service-disconnection callbacks
in its [bound services guide](https://developer.android.com/develop/background-work/services/bound-services)
and [ServiceConnection reference](https://developer.android.com/reference/android/content/ServiceConnection).

Reconnection is shown in the core/VPN notifications, app and widget. The running
VPN flag records that TUN is retained; it does not hide the reconnecting status.
An activity pause is logged so future reports can correlate backgrounding with
network events.

## Selected apps

Checked packages sort first in both Only selected and Bypass modes. Names sort
alphabetically within each group, with package name breaking ties. Checking or
unchecking an app rerenders the list after the click, preserving the search.
Routing mode semantics and the saved package selection are unchanged.

## Build and device verification

Version code/name: `176` / `1.7.6`. The debug package and development certificate
are retained so this APK can update v1.7.5 debug without removing its settings.
The existing unit-test, lint, native-library, signature and APK checks remain
enabled in GitHub Actions. The Termux helper downloads the exact commit's APK.

Required device checks after installing the debug APK:

- Use WireGuard VPN, open another app and keep using the connection. If the
  endpoint reconnects, the VPN must show reconnecting and recover without an
  automatic core cancellation or disappearance of the TUN interface.
- Briefly lose network access and restore it. Check that connection attempts
  continue and recovery occurs when the underlying transport becomes available.
- Press Stop during reconnection: both services and notifications must stop,
  and the VPN must stay off. Then reconnect without force-stopping the app.
- Stop/kill the actual core process during an active test: the VPN must clean up
  instead of indefinitely retaining a TUN with no core. HEV failure and VPN
  permission revocation must still trigger cleanup.
- In both Only selected and Bypass, reopen the list with several checked apps;
  verify they appear first. Search, check/uncheck, clear the search, save and
  reopen; verify the selection and ordering.

Local compilation and policy tests cannot replace these phone/network checks.

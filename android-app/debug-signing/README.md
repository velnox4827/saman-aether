# Public debug signing identity

`PUBLIC-DEBUG-KEYSTORE.base64` is an intentionally public development PKCS12
keystore. Its alias is `androiddebugkey` and its password is `android`.
It is NOT a secret and MUST NEVER sign production builds.

Debug APKs use `com.saman.tunnel.debug` and the label **Saman Tunnel Debug**.
The same development key is used on every build, so debug updates no longer
depend on the ephemeral runner's newly generated Android debug certificate.
The production package and its signing secret requirements are unchanged.

Version 1.7.5 is the first build in this separate debug install stream. Stop any
older Saman Tunnel instance before connecting, because both apps use local
ports 1819 and 1820. Settings must be selected once in the new debug app.

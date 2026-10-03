package com.saman.tunnel

/** Official Aether v2 command lines only; no Saman-specific protocol flags. */
object AetherArguments {
    const val APP_SOCKS_PORT = 1819
    const val APP_HTTP_PORT = 1820
    private const val socks = "127.0.0.1:$APP_SOCKS_PORT"
    private const val http = "127.0.0.1:$APP_HTTP_PORT"
    private const val psiphonSocks = "127.0.0.1:1821"
    private const val psiphonHttp = "127.0.0.1:1822"

    fun canonicalMode(mode: String): String = mode.trim().uppercase()

    fun needsPsiphonBinary(mode: String): Boolean = canonicalMode(mode) == "PSIPHON_ONLY"

    fun forMode(mode: String, psiphonBinary: String? = null): List<String> = when (canonicalMode(mode)) {
        "TOR_ONLY" -> listOf("--tor-only", "--bind", socks)
        "TOR_REVERSE" -> listOf("--masque", "--h2", "-4", "--bind", socks, "--tor-reverse", "--tor-bind", http)
        "TOR_INSIDE_WG" -> listOf("--wg", "-4", "--bind", socks, "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect", "--tor", "--tor-bind", http)
        "TOR_INSIDE_GOOL" -> listOf("--gool", "-4", "--bind", socks, "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect", "--tor", "--tor-bind", http)
        "TOR_INSIDE_MASQUE_H2" -> listOf("--masque", "--h2", "-4", "--bind", socks, "--scan", "balanced", "--noize", "firewall", "--quick-reconnect", "--tor", "--tor-bind", http)
        "TOR_INSIDE_MASQUE", "TOR_INSIDE_MASQUE_H3" -> listOf("--masque", "-4", "--bind", socks, "--scan", "balanced", "--noize", "firewall", "--quick-reconnect", "--tor", "--tor-bind", http)
        "MASQUE_H2" -> listOf("--masque", "--h2", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "firewall", "--quick-reconnect")
        "MASQUE_H3", "MASQUE" -> listOf("--masque", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "firewall", "--quick-reconnect")
        "GOOL" -> listOf("--gool", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect")
        "PSIPHON_ONLY" -> buildList {
            addAll(listOf("--psiphon", "--masque", "--h2", "--bind", socks, "--psiphon-bind", psiphonSocks, "--psiphon-http", psiphonHttp, "--http-proxy", http))
            if (!psiphonBinary.isNullOrBlank()) addAll(listOf("--psiphon-bin", psiphonBinary))
        }
        "MIM_H2" -> forMode("MIM_H3") + "--h2"
        "MIM_H3", "MIM" -> listOf("--mim", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "firewall", "--quick-reconnect")
        else -> listOf("--wg", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect")
    }
}

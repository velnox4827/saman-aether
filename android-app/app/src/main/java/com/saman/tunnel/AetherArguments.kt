package com.saman.tunnel

/** Official Aether v2 command lines only; no Saman-specific core flags. */
object AetherArguments {
    private const val socks = "127.0.0.1:1819"
    private const val http = "127.0.0.1:1820"

    fun forMode(mode: String): List<String> = when (mode.uppercase()) {
        "TOR_ONLY" -> listOf("--tor-only", "--bind", socks)
        "TOR_REVERSE" -> listOf("--masque", "--h2", "-4", "--bind", socks, "--tor-reverse", "--tor-bind", http)
        "TOR_INSIDE_WG" -> listOf("--wg", "-4", "--bind", socks, "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect", "--tor", "--tor-bind", http)
        "TOR_INSIDE_GOOL" -> listOf("--gool", "-4", "--bind", socks, "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect", "--tor", "--tor-bind", http)
        "TOR_INSIDE_MASQUE_H2" -> listOf("--masque", "--h2", "-4", "--bind", socks, "--scan", "balanced", "--noize", "firewall", "--quick-reconnect", "--tor", "--tor-bind", http)
        "TOR_INSIDE_MASQUE", "TOR_INSIDE_MASQUE_H3" -> listOf("--masque", "-4", "--bind", socks, "--scan", "balanced", "--noize", "firewall", "--quick-reconnect", "--tor", "--tor-bind", http)
        "MASQUE_H2" -> listOf("--masque", "--h2", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "firewall", "--quick-reconnect")
        "MASQUE_H3", "MASQUE" -> listOf("--masque", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "firewall", "--quick-reconnect")
        "GOOL" -> listOf("--gool", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect")
        "MIM" -> listOf("--mim", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "firewall", "--quick-reconnect")
        else -> listOf("--wg", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect")
    }
}

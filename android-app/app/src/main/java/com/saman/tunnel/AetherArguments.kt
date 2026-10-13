package com.saman.tunnel

/** Official Aether v2.3 command lines only; no Saman-specific protocol flags. */
object AetherArguments {
    const val APP_SOCKS_PORT = 1819
    const val APP_HTTP_PORT = 1820
    /** Internal tunnel listener when Psiphon owns the app-facing ports. */
    const val INNER_SOCKS_PORT = 1821
    private const val socks = "127.0.0.1:$APP_SOCKS_PORT"
    private const val http = "127.0.0.1:$APP_HTTP_PORT"
    private const val inner = "127.0.0.1:$INNER_SOCKS_PORT"

    /** Psiphon exit shapes the core accepts for --psiphon-mode. */
    val PSIPHON_SHAPES = listOf("auto", "cdn", "direct")

    fun canonicalMode(mode: String): String = ModeCatalog.canonical(mode)

    fun needsPsiphonBinary(mode: String): Boolean = ModeCatalog.isPsiphon(mode)

    fun forMode(mode: String, psiphonBinary: String? = null, psiphonRegion: String? = null, psiphonShape: String? = null): List<String> =
        when (canonicalMode(mode)) {
            "TOR_ONLY" -> listOf("--tor-only", "--bind", socks)
            "TOR_REVERSE" -> listOf("--masque", "--h2", "-4", "--bind", socks, "--tor-reverse", "--tor-bind", http)
            "TOR_INSIDE_MASQUE_H3" -> listOf("--masque", "-4", "--bind", socks, "--scan", "balanced", "--noize", "firewall", "--quick-reconnect", "--tor", "--tor-bind", http)
            "MASQUE_H2" -> listOf("--masque", "--h2", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "firewall", "--quick-reconnect")
            "MASQUE_H3" -> listOf("--masque", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "firewall", "--quick-reconnect")
            "GOOL" -> listOf("--gool", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect")
            "GOOL_CLASSIC" -> listOf("--gool-classic", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect")
            // Psiphon in tunnel: Psiphon is the app-facing exit, so it takes 1819/1820 and the WARP tunnel moves inside.
            "PSIPHON" -> listOf("--psiphon", "--masque", "--h2", "-4", "--bind", inner, "--psiphon-bind", socks, "--psiphon-http", http) +
                psiphonArgs(psiphonBinary, psiphonRegion, psiphonShape)
            // Reverse: WARP (on 1819) is dialled through Psiphon; Psiphon stays internal.
            "PSIPHON_REVERSE" -> listOf("--psiphon-reverse", "--masque", "--h2", "-4", "--bind", socks, "--http-proxy", http, "--psiphon-bind", inner) +
                psiphonArgs(psiphonBinary, psiphonRegion, psiphonShape)
            "PSIPHON_ONLY" -> listOf("--psiphon-only", "--bind", socks, "--psiphon-http", http) +
                psiphonArgs(psiphonBinary, psiphonRegion, psiphonShape)
            "MIM_H2" -> forMode("MIM_H3") + "--h2"
            "MIM_H3" -> listOf("--mim", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "firewall", "--quick-reconnect")
            else -> listOf("--wg", "-4", "--bind", socks, "--http-proxy", http, "--scan", "balanced", "--noize", "balanced", "--keepalive", "5", "--quick-reconnect")
        }

    fun validRegion(code: String?): String? =
        code?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }

    private fun psiphonArgs(binary: String?, region: String?, shape: String?): List<String> = buildList {
        validRegion(region)?.let { addAll(listOf("--psiphon-region", it)) }
        shape?.trim()?.lowercase()?.takeIf { it in PSIPHON_SHAPES && it != "auto" }?.let { addAll(listOf("--psiphon-mode", it)) }
        if (!binary.isNullOrBlank()) addAll(listOf("--psiphon-bin", binary))
    }
}

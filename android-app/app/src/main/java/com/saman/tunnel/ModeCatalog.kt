package com.saman.tunnel

/** One list drives the picker, labels, widget badge and legacy-id migration. Pure Kotlin so kotlinc can check it. */
data class ModeInfo(
    val id: String,
    val label: String,
    val badge: String,
    val short: String,
    val group: String,
    val color: Int,
    val fa: String,
    val en: String
)

object ModeCatalog {
    private const val MASQUE = 0xFF3A8DFF.toInt()
    private const val WG = 0xFF2FC27A.toInt()
    private const val GOOL = 0xFF8B6CFF.toInt()
    private const val PSI = 0xFFE85D9B.toInt()
    private const val TOR = 0xFFFF9A3D.toInt()

    val all: List<ModeInfo> = listOf(
        ModeInfo("MASQUE_H3", "MASQUE H3", "MASQUE", "H3", "MASQUE", MASQUE,
            "پیش‌فرض سریع روی QUIC", "Fast default over QUIC"),
        ModeInfo("MASQUE_H2", "MASQUE H2", "MASQUE", "H2", "MASQUE", MASQUE,
            "برای شبکه‌هایی که QUIC را می‌بندند (TCP)", "For networks that block QUIC (TCP)"),
        ModeInfo("MIM_H3", "MiM H3", "MIM", "MiM", "MASQUE", MASQUE,
            "دو لایه MASQUE برای تغییر آدرس خروجی", "Two MASQUE layers to change the exit address"),
        ModeInfo("MIM_H2", "MiM H2", "MIM", "MiM", "MASQUE", MASQUE,
            "مثل MiM H3 ولی روی TCP", "Like MiM H3 but over TCP"),
        ModeInfo("WG", "WireGuard", "WG", "WG", "WireGuard", WG,
            "WireGuard ساده و سبک", "Plain, light WireGuard"),
        ModeInfo("GOOL", "GOOL", "GOOL", "GOOL", "GOOL", GOOL,
            "جدید: WireGuard داخل MASQUE با خروجی خارجی", "New: WireGuard inside MASQUE, foreign exit"),
        ModeInfo("GOOL_CLASSIC", "GOOL Classic", "GOOL", "GOOL", "GOOL", GOOL,
            "قدیمی: WireGuard داخل WireGuard", "Old: WireGuard inside WireGuard"),
        ModeInfo("PSIPHON", "Psiphon: in tunnel", "PSIPHON", "PSI", "Psiphon", PSI,
            "سایفون روی تونل وارپ؛ خروجی از سایفون", "Psiphon carried by the WARP tunnel; exit is Psiphon"),
        ModeInfo("PSIPHON_REVERSE", "Psiphon: reverse", "PSIPHON", "PSI", "Psiphon", PSI,
            "وارپ از راه سایفون وصل می‌شود؛ وقتی وارپ مستقیم بسته است", "WARP is reached through Psiphon; use when WARP is blocked"),
        ModeInfo("PSIPHON_ONLY", "Psiphon: only", "PSIPHON", "PSI", "Psiphon", PSI,
            "فقط سایفون، بدون وارپ؛ ساده‌ترین", "Psiphon alone, no WARP; simplest"),
        ModeInfo("TOR_ONLY", "Tor", "TOR", "TOR", "Tor", TOR,
            "فقط Tor", "Tor alone"),
        ModeInfo("TOR_INSIDE_MASQUE_H3", "MASQUE → Tor", "TOR", "TOR", "Tor", TOR,
            "اول MASQUE، بعد Tor؛ برای شبکه‌هایی که Tor را می‌بندند", "MASQUE first, then Tor; for networks that block Tor"),
        ModeInfo("TOR_REVERSE", "Tor → MASQUE", "TOR", "TOR", "Tor", TOR,
            "اول Tor، بعد MASQUE H2؛ شبکه وارپ را نمی‌بیند", "Tor first, then MASQUE H2; network never sees WARP")
    )

    /** Ids from older versions -> nearest mode still offered. Idempotent; unknown ids pass through. */
    fun canonical(raw: String): String = when (val id = raw.trim().uppercase()) {
        "MASQUE" -> "MASQUE_H3"
        "MIM" -> "MIM_H3"
        "TOR_INSIDE_MASQUE", "TOR_INSIDE_MASQUE_H2", "TOR_INSIDE_WG", "TOR_INSIDE_GOOL" -> "TOR_INSIDE_MASQUE_H3"
        "PSIPHON_CHAIN" -> "PSIPHON"
        else -> id
    }

    fun info(raw: String): ModeInfo? = canonical(raw).let { id -> all.firstOrNull { it.id == id } }
    fun label(raw: String): String = info(raw)?.label ?: raw.ifBlank { "Tunnel" }
    fun badge(raw: String): String = info(raw)?.badge ?: raw.ifBlank { "WG" }
    fun short(raw: String): String = info(raw)?.short ?: "WG"
    fun isPsiphon(raw: String): Boolean = canonical(raw).startsWith("PSIPHON")
}

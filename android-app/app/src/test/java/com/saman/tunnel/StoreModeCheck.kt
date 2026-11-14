package com.saman.tunnel

fun main() {
    check(ModeCatalog.info("XRAY")?.group == "Xray") { "Config mode must be in catalog" }
    check(runCatching { AetherArguments.forMode("XRAY") }.isFailure) { "Config mode must never fall back to WARP" }
    check(ModeCatalog.canonical("PSIPHON_CHAIN") == "PSIPHON")
    check(AetherArguments.forMode("GOOL_CLASSIC").first() == "--gool-classic")
    check(AetherArguments.forMode("PSIPHON_ONLY").first() == "--psiphon-only")
    check(AetherArguments.forMode("TOR_ONLY").first() == "--tor-only")
    println("PASS: config mode isolated from Aether; existing mode migrations/flags retained")
}

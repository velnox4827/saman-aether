package com.saman.tunnel

/** Also runnable with kotlinc, without an Android SDK or test framework. */
fun runAndroidReleaseCheck() {
    val h2 = AetherArguments.forMode("MIM_H2")
    val h3 = AetherArguments.forMode("MIM_H3")
    check("--mim" in h2 && "--h2" in h2) { "MIM H2 must select both official flags" }
    check("--mim" in h3 && "--h2" !in h3) { "MIM H3 must use QUIC" }
    check(AetherArguments.forMode("MIM") == h3) { "Legacy MIM must retain H3" }
    check(ConnectionStatus.modeLabel("MIM_H2") == "MiM H2")
    check(ConnectionStatus.modeLabel("MIM_H3") == "MiM H3")
    check(!ReleaseVersion.isTrustedReleaseUrl("https://user@github.com/velnox4827/saman-aether/releases/tag/v1.9.2")) { "Release URLs must reject userinfo" }
    check(!ReleaseVersion.isTrustedReleaseUrl("https://github.com/velnox4827/saman-aether/releases/../../other")) { "Release URLs must reject traversal" }
    check(ReleaseVersion.isTrustedReleaseUrl("https://github.com/velnox4827/saman-aether/releases/download/v1.9.2/Saman-Tunnel-v1.9.2-universal-arm.apk"))
    check(!ReleaseVersion.isTrustedReleaseUrl("https://github.com/velnox4827/saman-aether/releases/download/v1.9.2/Other.apk"))
    check(!ReleaseVersion.isTrustedReleaseUrl("https://github.com/velnox4827/saman-aether/releases/download/v1.9.2/Saman-Tunnel-v1.9.2-universal-arm.apk?token=x"))
    runModeCheck()
    println("AndroidReleaseCheck: PASS")
}

fun main() = runAndroidReleaseCheck()

/** Modes, migration and Psiphon flag mapping. Runs under kotlinc and JUnit alike. */
fun runModeCheck() {
    // every offered mode builds args and has a label
    for (m in ModeCatalog.all) {
        check(AetherArguments.forMode(m.id).isNotEmpty() && ModeCatalog.label(m.id) == m.label) { "bad mode ${m.id}" }
        check(m.fa.isNotBlank() && m.en.isNotBlank()) { "missing description ${m.id}" }
    }
    // removed modes migrate to a mode that still exists
    for (old in listOf("MASQUE", "MIM", "TOR_INSIDE_MASQUE", "TOR_INSIDE_MASQUE_H2", "TOR_INSIDE_WG", "TOR_INSIDE_GOOL", "PSIPHON_CHAIN", "psiphon_only", " wg ")) {
        check(ModeCatalog.all.any { it.id == ModeCatalog.canonical(old) }) { "$old not migrated" }
    }
    check(ModeCatalog.canonical("TOR_INSIDE_GOOL") == "TOR_INSIDE_MASQUE_H3")
    // GOOL new vs classic vs WG stay distinct
    check("--gool" in AetherArguments.forMode("GOOL") && "--gool-classic" !in AetherArguments.forMode("GOOL"))
    check("--gool-classic" in AetherArguments.forMode("GOOL_CLASSIC") && "--gool" !in AetherArguments.forMode("GOOL_CLASSIC"))
    check("--wg" in AetherArguments.forMode("WG") && "--gool" !in AetherArguments.forMode("WG"))
    // three Psiphon shapes, each with the official flag, app ports 1819/1820 never double-bound
    val chain = AetherArguments.forMode("PSIPHON", "/bin/p", "de", "cdn")
    check("--psiphon" in chain && "--psiphon-reverse" !in chain && "--psiphon-only" !in chain)
    check(chain[chain.indexOf("--bind") + 1] == "127.0.0.1:1821" && chain[chain.indexOf("--psiphon-bind") + 1] == "127.0.0.1:1819")
    check(chain[chain.indexOf("--psiphon-region") + 1] == "DE" && chain[chain.indexOf("--psiphon-mode") + 1] == "cdn")
    check(chain[chain.indexOf("--psiphon-bin") + 1] == "/bin/p")
    val rev = AetherArguments.forMode("PSIPHON_REVERSE")
    check("--psiphon-reverse" in rev && rev[rev.indexOf("--bind") + 1] == "127.0.0.1:1819" && "--h2" in rev && "--wg" !in rev)
    val only = AetherArguments.forMode("PSIPHON_ONLY")
    check("--psiphon-only" in only && only[only.indexOf("--bind") + 1] == "127.0.0.1:1819")
    // auto adds nothing; bad region dropped
    val auto = AetherArguments.forMode("PSIPHON_ONLY", null, "Germany", "auto")
    check("--psiphon-region" !in auto && "--psiphon-mode" !in auto)
    check(AetherArguments.needsPsiphonBinary("PSIPHON_REVERSE") && !AetherArguments.needsPsiphonBinary("WG"))
}

package com.saman.tunnel

/** Also runnable with kotlinc, without an Android SDK or test framework. */
fun runAndroidReleaseCheck() {
    val h2 = AetherArguments.forMode("MIM_H2")
    val h3 = AetherArguments.forMode("MIM_H3")
    check("--mim" in h2 && "--h2" in h2) { "MIM H2 must select both official flags" }
    check("--mim" in h3 && "--h2" !in h3) { "MIM H3 must use QUIC" }
    check(AetherArguments.forMode("MIM") == h3) { "Legacy MIM must retain H3" }
    check(ConnectionStatus.modeLabel("MIM_H2") == "MASQUE-in-MASQUE H2")
    check(ConnectionStatus.modeLabel("MIM_H3") == "MASQUE-in-MASQUE H3")
    check(!ReleaseVersion.isTrustedReleaseUrl("https://user@github.com/velnox4827/saman-aether/releases/tag/v1.9.2")) { "Release URLs must reject userinfo" }
    check(!ReleaseVersion.isTrustedReleaseUrl("https://github.com/velnox4827/saman-aether/releases/../../other")) { "Release URLs must reject traversal" }
    check(ReleaseVersion.isTrustedReleaseUrl("https://github.com/velnox4827/saman-aether/releases/download/v1.9.2/Saman-Tunnel-v1.9.2-universal-arm.apk"))
    check(!ReleaseVersion.isTrustedReleaseUrl("https://github.com/velnox4827/saman-aether/releases/download/v1.9.2/Other.apk"))
    check(!ReleaseVersion.isTrustedReleaseUrl("https://github.com/velnox4827/saman-aether/releases/download/v1.9.2/Saman-Tunnel-v1.9.2-universal-arm.apk?token=x"))
    println("AndroidReleaseCheck: PASS")
}

fun main() = runAndroidReleaseCheck()

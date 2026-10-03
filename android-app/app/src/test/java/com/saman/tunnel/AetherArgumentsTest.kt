package com.saman.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherArgumentsTest {
    @Test
    fun androidReleaseModesAndDownloadUrls() = runAndroidReleaseCheck()

    @Test
    fun wireGuardChooserKeepsGoolAndWireGuardDistinct() {
        assertEquals("WG", AetherArguments.canonicalMode("WG"))
        assertEquals("GOOL", AetherArguments.canonicalMode("GOOL"))
        assertEquals("TOR_INSIDE_GOOL", AetherArguments.canonicalMode("TOR_INSIDE_GOOL"))
        assertTrue(AetherArguments.forMode("WG").contains("--wg"))
        assertTrue(AetherArguments.forMode("GOOL").contains("--gool"))
        assertTrue(AetherArguments.forMode("GOOL").none { it == "--wg" })
    }

    @Test
    fun wireGuardModeUsesOfficialAetherFlag() {
        assertTrue(AetherArguments.forMode("WG").contains("--wg"))
        assertTrue(AetherArguments.forMode("WG").none { it == "--gool" })
    }

    @Test
    fun psiphonModeKeepsAppSocksSeparateFromPsiphonListener() {
        val args = AetherArguments.forMode("PSIPHON_ONLY", "/native/libpsiphon-tunnel-core.so")
        assertEquals(1819, AetherArguments.APP_SOCKS_PORT)
        assertEquals(1820, AetherArguments.APP_HTTP_PORT)
        assertTrue(args.containsAll(listOf(
            "--psiphon", "--masque", "--h2", "--bind", "127.0.0.1:1819",
            "--psiphon-bind", "127.0.0.1:1821", "--psiphon-http", "127.0.0.1:1822",
            "--http-proxy", "127.0.0.1:1820", "--psiphon-bin", "/native/libpsiphon-tunnel-core.so"
        )))
        assertEquals("127.0.0.1:${AetherArguments.APP_SOCKS_PORT}", args[args.indexOf("--bind") + 1])
        assertEquals("127.0.0.1:1821", args[args.indexOf("--psiphon-bind") + 1])
        assertTrue("--psiphon-only" !in args)
        assertTrue("--upstream" !in args)
        assertTrue(AetherArguments.needsPsiphonBinary("psiphon_only"))
        assertTrue(!AetherArguments.needsPsiphonBinary("WG"))
        assertTrue(AetherArguments.forMode("PSIPHON_ONLY").none { it == "--psiphon-bin" })
    }

    @Test
    fun psiphonArchiveSupportsOnlyVerifiedAndroidAbis() {
        assertEquals("psiphon/arm64-v8a/psiphon-tunnel-core", psiphonAssetPath("arm64-v8a"))
        assertEquals("psiphon/armeabi-v7a/psiphon-tunnel-core", psiphonAssetPath("armeabi-v7a"))
        assertEquals(null, psiphonAssetPath("x86_64"))
        assertEquals(64, psiphonExpectedSha256("arm64-v8a")?.length)
        assertEquals(64, psiphonExpectedSha256("armeabi-v7a")?.length)
    }

    @Test
    fun torModesUseOnlyOfficialV2Flags() {
        assertEquals(
            listOf("--tor-only", "--bind", "127.0.0.1:1819"),
            AetherArguments.forMode("TOR_ONLY")
        )
        assertTrue(AetherArguments.forMode("TOR_INSIDE_WG").containsAll(listOf("--wg", "--tor")))
        assertTrue(AetherArguments.forMode("TOR_REVERSE").containsAll(listOf("--masque", "--h2", "--tor-reverse")))
    }

    @Test
    fun torReverseDoesNotSelectUdpTransport() {
        val args = AetherArguments.forMode("TOR_REVERSE")
        assertTrue("--wg" !in args)
        assertTrue("--gool" !in args)
        assertTrue("--mim" !in args)
    }
}

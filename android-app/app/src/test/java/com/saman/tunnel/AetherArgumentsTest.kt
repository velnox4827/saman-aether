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
    fun psiphonOnlyUsesOfficialAetherFlagAndSharedLocalProxyPorts() {
        val args = AetherArguments.forMode("PSIPHON_ONLY", "/native/libpsiphon-tunnel-core.so")
        assertTrue(args.containsAll(listOf(
            "--psiphon-only", "--bind", "127.0.0.1:1819", "--psiphon-http", "127.0.0.1:1820",
            "--psiphon-bin", "/native/libpsiphon-tunnel-core.so"
        )))
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

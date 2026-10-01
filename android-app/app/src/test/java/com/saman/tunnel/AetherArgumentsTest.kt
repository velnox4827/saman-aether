package com.saman.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AetherArgumentsTest {
    @Test
    fun androidReleaseModesAndDownloadUrls() = runAndroidReleaseCheck()

    @Test
    fun legacyGoolModesMigrateToWireGuardWithoutGoolArguments() {
        assertEquals("WG", AetherArguments.canonicalMode("GOOL"))
        assertEquals("TOR_INSIDE_WG", AetherArguments.canonicalMode("TOR_INSIDE_GOOL"))
        assertTrue(AetherArguments.forMode("GOOL").contains("--wg"))
        assertTrue(AetherArguments.forMode("TOR_INSIDE_GOOL").containsAll(listOf("--wg", "--tor")))
        assertTrue(AetherArguments.forMode("GOOL").none { it == "--gool" })
    }

    @Test
    fun wireGuardModeUsesOfficialAetherFlag() {
        assertTrue(AetherArguments.forMode("WG").contains("--wg"))
        assertTrue(AetherArguments.forMode("WG").none { it == "--gool" })
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

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
        assertEquals("TOR_INSIDE_MASQUE_H3", AetherArguments.canonicalMode("TOR_INSIDE_GOOL"))
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
    fun psiphonBinaryPinsExistForEveryShippedAbi() {
        assertEquals(64, PsiphonBinaryPolicy.expectedSha256("arm64-v8a")?.length)
        assertEquals(64, PsiphonBinaryPolicy.expectedSha256("armeabi-v7a")?.length)
        assertEquals(null, PsiphonBinaryPolicy.expectedSha256("x86_64"))
    }

    @Test
    fun modesAndPsiphonFlags() = runModeCheck()

    @Test
    fun torModesUseOnlyOfficialV2Flags() {
        assertEquals(
            listOf("--tor-only", "--bind", "127.0.0.1:1819"),
            AetherArguments.forMode("TOR_ONLY")
        )
        assertTrue(AetherArguments.forMode("TOR_INSIDE_MASQUE_H3").containsAll(listOf("--masque", "--tor")))
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

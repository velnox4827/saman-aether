package com.saman.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class EgressLookupTest {
    @Test
    fun parsesValidIpAndCountryAndBuildsFlag() {
        val result = EgressLookup.parse("""{"success":true,"ip":"203.0.113.8","country":"Example","country_code":"DE"}""")
        assertNotNull(result)
        assertEquals("203.0.113.8", result!!.ip)
        assertEquals("Example", result.country)
        assertEquals("🇩🇪", result.flag)
    }

    @Test
    fun rejectsInvalidIpOrUnsuccessfulProviderResponse() {
        assertNull(EgressLookup.parse("""{"success":false,"ip":"8.8.8.8","country":"Example","country_code":"US"}"""))
        assertNull(EgressLookup.parse("""{"success":true,"ip":"not-an-ip","country":"Example","country_code":"US"}"""))
        assertNull(EgressLookup.parse("""{"success":true,"ip":"256.1.1.1","country":"Example","country_code":"US"}"""))
        assertNull(EgressLookup.parse("""{"success":true,"ip":"2001:::1","country":"Example","country_code":"US"}"""))
        assertEquals("🌐", EgressLookup.parse("""{"success":true,"ip":"::ffff:192.0.2.1"}""")?.flag)
        assertNull(EgressLookup.parse("not-json"))
    }

    @Test
    fun missingCountryKeepsIpAndUsesPlaceholder() {
        val result = EgressLookup.parse("""{"success":true,"ip":"203.0.113.8"}""")
        assertEquals("203.0.113.8", result?.ip)
        assertEquals("—", result?.country)
        assertEquals("🌐", result?.flag)
    }

    @Test
    fun retryBackoffIsBoundedAndResetsOnSuccess() {
        val retry = EgressRetryPolicy()
        assertEquals(15 * 60_000L, retry.delayMillis())
        retry.failed()
        assertEquals(30_000L, retry.delayMillis())
        repeat(10) { retry.failed() }
        assertEquals(30 * 60_000L, retry.delayMillis())
        retry.succeeded()
        assertEquals(15 * 60_000L, retry.delayMillis())
    }

    @Test
    fun invalidCountryCodeUsesGlobeFallback() {
        val result = EgressLookup.parse("""{"success":true,"ip":"2001:4860:4860::8888","country":"Example","country_code":"ZZZ"}""")
        assertEquals("🌐", result?.flag)
        assertEquals("Example", result?.country)
    }
}

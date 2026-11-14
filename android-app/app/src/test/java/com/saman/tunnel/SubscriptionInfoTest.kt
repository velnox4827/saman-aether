package com.saman.tunnel

import org.junit.Assert.*
import org.junit.Test

class SubscriptionInfoTest {
    @Test fun missingIsUnknownAndFieldsAreBytesAndUnixSeconds() {
        assertNull(SubscriptionInfo.parse(null))
        assertNull(SubscriptionInfo.parse(" "))
        assertNull(SubscriptionInfo.parse("custom=value"))
        val info = SubscriptionInfo.parse("upload=12; download=34; total=100; expire=1790000000")!!
        assertEquals(12L, info.upload)
        assertEquals(34L, info.download)
        assertEquals(100L, info.total)
        assertEquals(1790000000L, info.expire)
        val partial = SubscriptionInfo.parse("download=0")!!
        assertEquals(0L, partial.download)
        assertNull(partial.upload)
        assertNull(partial.total)
        assertNull(partial.expire)
        assertEquals(Long.MAX_VALUE, SubscriptionInfo.parse("total=9223372036854775807")!!.total)
    }
    @Test fun malformedNegativeAndOverflowAreExplicit() {
        for (header in listOf("upload=-1", "download=1.5", "total=9223372036854775808", "expire=1790000000000", "upload=1;upload=2", "upload=", "total=+1", "upload=secret", "download")) {
            try { SubscriptionInfo.parse(header); fail("accepted malformed header") } catch (e: IllegalArgumentException) {
                assertFalse(e.message.orEmpty().contains("secret"))
                assertNull(e.cause)
            }
        }
    }
}

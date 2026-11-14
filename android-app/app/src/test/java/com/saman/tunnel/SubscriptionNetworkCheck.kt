package com.saman.tunnel

import java.io.ByteArrayInputStream

object SubscriptionNetworkCheck {
    @JvmStatic fun main(args: Array<String>) {
        check(SubscriptionFetcher.validateUrl("https://example.org/sub?token=test").protocol == "https")
        listOf("http://example.org/sub", "https://user:pass@example.org/sub", "file:///tmp/sub", "https://example.org/sub#secret", "https:///sub").forEach {
            check(runCatching { SubscriptionFetcher.validateUrl(it) }.isFailure)
        }
        check(SubscriptionFetcher.readBounded(ByteArrayInputStream("test".toByteArray()), 4, System.nanoTime() + 1_000_000_000) == "test")
        check(runCatching { SubscriptionFetcher.readBounded(ByteArrayInputStream(ByteArray(5)), 4, System.nanoTime() + 1_000_000_000) }.isFailure)
        check(runCatching { SubscriptionFetcher.readBounded(ByteArrayInputStream(ByteArray(1)), 4, 0) }.isFailure)
        val unknown = SubscriptionUsage.from(null, 100)
        check(unknown.consumedBytes == null && unknown.remainingBytes == null && unknown.daysRemaining == null)
        val zero = SubscriptionUsage.from(SubscriptionInfo(upload=0, download=0, total=0, expire=0), 100)
        check(zero.remainingBytes.toString() == "0" && zero.daysRemaining == null)
        val usage = SubscriptionUsage.from(SubscriptionInfo(upload=Long.MAX_VALUE, download=Long.MAX_VALUE, total=Long.MAX_VALUE, expire=101), 100)
        check(usage.consumedBytes.toString() == "18446744073709551614")
        check(usage.remainingBytes.toString() == "0" && usage.daysRemaining == 1L)
        println("Subscription network/usage boundary checks passed")
    }
}

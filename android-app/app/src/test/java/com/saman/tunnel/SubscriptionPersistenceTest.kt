package com.saman.tunnel

import org.junit.Test

class SubscriptionPersistenceTest {
    @Test fun atomicDocumentReplacementAndMigration() = SubscriptionStoreCheck.main(emptyArray())
    @Test fun networkBoundariesAndUnknownUsage() = SubscriptionNetworkCheck.main(emptyArray())
}

package com.saman.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnHealthPolicyTest {
    @Test fun endpointRescanOutlastsTheOldWatchdogAndRecovers() {
        val policy = VpnHealthPolicy()
        // The supplied log lost SOCKS5 while the native job was still scanning.
        // Even a two-minute rescan must not tear down TUN or cancel that job.
        repeat(60) { index ->
            val state = policy.observe(coreAlive = true, workerAlive = true, proxyReady = false)
            assertTrue(state == VpnHealthPolicy.State.CONNECTED || state == VpnHealthPolicy.State.RECONNECTING)
            if (index >= 2) assertEquals(VpnHealthPolicy.State.RECONNECTING, state)
        }
        assertEquals(VpnHealthPolicy.State.CONNECTED, policy.observe(true, true, true))
    }

    @Test fun losingCoreDuringReconnectRequiresCleanup() {
        val policy = VpnHealthPolicy()
        repeat(4) { policy.observe(true, true, false) }
        assertEquals(VpnHealthPolicy.State.CORE_STOPPED, policy.observe(false, true, false))
        // An unrelated listener on the old port cannot conceal core death.
        assertEquals(VpnHealthPolicy.State.CORE_STOPPED, policy.observe(false, true, true))
    }

    @Test fun workerExitIsNotMistakenForEndpointReconnect() {
        val policy = VpnHealthPolicy()
        assertEquals(VpnHealthPolicy.State.WORKER_STOPPED, policy.observe(true, false, true))
    }

    @Test fun isolatedProbeMissesResetOnRecovery() {
        val policy = VpnHealthPolicy()
        repeat(5) {
            repeat(2) { assertEquals(VpnHealthPolicy.State.CONNECTED, policy.observe(true, true, false)) }
            assertEquals(VpnHealthPolicy.State.CONNECTED, policy.observe(true, true, true))
        }
    }
}

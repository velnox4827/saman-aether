package com.saman.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConnectionStatusTest {
    private val proxy = "Connected — SOCKS5 :1819 + HTTP :1820"

    @Test fun proxyReadinessDoesNotClaimDeviceVpnConnectivity() {
        val waiting = ConnectionStatus.display(proxy, true, false, "Waiting for Aether SOCKS5")
        assertEquals(TunnelPhase.CONNECTING, TunnelPhase.fromStatus(waiting))
        assertFalse(waiting.startsWith("Connected"))
        assertEquals(proxy, ConnectionStatus.display(proxy, false, false, "Stopped"))
        assertEquals("Connected — VPN", ConnectionStatus.display(proxy, true, true, "GOOL VPN connected"))
    }

    @Test fun failedOrRevokedVpnRemainsVisibleWithAHealthyProxy() {
        assertEquals("Error: HEV worker exited", ConnectionStatus.display(proxy, true, false, "Error: HEV worker exited"))
        assertEquals("Error: HEV worker exited", ConnectionStatus.display("Stopped", true, false, "Error: HEV worker exited"))
        assertEquals(TunnelPhase.FAILED, TunnelPhase.fromStatus(
            ConnectionStatus.display(proxy, true, false, "Permission revoked")))
        assertEquals(TunnelPhase.FAILED, TunnelPhase.fromStatus(
            ConnectionStatus.display(proxy, true, false, "Stopped")))
    }

    @Test fun staleVpnFlagCannotOverrideCoreStopOrFailure() {
        for (core in listOf("Stopping…", "Stopped", "Error: Port busy", "Connection unstable — checking SOCKS5")) {
            assertEquals(core, ConnectionStatus.display(core, true, true, "GOOL VPN connected"))
        }
    }

    @Test fun transportLabelsDistinguishMasqueProtocols() {
        assertEquals("MASQUE H2", ConnectionStatus.modeLabel("MASQUE_H2"))
        assertEquals("MASQUE H3", ConnectionStatus.modeLabel("MASQUE_H3"))
        assertEquals("GOOL", ConnectionStatus.modeLabel("GOOL"))
    }

    @Test fun retainedTunDuringReconnectDoesNotClaimConnectivity() {
        val reconnecting = "Connecting — WG VPN reconnecting"
        assertEquals(reconnecting, ConnectionStatus.display(proxy, true, true, reconnecting))
        val coreReconnect = "Connecting — WG reconnecting"
        assertEquals(coreReconnect, ConnectionStatus.display(coreReconnect, true, true, "WG VPN connected"))
        assertEquals("Connected — VPN", ConnectionStatus.display(proxy, true, true, "WG VPN connected"))
    }
}

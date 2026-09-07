package com.saman.tunnel

/** Combines the core and device VPN states without treating a proxy as a VPN. */
object ConnectionStatus {
    fun display(core: String, vpnSelected: Boolean, vpnRunning: Boolean, vpn: String): String {
        if (!vpnSelected) return core
        val corePhase = TunnelPhase.fromStatus(core)
        if (corePhase == TunnelPhase.STOPPED && !vpnRunning) {
            if (vpn.startsWith("Error", true)) return vpn
            if (vpn.startsWith("Permission", true)) return "Error: $vpn"
        }
        if (corePhase != TunnelPhase.CONNECTED) return core
        if (vpnRunning) {
            return if (TunnelPhase.fromStatus(vpn) == TunnelPhase.CONNECTING) vpn
            else "Connected — VPN"
        }
        return when {
            vpn.startsWith("Error", true) -> vpn
            vpn.startsWith("Permission", true) -> "Error: $vpn"
            vpn.startsWith("Stopped", true) -> "Error: VPN stopped — tap the mode to retry"
            else -> "Connecting — ${vpn.ifBlank { "Preparing VPN" }}"
        }
    }

    fun modeLabel(mode: String): String = when (mode.uppercase()) {
        "MASQUE_H2" -> "MASQUE H2"
        "MASQUE_H3", "MASQUE" -> "MASQUE H3"
        else -> mode.ifBlank { "Tunnel" }
    }
}

package com.saman.tunnel

/** Distinguish an endpoint reconnect from the loss of a VPN component. */
class VpnHealthPolicy {
    enum class State { CONNECTED, RECONNECTING, CORE_STOPPED, WORKER_STOPPED }

    private var misses = 0

    fun observe(coreAlive: Boolean, workerAlive: Boolean, proxyReady: Boolean): State {
        if (!coreAlive) return State.CORE_STOPPED
        if (!workerAlive) return State.WORKER_STOPPED
        misses = if (proxyReady) 0 else (misses + 1).coerceAtMost(3)
        // Three misses change the presentation, never cancel Aether's retry loop.
        return if (misses >= 3) State.RECONNECTING else State.CONNECTED
    }
}

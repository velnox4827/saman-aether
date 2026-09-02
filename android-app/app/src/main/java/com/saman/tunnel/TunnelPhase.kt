package com.saman.tunnel

enum class TunnelPhase {
    STOPPED,
    STARTING,
    CONNECTING,
    CONNECTED,
    DEGRADED,
    STOPPING,
    FAILED;

    val isBusy: Boolean
        get() = this == STARTING || this == CONNECTING || this == STOPPING

    val isActive: Boolean
        get() = this == STARTING || this == CONNECTING || this == CONNECTED || this == DEGRADED || this == STOPPING

    val shouldPreserveOnServiceDestroy: Boolean
        get() = this == FAILED

    companion object {
        fun fromStatus(status: String): TunnelPhase {
            val normalized = status.trim().lowercase()
            return when {
                normalized.startsWith("connection unstable") -> DEGRADED
                normalized.startsWith("connected") -> CONNECTED
                normalized.startsWith("connecting") -> CONNECTING
                normalized.startsWith("starting") || normalized.startsWith("switching") -> STARTING
                normalized.startsWith("stopping") -> STOPPING
                normalized.startsWith("error") || normalized.startsWith("failed") -> FAILED
                else -> STOPPED
            }
        }
    }
}

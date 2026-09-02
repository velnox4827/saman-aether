package com.saman.tunnel

object LogRotationPolicy {
    const val MAX_CORE_BYTES: Long = 2L * 1024 * 1024
    const val MAX_APP_BYTES: Long = 1024 * 1024

    fun shouldRotate(sizeBytes: Long, limitBytes: Long = MAX_CORE_BYTES): Boolean =
        sizeBytes > limitBytes
}

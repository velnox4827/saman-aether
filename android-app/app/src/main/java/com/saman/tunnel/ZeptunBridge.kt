package com.saman.tunnel

object ZeptunBridge {
    init { System.loadLibrary("zeptun-jni") }

    private external fun nativeStart(service: Any?, fd: Int, config: String?): Int
    private external fun nativeStop()
    private external fun nativeVersion(): String
    private external fun nativeCounter(index: Int): Long

    fun start(service: Any?, fd: Int, config: String?): Int = nativeStart(service, fd, config)
    fun stop() = nativeStop()
    fun version(): String = nativeVersion()
    fun isRunning(): Boolean = nativeCounter(0) >= 0L
}

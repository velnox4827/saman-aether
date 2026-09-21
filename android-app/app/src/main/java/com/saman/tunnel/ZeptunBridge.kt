package com.saman.tunnel

object ZeptunBridge {
    init { System.loadLibrary("zeptun-jni") }

    external fun start(service: Any?, fd: Int, config: String?): Int
    external fun stop()
    external fun version(): String
    external fun counter(index: Int): Long

    fun isRunning(): Boolean = counter(0) >= 0L
}

package com.saman.tunnel

object HevTunnel {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    private external fun TProxyStartService(configPath: String, fd: Int): Boolean

    @JvmStatic
    private external fun TProxyStopService(): Boolean

    @JvmStatic
    private external fun TProxyIsRunning(): Boolean

    @JvmStatic
    private external fun TProxyGetStats(): LongArray?

    @Synchronized
    fun start(configPath: String, fd: Int): Boolean =
        TProxyStartService(configPath, fd)

    @Synchronized
    fun stop(): Boolean =
        runCatching { TProxyStopService() }.getOrDefault(false)

    fun isRunning(): Boolean =
        runCatching { TProxyIsRunning() }.getOrDefault(false)

    fun stats(): LongArray =
        runCatching { TProxyGetStats() ?: longArrayOf(0, 0, 0, 0) }
            .getOrDefault(longArrayOf(0, 0, 0, 0))
}

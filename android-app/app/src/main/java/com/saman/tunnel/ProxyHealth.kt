package com.saman.tunnel

import java.net.InetSocketAddress
import java.net.Socket

object ProxyHealth {
    fun probeSocks5(host: String, port: Int, timeoutMs: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            socket.getOutputStream().apply {
                write(byteArrayOf(0x05, 0x01, 0x00))
                flush()
            }
            val reply = ByteArray(2)
            var offset = 0
            while (offset < reply.size) {
                val count = socket.getInputStream().read(reply, offset, reply.size - offset)
                if (count < 0) return@runCatching false
                offset += count
            }
            reply[0] == 0x05.toByte() && reply[1] == 0x00.toByte()
        }
    }.getOrDefault(false)
}

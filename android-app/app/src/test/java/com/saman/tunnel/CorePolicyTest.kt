package com.saman.tunnel

import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePolicyTest {
    @Test
    fun tunnelPhaseMapsStableStates() {
        assertTrue(TunnelPhase.STARTING.isBusy)
        assertTrue(TunnelPhase.CONNECTED.isActive)
        assertFalse(TunnelPhase.FAILED.isActive)
        assertTrue(TunnelPhase.FAILED.shouldPreserveOnServiceDestroy)
        assertFalse(TunnelPhase.CONNECTED.shouldPreserveOnServiceDestroy)
        assertTrue(TunnelPhase.fromStatus("Connection unstable — SOCKS5") == TunnelPhase.DEGRADED)
        assertTrue(TunnelPhase.fromStatus("Connected — SOCKS5 + HTTP") == TunnelPhase.CONNECTED)
        assertTrue(TunnelPhase.fromStatus("Error: core stopped") == TunnelPhase.FAILED)
    }

    @Test
    fun releaseSelectionRejectsForeignStreamsAndHosts() {
        assertTrue(ReleaseVersion.compare("1.5.0", "1.4.0") > 0)
        assertTrue(ReleaseVersion.compare("1.5.0-rc.1", "1.5.0") < 0)
        assertTrue(ReleaseVersion.isAndroidReleaseTag("v1.5.0"))
        assertFalse(ReleaseVersion.isAndroidReleaseTag("termux-v1.5.0"))
        assertTrue(ReleaseVersion.isTrustedReleaseUrl("https://github.com/velnox4827/saman-aether/releases/tag/v1.5.0"))
        assertFalse(ReleaseVersion.isTrustedReleaseUrl("https://example.com/v1.5.0"))
    }

    @Test
    fun diagnosticsRedactsSensitiveFields() {
        val sensitive = "Authorization: Bearer abcdefghijklmnop token=secret123 api_key: header-secret {\"password\":\"json-secret\"} device=123e4567-e89b-12d3-a456-426614174000 ipv6=2001:db8::1 https://u:p@example.com/x?key=value"
        val sanitized = DiagnosticsSanitizer.sanitize(sensitive)
        assertFalse(sanitized.contains("abcdefghijklmnop"))
        assertFalse(sanitized.contains("secret123"))
        assertFalse(sanitized.contains("header-secret"))
        assertFalse(sanitized.contains("json-secret"))
        assertFalse(sanitized.contains("123e4567"))
        assertFalse(sanitized.contains("2001:db8"))
        assertFalse(sanitized.contains("u:p@"))
        assertFalse(sanitized.contains("key=value"))
    }

    @Test
    fun socksProbeRequiresProtocolGreeting() {
        ServerSocket(0).use { server ->
            val responder = thread {
                server.accept().use { socket ->
                    val request = ByteArray(3)
                    socket.getInputStream().read(request)
                    socket.getOutputStream().write(byteArrayOf(5, 0))
                    socket.getOutputStream().flush()
                }
            }
            assertTrue(ProxyHealth.probeSocks5("127.0.0.1", server.localPort, 2_000))
            responder.join()
        }
        assertFalse(ProxyHealth.probeSocks5("127.0.0.1", 1, 100))
    }
}

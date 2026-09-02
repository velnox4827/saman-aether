package com.saman.tunnel

import java.net.ServerSocket
import kotlin.concurrent.thread

private fun assertThat(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun main() {
    assertThat(TunnelPhase.STARTING.isBusy, "STARTING must be busy")
    assertThat(TunnelPhase.CONNECTED.isActive, "CONNECTED must be active")
    assertThat(!TunnelPhase.FAILED.isActive, "FAILED must not be active")
    assertThat(TunnelPhase.FAILED.shouldPreserveOnServiceDestroy, "FAILED must survive service teardown")
    assertThat(!TunnelPhase.CONNECTED.shouldPreserveOnServiceDestroy, "CONNECTED must not survive service teardown")
    assertThat(TunnelPhase.fromStatus("Connection unstable — SOCKS5") == TunnelPhase.DEGRADED, "unstable status must map to DEGRADED")
    assertThat(TunnelPhase.fromStatus("Connected — SOCKS5 + HTTP") == TunnelPhase.CONNECTED, "connected status must map to CONNECTED")
    assertThat(TunnelPhase.fromStatus("Error: core stopped") == TunnelPhase.FAILED, "errors must map to FAILED")

    assertThat(ReleaseVersion.compare("1.5.0", "1.4.0") > 0, "1.5.0 must be newer")
    assertThat(ReleaseVersion.compare("1.5.0-rc.1", "1.5.0") < 0, "release candidate must be older than stable")
    assertThat(ReleaseVersion.isAndroidReleaseTag("v1.5.0"), "stable Android tag must be accepted")
    assertThat(!ReleaseVersion.isAndroidReleaseTag("termux-v1.5.0"), "Termux tag must be rejected")
    assertThat(ReleaseVersion.isTrustedReleaseUrl("https://github.com/velnox4827/saman-aether/releases/tag/v1.5.0"), "canonical release URL must be trusted")
    assertThat(!ReleaseVersion.isTrustedReleaseUrl("https://example.com/v1.5.0"), "foreign release URL must be rejected")

    val sensitive = "Authorization: Bearer abcdefghijklmnop token=secret123 api_key: header-secret {\"password\":\"json-secret\"} device=123e4567-e89b-12d3-a456-426614174000 ipv6=2001:db8::1 https://u:p@example.com/x?key=value"
    val sanitized = DiagnosticsSanitizer.sanitize(sensitive)
    assertThat(!sanitized.contains("abcdefghijklmnop"), "bearer token must be redacted")
    assertThat(!sanitized.contains("secret123"), "token field must be redacted")
    assertThat(!sanitized.contains("header-secret"), "colon-delimited secret must be redacted")
    assertThat(!sanitized.contains("json-secret"), "JSON secret must be redacted")
    assertThat(!sanitized.contains("123e4567"), "device id must be redacted")
    assertThat(!sanitized.contains("2001:db8"), "IPv6 field must be redacted")
    assertThat(!sanitized.contains("u:p@"), "URL userinfo must be redacted")
    assertThat(!sanitized.contains("key=value"), "sensitive query value must be redacted")

    assertThat(LogRotationPolicy.shouldRotate(2L * 1024 * 1024 + 1), "oversized log must rotate")
    assertThat(!LogRotationPolicy.shouldRotate(1024), "small log must not rotate")

    ServerSocket(0).use { server ->
        val responder = thread {
            server.accept().use { socket ->
                val request = ByteArray(3)
                socket.getInputStream().read(request)
                socket.getOutputStream().write(byteArrayOf(5, 0))
                socket.getOutputStream().flush()
            }
        }
        assertThat(ProxyHealth.probeSocks5("127.0.0.1", server.localPort, 2_000), "valid SOCKS5 greeting must pass")
        responder.join()
    }
    assertThat(!ProxyHealth.probeSocks5("127.0.0.1", 1, 100), "closed port must fail")

    println("CorePolicyTestMain: PASS")
}

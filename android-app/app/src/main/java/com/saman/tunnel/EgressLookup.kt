package com.saman.tunnel

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

internal data class EgressInfo(val ip: String, val country: String, val countryCode: String, val flag: String)

internal object EgressLookup {
    private const val ENDPOINT = "https://ipwho.is/"
    private const val MAX_RESPONSE = 16 * 1024

    fun fetch(): EgressInfo {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", AetherArguments.APP_SOCKS_PORT))
        val connection = URL(ENDPOINT).openConnection(proxy) as HttpURLConnection
        try {
            connection.connectTimeout = 7_000
            connection.readTimeout = 7_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Saman-Tunnel")
            check(connection.responseCode in 200..299) { "Geolocation service unavailable" }
            val body = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(2048)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_RESPONSE) { "Geolocation response too large" }
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
            return parse(body) ?: error("Invalid geolocation response")
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(body: String): EgressInfo? = runCatching {
        val json = JSONObject(body)
        if (!json.optBoolean("success", false)) return null
        val ip = json.optString("ip").trim()
        val country = json.optString("country").trim().takeUnless { it.isBlank() || it == "null" } ?: "—"
        val code = json.optString("country_code").trim().uppercase()
        if (!isIpLiteral(ip)) return null
        EgressInfo(ip, country, code, if (country == "—") "🌐" else flagFor(code) ?: "🌐")
    }.getOrNull()

    internal fun flagFor(code: String): String? {
        if (!code.matches(Regex("[A-Z]{2}"))) return null
        return String(Character.toChars(0x1F1E6 + code[0].code - 'A'.code)) +
            String(Character.toChars(0x1F1E6 + code[1].code - 'A'.code))
    }

    private fun isIpLiteral(value: String): Boolean = when {
        value.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) ->
            value.split('.').all { it.toIntOrNull() in 0..255 }
        value.contains(':') -> runCatching {
            java.net.InetAddress.getByName(value)
            true
        }.getOrDefault(false)
        else -> false
    }
}

internal class EgressRetryPolicy {
    private var failures = 0
    fun succeeded() { failures = 0 }
    fun failed() { failures = (failures + 1).coerceAtMost(6) }
    fun delayMillis(): Long = if (failures == 0) 15 * 60_000L else (30_000L shl (failures - 1)).coerceAtMost(30 * 60_000L)
}

internal fun egressStatus(connected: Boolean, checking: Boolean, failed: Boolean): String = when {
    !connected -> "—"
    checking -> "در حال بررسی…"
    failed -> "در دسترس نیست"
    else -> "آماده"
}

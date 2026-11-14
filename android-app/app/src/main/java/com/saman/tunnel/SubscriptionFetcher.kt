package com.saman.tunnel

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.net.ssl.HttpsURLConnection

internal data class SubscriptionUsage(val consumedBytes: java.math.BigInteger?, val remainingBytes: java.math.BigInteger?, val daysRemaining: Long?) {
    companion object {
        fun from(info: SubscriptionInfo?, nowSeconds: Long): SubscriptionUsage {
            val used = if (info?.upload != null && info.download != null) java.math.BigInteger.valueOf(info.upload).add(java.math.BigInteger.valueOf(info.download)) else null
            val remaining = if (info?.total != null && used != null) java.math.BigInteger.valueOf(info.total).subtract(used).max(java.math.BigInteger.ZERO) else null
            val days = info?.expire?.takeIf { it > 0 }?.let { expire ->
                java.math.BigInteger.valueOf(expire).subtract(java.math.BigInteger.valueOf(nowSeconds)).max(java.math.BigInteger.ZERO)
                    .add(java.math.BigInteger.valueOf(86399)).divide(java.math.BigInteger.valueOf(86400)).min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).toLong()
            }
            return SubscriptionUsage(used, remaining, days)
        }
    }
}

object SubscriptionFetcher {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val TIMEOUT_MS = 20_000
    data class Result(val profiles: List<ImportedProfile>, val info: SubscriptionInfo?)

    fun validateUrl(text: String): URL {
        try {
            val uri = URI(text)
            require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawFragment == null && (uri.port == -1 || uri.port in 1..65535))
            return uri.toURL()
        } catch (_: Exception) { throw IllegalArgumentException("Use a valid HTTPS subscription URL without credentials or fragment") }
    }
    fun readBounded(input: InputStream, maxBytes: Int = MAX_BYTES, deadlineNanos: Long = System.nanoTime() + TIMEOUT_MS * 1_000_000L): String {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            require(!Thread.currentThread().isInterrupted && System.nanoTime() < deadlineNanos) { "Import timed out or cancelled" }
            val count = input.read(buffer)
            if (count < 0) break
            require(count <= maxBytes - bytes.size()) { "Import exceeds size limit" }
            bytes.write(buffer, 0, count)
        }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
    }
    fun fetch(text: String): Result {
        val url = validateUrl(text)
        val connection = url.openConnection() as HttpsURLConnection
        val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000L
        val watchdog = java.util.Timer("subscription-timeout", true)
        watchdog.schedule(object : java.util.TimerTask() { override fun run() { connection.disconnect() } }, TIMEOUT_MS.toLong())
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "text/plain, application/json")
            connection.setRequestProperty("Accept-Encoding", "identity")
            require(connection.responseCode == 200) { "Subscription server did not return HTTP 200; saved nodes unchanged" }
            require(connection.contentLengthLong <= MAX_BYTES) { "Subscription exceeds size limit" }
            val info = SubscriptionInfo.parse(connection.getHeaderField("Subscription-Userinfo"))
            val content = connection.inputStream.use { readBounded(it, MAX_BYTES, deadline) }
            val profiles = ConfigParser.subscription(content)
            require(profiles.isNotEmpty()) { "Subscription contains no nodes" }
            profiles.forEach { XrayConfig.build(it) }
            return Result(profiles, info)
        } catch (e: IllegalArgumentException) { throw e }
        catch (_: Exception) { throw IllegalArgumentException("Subscription download failed (network, encoding or TLS); saved nodes unchanged") }
        finally { watchdog.cancel(); connection.disconnect() }
    }
    fun refresh(store: ConfigStore, id: String, url: String) {
        val result = fetch(url)
        if (Thread.currentThread().isInterrupted) throw IllegalStateException("Refresh cancelled")
        store.replace(id, url, result.profiles, result.info)
    }
}

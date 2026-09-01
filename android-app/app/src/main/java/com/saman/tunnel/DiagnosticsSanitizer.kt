package com.saman.tunnel

object DiagnosticsSanitizer {
    private val bearer = Regex("(?i)(authorization\\s*:\\s*bearer\\s+)[^\\s]+")
    private val jsonSecret = Regex(
        "(?i)([\\\"'](?:token|secret|password|passwd|api[_-]?key|private[_-]?key)[\\\"']\\s*:\\s*[\\\"'])[^\\\"']*([\\\"'])"
    )
    private val namedSecret = Regex(
        "(?i)\\b(token|secret|password|passwd|api[_-]?key|private[_-]?key)(\\s*[:=]\\s*)[^\\s&,}]+"
    )
    private val deviceId = Regex("(?i)(device=)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val ipv6Field = Regex("(?i)(ipv6=)[0-9a-f:]+")
    private val urlUserInfo = Regex("(https?://)[^/@\\s]+@")
    private val sensitiveQuery = Regex("(?i)([?&](?:token|secret|password|passwd|key|api_key)=)[^&#\\s]+")

    fun sanitize(input: String): String = input
        .replace(bearer, "$1[REDACTED]")
        .replace(jsonSecret, "$1[REDACTED]$2")
        .replace(namedSecret, "$1$2[REDACTED]")
        .replace(deviceId, "$1[REDACTED]")
        .replace(ipv6Field, "$1[REDACTED]")
        .replace(urlUserInfo, "$1[REDACTED]@")
        .replace(sensitiveQuery, "$1[REDACTED]")
}

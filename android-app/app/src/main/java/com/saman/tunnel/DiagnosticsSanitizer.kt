package com.saman.tunnel

object DiagnosticsSanitizer {
    private val authorization = Regex("(?im)(authorization\\s*:\\s*\\S+\\s+)[^\\r\\n]+")
    private val cookie = Regex("(?im)((?:set-)?cookie\\s*:\\s*)[^\\r\\n]+")
    private val privateKey = Regex(
        "(?s)-----BEGIN [^-\\r\\n]*PRIVATE KEY-----.*?-----END [^-\\r\\n]*PRIVATE KEY-----"
    )
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
        .replace(authorization, "$1[REDACTED]")
        .replace(cookie, "$1[REDACTED]")
        .replace(privateKey, "[REDACTED PRIVATE KEY]")
        .replace(jsonSecret, "$1[REDACTED]$2")
        .replace(namedSecret, "$1$2[REDACTED]")
        .replace(deviceId, "$1[REDACTED]")
        .replace(ipv6Field, "$1[REDACTED]")
        .replace(urlUserInfo, "$1[REDACTED]@")
        .replace(sensitiveQuery, "$1[REDACTED]")
}

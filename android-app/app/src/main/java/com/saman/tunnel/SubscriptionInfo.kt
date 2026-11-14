package com.saman.tunnel

/** Traffic values are bytes; expiry is Unix seconds (0 through 9999-12-31). Null means unknown. */
data class SubscriptionInfo(
    val upload: Long? = null,
    val download: Long? = null,
    val total: Long? = null,
    val expire: Long? = null
) {
    companion object {
        fun parse(header: String?): SubscriptionInfo? {
            if (header.isNullOrBlank()) return null
            val fields = mutableMapOf<String, Long>()
            val message = "اطلاعات مصرف یا انقضای اشتراک نامعتبر است"
            require(header.length <= 8192) { message }
            header.split(';').map(String::trim).filter(String::isNotEmpty).forEach { item ->
                val parts = item.split('=', limit = 2)
                val key = parts[0].trim().lowercase()
                if (key in listOf("upload", "download", "total", "expire")) {
                    require(parts.size == 2 && key !in fields) { message }
                    val raw = parts[1].trim()
                    require(raw.matches(Regex("[0-9]+"))) { message }
                    val value = raw.toLongOrNull()
                    require(value != null && (key != "expire" || value <= 253402300799L)) { message }
                    fields[key] = value
                }
            }
            if (fields.isEmpty()) return null
            return SubscriptionInfo(fields["upload"], fields["download"], fields["total"], fields["expire"])
        }
    }
}

package com.saman.tunnel

import java.net.URI
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject

data class ImportedProfile(val name: String, val outbound: JSONObject)

object ConfigParser {
    fun parse(text: String): List<ImportedProfile> = subscription(text)

    fun subscription(text: String): List<ImportedProfile> {
        var value = text.trim().removePrefix("\uFEFF")
        require(value.isNotEmpty() && value.length <= 4 * 1024 * 1024) { "پیکربندی خالی یا بیش از حد بزرگ است" }
        if (value.startsWith("[Interface]", true) || value.startsWith("[Peer]", true) || value.startsWith('#'))
            return try { listOf(wireguard(value)) } catch (_: Exception) {
                throw IllegalArgumentException("پیکربندی WireGuard نامعتبر است؛ فقط یک همتا و گزینه‌های استاندارد پشتیبانی می‌شود")
            }
        if (!value.contains("://")) value = try { base64(value.filterNot(Char::isWhitespace)) } catch (_: Exception) {
            throw IllegalArgumentException("کدگذاری اشتراک نامعتبر است")
        }
        val lines = value.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(lines.isNotEmpty() && lines.size <= 10000) { "فهرست اشتراک نامعتبر است" }
        return lines.mapIndexed { index, line ->
            val protocol = line.substringBefore("://").lowercase()
            if (protocol in listOf("hysteria2", "hy2", "hysteria", "tuic", "wireguard", "wg"))
                throw IllegalArgumentException("گزینه ${index + 1}: این پروتکل پشتیبانی نمی‌شود")
            try { link(line) } catch (_: Exception) {
                val label = if (protocol in listOf("vless", "vmess", "trojan", "ss")) " ($protocol)" else ""
                throw IllegalArgumentException("گزینه ${index + 1}$label: پیکربندی نامعتبر یا پشتیبانی‌نشده است")
            }
        }
    }

    private fun link(text: String): ImportedProfile {
        if (text.startsWith("vmess://", true)) return vmess(text.substringAfter("://"))
        val rawUri = if (text.startsWith("ss://", true) && '@' !in text.substringBefore('#')) {
            val encoded = text.substringAfter("://").substringBefore('#')
            val decoded = base64(encoded)
            val credentials = decoded.substringBeforeLast('@')
            val endpoint = decoded.substringAfterLast('@')
            require(credentials != decoded)
            "ss://" + java.net.URLEncoder.encode(credentials, "UTF-8").replace("+", "%20") + "@" + endpoint +
                if ('#' in text) "#" + text.substringAfter('#') else ""
        } else text
        val uri = URI(rawUri)
        val protocol = uri.scheme?.lowercase()
        require(protocol in listOf("vless", "trojan", "ss"))
        val q = query(uri.rawQuery)
        require(q.keys.all { it in setOf("type", "security", "encryption", "flow", "sni", "serverName", "host", "path", "serviceName", "mode", "fp", "alpn", "pbk", "sid", "spx", "headerType", "allowInsecure", "insecure") })
        if (protocol != "vless") require("encryption" !in q && "flow" !in q)
        val address = uri.host?.removeSurrounding("[", "]") ?: error("")
        require(address.isNotBlank() && uri.port in 1..65535 && uri.rawPath.orEmpty() in listOf("", "/"))
        val auth = decode(uri.rawUserInfo ?: "")
        val server = JSONObject().put("address", address).put("port", uri.port)
        val settings: JSONObject
        val stream: JSONObject
        when (protocol) {
            "vless" -> {
                uuid(auth)
                require(q.getOrDefault("encryption", "none") == "none")
                val user = JSONObject().put("id", auth).put("encryption", "none")
                val flow = q["flow"].orEmpty()
                require(flow in listOf("", "xtls-rprx-vision", "xtls-rprx-vision-udp443"))
                if (flow.isNotEmpty()) {
                    require(q.getOrDefault("type", "tcp") == "tcp" && q["security"] in listOf("tls", "reality"))
                    user.put("flow", flow)
                }
                server.put("users", JSONArray().put(user))
                settings = JSONObject().put("vnext", JSONArray().put(server))
                stream = stream(q, address)
            }
            "trojan" -> {
                require(auth.isNotEmpty())
                server.put("password", auth)
                settings = JSONObject().put("servers", JSONArray().put(server))
                stream = stream(mapOf("security" to "tls") + q, address)
            }
            else -> {
                require(q.isEmpty()) // SIP002 plugins require an external process and are not supported.
                val credentials = if (':' in auth) auth else base64(auth)
                val method = credentials.substringBefore(':')
                val password = credentials.substringAfter(':', "")
                require(method in listOf("aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305"))
                require(password.isNotEmpty())
                server.put("method", method).put("password", password)
                settings = JSONObject().put("servers", JSONArray().put(server))
                stream = stream(emptyMap(), address)
            }
        }
        return ImportedProfile(decode(uri.rawFragment ?: protocol!!.uppercase()).ifBlank { protocol!!.uppercase() },
            JSONObject().put("protocol", if (protocol == "ss") "shadowsocks" else protocol)
                .put("settings", settings).put("streamSettings", stream))
    }

    private fun vmess(encoded: String): ImportedProfile {
        val json = JSONObject(base64(encoded))
        require(json.keys().asSequence().all { it in setOf("v", "ps", "add", "port", "id", "aid", "scy", "net", "type", "host", "path", "tls", "sni", "fp", "alpn", "allowInsecure", "serviceName", "mode") })
        require(json.optString("v", "2") == "2")
        val address = json.getString("add")
        require(address.isNotBlank() && address.none { it.isWhitespace() || it in "/?#@" })
        val port = json.get("port").toString().toInt()
        require(port in 1..65535)
        val id = json.getString("id")
        uuid(id)
        require(json.optString("aid", "0").toInt() == 0)
        val cipher = json.optString("scy", "auto").ifEmpty { "auto" }
        require(cipher in listOf("auto", "none", "zero", "aes-128-gcm", "chacha20-poly1305"))
        val q = mutableMapOf("type" to json.optString("net", "tcp"), "security" to json.optString("tls", "none").ifEmpty { "none" })
        for (key in listOf("host", "path", "sni", "fp", "alpn", "allowInsecure", "serviceName", "mode"))
            if (json.has(key)) q[key] = json.get(key).toString()
        if (json.has("type")) q["headerType"] = json.getString("type")
        if (q["type"] == "grpc" && !q.containsKey("serviceName")) q["serviceName"] = q["path"].orEmpty()
        val server = JSONObject().put("address", address).put("port", port).put("users", JSONArray().put(
            JSONObject().put("id", id).put("security", cipher).put("alterId", 0)))
        return ImportedProfile(json.optString("ps", "VMESS").ifBlank { "VMESS" }, JSONObject().put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(server))).put("streamSettings", stream(q, address)))
    }

    private fun wireguard(text: String): ImportedProfile {
        require(text.length <= 65536)
        val sections = mutableMapOf<String, MutableMap<String, String>>()
        var section = ""
        text.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isNotEmpty()) {
                if (line.startsWith('[')) {
                    require(line in listOf("[Interface]", "[Peer]"))
                    section = line
                    require(section !in sections)
                    sections[section] = mutableMapOf()
                } else {
                    val fields = sections[section] ?: error("")
                    val parts = line.split('=', limit = 2)
                    require(parts.size == 2)
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    val supported = if (section == "[Interface]") listOf("PrivateKey", "Address", "MTU", "DNS")
                        else listOf("PublicKey", "PresharedKey", "Endpoint", "AllowedIPs", "PersistentKeepalive")
                    require(key in supported && key !in fields && value.isNotEmpty())
                    fields[key] = value
                }
            }
        }
        val local = sections["[Interface]"] ?: error("")
        val remote = sections["[Peer]"] ?: error("")
        fun key(value: String?): String {
            require(value != null && value.matches(Regex("[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=")))
            require(value != "A".repeat(43) + "=")
            return value
        }
        fun csv(value: String) = value.split(',').map(String::trim).also { require(it.size <= 32 && it.all(String::isNotEmpty)) }
        fun cidr(value: String): String {
            val parts = value.split('/')
            require(parts.size == 2)
            ip(parts[0])
            val prefix = parts[1].toInt()
            require(prefix in 0..if (':' in parts[0]) 128 else 32)
            return parts[0]
        }
        val addresses = csv(local["Address"] ?: error("")).map { cidr(it) }
        val endpoint = remote["Endpoint"] ?: error("")
        val uri = URI("udp://$endpoint")
        require(uri.host != null && uri.port in 1..65535 && uri.rawUserInfo == null && uri.rawPath.orEmpty().isEmpty() && uri.rawQuery == null && uri.rawFragment == null)
        val allowed = csv(remote["AllowedIPs"] ?: error(""))
        allowed.forEach { cidr(it) }
        val peer = JSONObject().put("publicKey", key(remote["PublicKey"]))
            .put("endpoint", endpoint).put("allowedIPs", JSONArray(allowed))
        remote["PresharedKey"]?.let { peer.put("preSharedKey", key(it)) }
        remote["PersistentKeepalive"]?.let {
            require(it.matches(Regex("[0-9]+")) && it.toInt() in 0..65535)
            peer.put("keepAlive", it.toInt())
        }
        val settings = JSONObject().put("secretKey", key(local["PrivateKey"]))
            .put("address", JSONArray(addresses)).put("peers", JSONArray().put(peer)).put("noKernelTun", true)
        local["MTU"]?.let {
            require(it.matches(Regex("[0-9]+")) && it.toInt() in (if (addresses.any { a -> ':' in a }) 1280 else 576)..65535)
            settings.put("mtu", it.toInt())
        }
        local["DNS"]?.let {
            val dns = csv(it)
            dns.forEach { address -> ip(address) }
            settings.put("remoteDNS", JSONArray(dns))
        }
        return ImportedProfile("WireGuard", JSONObject().put("protocol", "wireguard").put("settings", settings))
    }

    private fun ip(value: String) {
        if (':' in value) {
            require(value.matches(Regex("[0-9a-fA-F:.]+")))
            require(java.net.InetAddress.getByName(value) is java.net.Inet6Address)
        } else {
            val octets = value.split('.')
            require(octets.size == 4 && octets.all { it.matches(Regex("[0-9]{1,3}")) && it.toInt() in 0..255 && (it.length == 1 || !it.startsWith('0')) })
        }
    }

    private fun uuid(value: String) {
        require(value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")))
    }

    // java.util.Base64 starts at Android 26; this decoder keeps the parser usable on Android 24.
    private fun base64(value: String): String {
        require(value.isNotEmpty() && value.matches(Regex("[A-Za-z0-9+/_-]+={0,2}")))
        val raw = value.trimEnd('=')
        require(raw.length % 4 != 1 && (value.length == raw.length || value.length % 4 == 0))
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val output = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        raw.forEach {
            val n = alphabet.indexOf(if (it == '-') '+' else if (it == '_') '/' else it)
            require(n >= 0)
            buffer = (buffer shl 6) or n
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output.write((buffer shr bits) and 255)
            }
        }
        require(bits == 0 || buffer and ((1 shl bits) - 1) == 0)
        return Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString()
    }

    private fun stream(q: Map<String, String>, address: String): JSONObject {
        val type = q.getOrDefault("type", "tcp")
        val security = q.getOrDefault("security", "none")
        require(type in listOf("tcp", "ws", "grpc", "xhttp", "httpupgrade"))
        if (type in listOf("ws", "xhttp", "httpupgrade")) require(q["path"] == null || q["path"]!!.startsWith('/'))
        if (type == "grpc") require(q["mode"] in listOf(null, "", "gun", "multi", "auto"))
        require(security in listOf("none", "tls", "reality"))
        require(q["allowInsecure"] in listOf(null, "0", "false") && q["insecure"] in listOf(null, "0", "false"))
        require(q["headerType"] in listOf(null, "", "none"))
        val result = JSONObject().put("network", type).put("security", security)
        if (security != "none") {
            val name = q["sni"] ?: q["serverName"] ?: address
            require(name.isNotBlank() && name.none { it.isWhitespace() || it in "/?#@" })
            val tls = JSONObject().put("serverName", name)
            q["fp"]?.let {
                require(it in listOf("chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random", "randomized"))
                tls.put("fingerprint", it)
            }
            q["alpn"]?.let {
                require(it.split(',').all { p -> p.isNotBlank() && p.none(Char::isWhitespace) })
                tls.put("alpn", JSONArray(it.split(',')))
            }
            if (security == "reality") {
                require(type in listOf("tcp", "grpc", "xhttp"))
                require(!q["sni"].isNullOrBlank() || !q["serverName"].isNullOrBlank())
                val key = q["pbk"] ?: error("")
                require(key.matches(Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]")))
                val sid = q["sid"].orEmpty()
                require(sid.length <= 16 && sid.length % 2 == 0 && sid.matches(Regex("[0-9a-fA-F]*")))
                tls.put("password", key).put("shortId", sid).put("fingerprint", q["fp"] ?: "chrome")
                q["spx"]?.let { tls.put("spiderX", it) }
            }
            result.put(if (security == "tls") "tlsSettings" else "realitySettings", tls)
        }
        when (type) {
            "ws" -> result.put("wsSettings", JSONObject().put("path", q["path"] ?: "/")
                .put("headers", JSONObject().put("Host", q["host"] ?: address)))
            "httpupgrade" -> result.put("httpupgradeSettings", JSONObject().put("path", q["path"] ?: "/").put("host", q["host"] ?: address))
            "grpc" -> result.put("grpcSettings", JSONObject().put("serviceName", q["serviceName"] ?: "").put("multiMode", q["mode"] == "multi"))
            "xhttp" -> {
                val mode = q["mode"] ?: "auto"
                require(mode in listOf("auto", "packet-up", "stream-up", "stream-one"))
                result.put("xhttpSettings", JSONObject().put("path", q["path"] ?: "/").put("host", q["host"] ?: address).put("mode", mode))
            }
        }
        return result
    }

    private fun query(raw: String?): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (raw.isNullOrEmpty()) return result
        raw.split('&').forEach {
            val parts = it.split('=', limit = 2)
            val key = decode(parts[0])
            require(key.isNotEmpty() && key !in result)
            result[key] = decode(parts.getOrElse(1) { "" })
        }
        return result
    }

    private fun decode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8").also {
        require('\uFFFD' !in it && it.none { c -> c == '\u0000' || c == '\r' || c == '\n' })
    }
}

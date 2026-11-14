package com.saman.tunnel

import org.junit.Assert.*
import org.junit.Test

class ConfigParserTest {
    private val id = "123e4567-e89b-12d3-a456-426614174000"
    private fun bad(text: String, marker: String? = null) {
        try { ConfigParser.parse(text); fail("accepted invalid input") } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().any { it in '\u0600'..'\u06ff' })
            assertFalse(e.message.orEmpty().contains(id))
            assertNull(e.cause)
            if (marker != null) assertTrue(e.message.orEmpty().contains(marker))
        }
    }
    @Test fun rejectsInvalidVlessWithoutLeakingSecrets() {
        for (tail in listOf("bad@example.org:443", "$id@example.org:0", "$id@example.org:65536", "$id@:443", "$id@example.org:443?security=xtls", "$id@example.org:443?type=quic", "$id@example.org:443?security=reality", "$id@example.org:443?encryption=bad", "$id@example.org:443?allowInsecure=1", "$id@example.org:443?flow=bad", "$id@example.org:443?type=tcp&type=ws")) bad("vless://$tail")
    }
    @Test fun supportedTransportAndReality() {
        for ((type, field) in mapOf("tcp" to "", "grpc" to "grpcSettings", "xhttp" to "xhttpSettings", "httpupgrade" to "httpupgradeSettings")) {
            val stream = ConfigParser.parse("vless://$id@example.org:443?type=$type&security=tls&serviceName=svc&path=%2Fa&host=cdn.example.org&mode=auto").single().outbound.getJSONObject("streamSettings")
            if (field.isNotEmpty()) assertTrue(stream.has(field))
        }
        val pbk = "A".repeat(43)
        val reality = ConfigParser.parse("vless://$id@example.org:443?security=reality&sni=cdn.example.org&fp=chrome&pbk=$pbk&sid=abcd&flow=xtls-rprx-vision").single().outbound.getJSONObject("streamSettings").getJSONObject("realitySettings")
        assertEquals(pbk, reality.getString("password"))
        assertEquals("abcd", reality.getString("shortId"))
        bad("vless://$id@example.org:443?security=reality&type=ws&sni=cdn.example.org&pbk=$pbk")
        bad("vless://$id@example.org:443?security=reality&sni=cdn.example.org&pbk=$pbk&sid=abc")
    }
    private fun b64(value: String) = java.util.Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    @Test fun subscriptionsPreserveUnicodeAndRejectPartialFailure() {
        val link = "vless://$id@example.org:443#سلام"
        assertEquals(listOf("سلام", "سلام"), ConfigParser.subscription(b64("$link\n$link")).map { it.name })
        assertEquals("سلام", ConfigParser.parse(b64(link)).single().name)
        bad("$link\nvless://invalid@example.org:443", "2")
        bad("hysteria2://secret@example.org:443", "پشتیبانی")
        bad("tuic://secret@example.org:443", "پشتیبانی")
        bad("[Interface]\nPrivateKey=secret")
        bad("%%%")
        bad(b64("not a link"))
    }
    @Test fun trojanAndSip002Shadowsocks() {
        val trojan = ConfigParser.parse("trojan://p%3Ass%2Bword@[2001:db8::1]:443?sni=cdn.example.org#T").single().outbound
        val server = trojan.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("p:ss+word", server.getString("password"))
        assertEquals("2001:db8::1", server.getString("address"))
        assertEquals("tls", trojan.getJSONObject("streamSettings").getString("security"))
        for (auth in listOf(b64("aes-256-gcm:رمز:pass"), "aes-256-gcm:%D8%B1%D9%85%D8%B2%3Apass")) {
            val ss = ConfigParser.parse("ss://$auth@example.org:8388#SS").single().outbound.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
            assertEquals("aes-256-gcm", ss.getString("method"))
            assertEquals("رمز:pass", ss.getString("password"))
        }
        bad("ss://${b64("bad:secret")}@example.org:8388")
        bad("ss://${b64("aes-256-gcm:secret")}@example.org:8388?plugin=v2ray-plugin")
        bad("trojan://@example.org:443")
    }
    @Test fun vmessBase64Json() {
        val json = org.json.JSONObject().put("v", "2").put("ps", "وی‌مس").put("add", "example.org").put("port", "443").put("id", id).put("aid", "0").put("scy", "auto").put("net", "ws").put("host", "cdn.example.org").put("path", "/ws").put("tls", "tls")
        val p = ConfigParser.parse("vmess://${b64(json.toString())}").single()
        assertEquals("وی‌مس", p.name)
        assertEquals("vmess", p.outbound.getString("protocol"))
        json.put("id", "bad-secret")
        bad("vmess://${b64(json.toString())}")
        json.put("id", id).put("aid", "64")
        bad("vmess://${b64(json.toString())}")
    }
    private val wgKey = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })
    private fun wireguard() = """
        [Interface]
        PrivateKey = $wgKey
        Address = 10.2.0.2/32, fd00::2/128
        DNS = 1.1.1.1, 2606:4700:4700::1111
        MTU = 1280
        [Peer]
        PublicKey = $wgKey
        PresharedKey = $wgKey
        Endpoint = [2001:db8::1]:51820
        AllowedIPs = 0.0.0.0/0, ::/0
        PersistentKeepalive = 25
    """.trimIndent()
    @Test fun wireguardIniMapsOnePeerAndRemoteDns() {
        val p = ConfigParser.parse(wireguard()).single()
        assertEquals("wireguard", p.outbound.getString("protocol"))
        val settings = p.outbound.getJSONObject("settings")
        assertEquals(wgKey, settings.getString("secretKey"))
        assertEquals("10.2.0.2", settings.getJSONArray("address").getString(0))
        assertEquals("1.1.1.1", settings.getJSONArray("remoteDNS").getString(0))
        assertTrue(settings.getBoolean("noKernelTun"))
        assertEquals(1280, settings.getInt("mtu"))
        val peer = settings.getJSONArray("peers").getJSONObject(0)
        assertEquals(wgKey, peer.getString("preSharedKey"))
        assertEquals(25, peer.getInt("keepAlive"))
        assertEquals("::/0", peer.getJSONArray("allowedIPs").getString(1))
        assertEquals("wireguard", XrayConfig.build(p).getJSONArray("outbounds").getJSONObject(0).getString("protocol"))
    }
    @Test fun wireguardRejectsUnsafeAndUnsupportedFields() {
        for (config in listOf(wireguard() + "\n[Peer]\nPublicKey=$wgKey", wireguard().replace("MTU = 1280", "PostUp = secret"), wireguard().replace("1.1.1.1", "dns.example.org"), wireguard().replace("10.2.0.2/32", "10.2.0.999/32"), wireguard().replace("::/0", "::/129"), wireguard().replace("25", "65536"), wireguard().replace("PrivateKey = $wgKey", "PrivateKey = secret"), wireguard().replace("51820", "0"), wireguard().replace("MTU = 1280", "MTU = 1280\nMTU = 1420"))) bad(config)
    }
    @Test fun rejectsIgnoredSettingsAndMalformedUtf8() {
        for (query in listOf("security=tls&plugin=secret", "security=tls&fp=unknown", "security=tls&alpn=", "type=xhttp&mode=invalid", "type=grpc&mode=invalid", "type=ws&path=relative", "security=reality&sni=example.org&pbk=" + "B".repeat(43))) bad("vless://$id@example.org:443?$query")
        bad("vless://$id@example.org:443#%FF")
        bad("vless://$id@example.org:443#%C0%AF")
        bad("vmess://////")
    }
    @Test fun legacyShadowsocksAndUnsupportedVmessFields() {
        assertEquals("shadowsocks", ConfigParser.parse("ss://${b64("aes-256-gcm:secret@example.org:8388")}#old").single().outbound.getString("protocol"))
        val json = org.json.JSONObject().put("add", "example.org").put("port", 443).put("id", id).put("ech", "secret")
        bad("vmess://${b64(json.toString())}")
    }
    @Test fun vlessTlsWebsocket() {
        val p = ConfigParser.parse("vless://$id@example.org:443?security=tls&type=ws&sni=cdn.example.org&host=cdn.example.org&path=%2Fsocket#%D8%AA%D8%B3%D8%AA").single()
        assertEquals("تست", p.name)
        assertEquals("vless", p.outbound.getString("protocol"))
        val server = p.outbound.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
        assertEquals("example.org", server.getString("address"))
        assertEquals(id, server.getJSONArray("users").getJSONObject(0).getString("id"))
        assertEquals("/socket", p.outbound.getJSONObject("streamSettings").getJSONObject("wsSettings").getString("path"))
    }
}

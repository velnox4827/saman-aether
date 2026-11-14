package com.saman.tunnel

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class XrayConfigTest {
    @Test fun loopbackOnlyAndNoDirectFallback() {
        val profile = ConfigParser.parse("trojan://secret@example.org:443").single()
        val config = XrayConfig.build(profile)
        val inbounds = config.getJSONArray("inbounds")
        assertEquals(2, inbounds.length())
        for (i in 0..1) assertEquals("127.0.0.1", inbounds.getJSONObject(i).getString("listen"))
        assertEquals(1819, inbounds.getJSONObject(0).getInt("port"))
        assertEquals("socks", inbounds.getJSONObject(0).getString("protocol"))
        assertTrue(inbounds.getJSONObject(0).getJSONObject("settings").getBoolean("udp"))
        assertEquals(1820, inbounds.getJSONObject(1).getInt("port"))
        assertEquals("http", inbounds.getJSONObject(1).getString("protocol"))
        val outbounds = config.getJSONArray("outbounds")
        assertEquals(1, outbounds.length())
        assertEquals("trojan", outbounds.getJSONObject(0).getString("protocol"))
        assertFalse(config.toString().contains("freedom"))
        assertEquals("proxy", outbounds.getJSONObject(0).getString("tag"))
        assertEquals("proxy", config.getJSONObject("routing").getJSONArray("rules").getJSONObject(0).getString("outboundTag"))
        assertFalse(profile.outbound.has("tag"))
        outbounds.getJSONObject(0).getJSONObject("settings").put("test", true)
        assertFalse(profile.outbound.getJSONObject("settings").has("test"))
    }
    @Test fun nativeXrayAcceptsRepresentativeConfigsWhenAvailable() {
        val binary = System.getenv("XRAY_BINARY")
        org.junit.Assume.assumeTrue("Set XRAY_BINARY to validate the packaged engine", !binary.isNullOrBlank())
        val id = "123e4567-e89b-12d3-a456-426614174000"
        val fixtures = mutableListOf("trojan://secret@example.org:443", "ss://YWVzLTI1Ni1nY206c2VjcmV0@example.org:8388")
        for (type in listOf("tcp", "ws", "grpc", "xhttp", "httpupgrade")) fixtures += "vless://$id@example.org:443?type=$type&security=tls"
        fixtures += "vless://$id@example.org:443?security=reality&sni=example.org&fp=chrome&pbk=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA&sid=abcd&flow=xtls-rprx-vision"
        val vmess = JSONObject().put("add", "example.org").put("port", 443).put("id", id).put("net", "tcp")
        fixtures += "vmess://" + java.util.Base64.getEncoder().encodeToString(vmess.toString().toByteArray())
        fixtures += """
            [Interface]
            PrivateKey = AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=
            Address = 10.2.0.2/32
            DNS = 1.1.1.1
            [Peer]
            PublicKey = AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=
            Endpoint = example.org:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
        for ((index, fixture) in fixtures.withIndex()) {
            val file = java.io.File.createTempFile("xray-fixture-", ".json")
            try {
                file.writeText(XrayConfig.build(ConfigParser.parse(fixture).single()).toString())
                val process = ProcessBuilder(binary!!, "run", "-test", "-config", file.absolutePath).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                assertEquals("fixture $index: $output", 0, process.waitFor())
            } finally { file.delete() }
        }
        println("Native Xray validated ${fixtures.size} synthetic configurations")
    }
    @Test fun rejectsDirectOutbound() {
        try { XrayConfig.build(ImportedProfile("secret", JSONObject().put("protocol", "freedom"))); fail("direct accepted") }
        catch (e: IllegalArgumentException) { assertFalse(e.message.orEmpty().contains("secret")) }
    }
}

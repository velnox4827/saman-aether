package com.saman.tunnel

import org.json.JSONArray
import org.json.JSONObject

object XrayConfig {
    fun build(profile: ImportedProfile): JSONObject {
        require(profile.outbound.optString("protocol") in listOf("vless", "vmess", "trojan", "shadowsocks", "wireguard")) {
            "پروتکل خروجی پشتیبانی نمی‌شود"
        }
        val outbound = JSONObject(profile.outbound.toString()).put("tag", "proxy")
        return JSONObject().put("log", JSONObject().put("loglevel", "none"))
            .put("inbounds", JSONArray()
                .put(JSONObject().put("tag", "socks").put("listen", "127.0.0.1").put("port", 1819).put("protocol", "socks")
                    .put("settings", JSONObject().put("auth", "noauth").put("udp", true)))
                .put(JSONObject().put("tag", "http").put("listen", "127.0.0.1").put("port", 1820).put("protocol", "http")
                    .put("settings", JSONObject())))
            .put("outbounds", JSONArray().put(outbound))
            .put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", JSONArray().put(
                JSONObject().put("type", "field").put("network", "tcp,udp").put("outboundTag", "proxy"))))
    }
}

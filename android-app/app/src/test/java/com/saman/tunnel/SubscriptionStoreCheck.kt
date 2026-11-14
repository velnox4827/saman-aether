package com.saman.tunnel

import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Standalone JVM checks; no account or network required. */
object SubscriptionStoreCheck {
    @JvmStatic fun main(args: Array<String>) {
        val profile = ImportedProfile("test", JSONObject("""{"protocol":"trojan","settings":{"servers":[{"address":"example.org","port":443,"password":"test"}]},"streamSettings":{"security":"tls"}}"""))
        val first = StoreDocument.replace(JSONObject(), "source", "https://example.org/sub", listOf(profile), null)
        val node = first.getJSONArray("subscriptions").getJSONObject(0).getJSONArray("nodes").getJSONObject(0)
        val id = node.getString("id")
        first.put("selected", id)
        first.put("selectedConfig", JSONObject().put("stale", true))
        val again = StoreDocument.replace(first, "source", "https://example.org/sub", listOf(profile), null)
        check(again.getString("selected") == id)
        check(!again.getJSONObject("selectedConfig").has("stale"))
        val removed = StoreDocument.replace(first, "source", "https://example.org/sub", listOf(profile.copy(name = "replacement")), null)
        check(!removed.has("selected") && !removed.has("selectedConfig"))
        check(removed.optBoolean("selectionLost"))
        val changed = profile.copy(outbound = JSONObject(profile.outbound.toString()).put("settings", JSONObject("""{"servers":[{"address":"new.example.org","port":443,"password":"new"}]}""")))
        val refreshed = StoreDocument.replace(first, "source", "https://example.org/sub", listOf(changed), null)
        check(refreshed.getString("selected") == id)
        check(refreshed.getJSONObject("selectedConfig").toString().contains("new.example.org"))
        val duplicates = StoreDocument.replace(first, "source", "https://example.org/sub", listOf(changed, changed), null)
        check(!duplicates.has("selected"))
        check(again.getJSONArray("subscriptions").getJSONObject(0).getJSONArray("nodes").getJSONObject(0).getString("id") == id)
        val before = first.toString()
        check(runCatching { StoreDocument.replace(first, "source", "https://example.org/sub", emptyList(), null) }.isFailure)
        check(first.toString() == before)
        check(runCatching { StoreDocument.replace(first, "source", "https://example.org/sub", listOf(profile, ImportedProfile("unsupported", JSONObject().put("protocol", "tuic"))), null) }.isFailure)
        check(first.toString() == before)
        val second = StoreDocument.replace(first, "other", null, listOf(profile), null)
        check(second.getJSONArray("subscriptions").length() == 2)
        check(!second.getJSONArray("subscriptions").getJSONObject(1).has("info"))
        println("StoreDocument checks passed")
    }
}

package com.saman.tunnel

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Detached JSON snapshots: callers cannot mutate stored state. */
internal object StoreDocument {
    fun replace(current: JSONObject, id: String, url: String?, profiles: List<ImportedProfile>, info: SubscriptionInfo?): JSONObject {
        require(profiles.isNotEmpty()) { "Subscription contains no supported nodes; nothing changed" }
        profiles.forEach { XrayConfig.build(it) }
        val next = JSONObject(current.toString())
        val sources = next.optJSONArray("subscriptions") ?: JSONArray().also { next.put("subscriptions", it) }
        val index = (0 until sources.length()).firstOrNull { sources.getJSONObject(it).getString("id") == id }
        val oldNodes = index?.let { sources.getJSONObject(it).getJSONArray("nodes") }
        val previous = oldNodes?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
        val incomingNames = profiles.groupingBy { it.name }.eachCount()
        val used = mutableSetOf<String>()
        val nodes = JSONArray()
        profiles.forEach { profile ->
            val old = previous.firstOrNull {
                it.getString("id") !in used && it.getString("name") == profile.name && it.getJSONObject("outbound").toString() == profile.outbound.toString()
            } ?: previous.singleOrNull { it.getString("name") == profile.name }?.takeIf {
                incomingNames[profile.name] == 1 && it.getString("id") !in used
            }
            val nodeId = old?.getString("id") ?: UUID.randomUUID().toString()
            used += nodeId
            nodes.put(JSONObject().put("id", nodeId).put("name", profile.name).put("outbound", JSONObject(profile.outbound.toString())))
        }
        val source = JSONObject().put("id", id).put("name", if (url == null) "Imported configurations" else "My subscription")
            .put("nodes", nodes).put("updated", System.currentTimeMillis())
        if (url != null) source.put("url", url)
        if (info != null) source.put("info", JSONObject().apply {
            info.upload?.let { put("upload", it) }; info.download?.let { put("download", it) }
            info.total?.let { put("total", it) }; info.expire?.let { put("expire", it) }
        })
        if (index == null) sources.put(source) else sources.put(index, source)
        val selected = next.optString("selected")
        if (selected.isNotEmpty() && !(0 until sources.length()).any { s ->
            val a = sources.getJSONObject(s).getJSONArray("nodes")
            (0 until a.length()).any { a.getJSONObject(it).getString("id") == selected }
        }) { next.remove("selected"); next.remove("selectedConfig"); next.put("selectionLost", true) }
        else if (selected.isNotEmpty()) {
            for (s in 0 until sources.length()) {
                val a = sources.getJSONObject(s).getJSONArray("nodes")
                for (n in 0 until a.length()) {
                    val node = a.getJSONObject(n)
                    if (node.getString("id") == selected) next.put("selectedConfig", XrayConfig.build(ImportedProfile(node.getString("name"), node.getJSONObject("outbound"))))
                }
            }
        }
        return next
    }
}

internal object StoreFileLock {
    private val monitor = Any()
    fun <T> withLock(file: File, action: () -> T): T = synchronized(monitor) {
        java.io.RandomAccessFile(file, "rw").use { handle ->
            val held = handle.channel.lock()
            try { action() } finally { held.release() }
        }
    }
}

class ConfigStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "config-store-v2.json"))
    private val lockFile = File(context.applicationContext.filesDir, "config-store-v2.lock")
    private fun <T> locked(action: () -> T): T = StoreFileLock.withLock(lockFile, action)

    private fun read(): JSONObject {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return JSONObject()
        try {
            return file.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }.also {
                val sources = it.getJSONArray("subscriptions")
                for (s in 0 until sources.length()) {
                    val source = sources.getJSONObject(s)
                    source.getString("id")
                    val nodes = source.getJSONArray("nodes")
                    for (n in 0 until nodes.length()) {
                        val node = nodes.getJSONObject(n)
                        node.getString("id"); node.getString("name"); node.getJSONObject("outbound")
                    }
                }
            }
        } catch (_: Exception) { throw IllegalStateException("Saved configuration is unreadable; it has not been overwritten") }
    }
    private fun write(value: JSONObject) {
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(value.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (_: Exception) {
            stream?.let { file.failWrite(it) }
            throw IllegalStateException("Could not save configurations; previous data retained")
        }
    }
    fun snapshot(): JSONObject = locked { read() }
    fun replace(id: String, url: String?, profiles: List<ImportedProfile>, info: SubscriptionInfo?) = locked {
        write(StoreDocument.replace(read(), id, url, profiles, info))
    }
    fun importText(text: String): String {
        val profiles = ConfigParser.subscription(text)
        val id = UUID.randomUUID().toString()
        replace(id, null, profiles, null)
        return id
    }
    fun select(nodeId: String) = locked {
        val state = read()
        val node = findNode(state, nodeId) ?: throw IllegalArgumentException("Node no longer exists")
        state.put("selectedConfig", XrayConfig.build(ImportedProfile(node.getString("name"), node.getJSONObject("outbound"))))
        state.put("selected", nodeId)
        state.remove("selectionLost")
        write(state)
    }
    fun selectedProfile(): ImportedProfile? = locked {
        val state = read()
        findNode(state, state.optString("selected"))?.let { ImportedProfile(it.getString("name"), it.getJSONObject("outbound")) }
    }
    private fun findNode(state: JSONObject, id: String): JSONObject? {
        val sources = state.optJSONArray("subscriptions") ?: return null
        for (s in 0 until sources.length()) {
            val nodes = sources.getJSONObject(s).getJSONArray("nodes")
            for (n in 0 until nodes.length()) if (nodes.getJSONObject(n).getString("id") == id) return nodes.getJSONObject(n)
        }
        return null
    }
}

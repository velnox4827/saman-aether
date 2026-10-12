package com.saman.tunnel

import android.content.Context
import java.io.File

/**
 * Region + shape live in a plain file, not SharedPreferences: the core runs in another process
 * (:aether_core) whose preference cache would not see UI writes.
 */
object PsiphonSettings {
    data class Value(val region: String?, val shape: String)

    private fun file(c: Context) = File(c.filesDir, "psiphon.cfg")

    fun get(c: Context): Value {
        val lines = runCatching { file(c).readLines() }.getOrDefault(emptyList())
        val region = AetherArguments.validRegion(lines.getOrNull(0))
        val shape = lines.getOrNull(1)?.trim()?.lowercase()?.takeIf { it in AetherArguments.PSIPHON_SHAPES } ?: "auto"
        return Value(region, shape)
    }

    fun set(c: Context, region: String?, shape: String) {
        val r = AetherArguments.validRegion(region).orEmpty()
        val s = shape.takeIf { it in AetherArguments.PSIPHON_SHAPES } ?: "auto"
        runCatching { file(c).writeText("$r\n$s\n") }
    }
}

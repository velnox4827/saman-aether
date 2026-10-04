package com.saman.tunnel

import android.content.Context
import android.graphics.Color

private fun opaque(hex: Int): Int = hex or (0xFF shl 24)

/** Colour palettes of the glass UI (blue / purple / orange). */
enum class Palette(
    val key: String,
    val acc: Int, val accDark: Int, val accLight: Int,
    val p1: Int, val p2: Int, val p3: Int,
    val d1: Int, val d2: Int, val d3: Int,
    val swA: Int, val swB: Int
) {
    BLUE(
        "blue", opaque(0x3aaeff), opaque(0x4db8ff), opaque(0x7cd0ff),
        opaque(0x9bdcff), opaque(0x6fb7ff), opaque(0xb6e8ff),
        opaque(0x1b6fb0), opaque(0x2a52b0), opaque(0x0f7f9a),
        opaque(0x3aaeff), opaque(0xffffff)
    ),
    PURPLE(
        "purple", opaque(0x8b5cff), opaque(0xa58bff), opaque(0xb49cff),
        opaque(0xcdbdff), opaque(0xa98cff), opaque(0xe3d6ff),
        opaque(0x4a2bc4), opaque(0x7a2fc0), opaque(0x2a45c8),
        opaque(0x8b5cff), opaque(0x2a45c8)
    ),
    ORANGE(
        "orange", opaque(0xff8a2b), opaque(0xffa14d), opaque(0xffb36b),
        opaque(0xffd2a1), opaque(0xffb36b), opaque(0xffe3c6),
        opaque(0xb4561a), opaque(0xc0481f), opaque(0xa8741a),
        opaque(0xff8a2b), opaque(0xffffff)
    );

    companion object {
        fun from(key: String?): Palette = values().firstOrNull { it.key == key } ?: BLUE
    }
}

/** Resolved colour tokens for one theme (light/dark) + palette. */
class Tk(val dark: Boolean, val pal: Palette) {
    val bg: Int = if (dark) opaque(0x061320) else opaque(0xf5fbff)
    val ink: Int = if (dark) opaque(0xe8f4ff) else opaque(0x0b2a43)
    val muted: Int = if (dark) opaque(0x8fa6bb) else opaque(0x5f7b92)
    val line: Int = if (dark) Color.argb(31, 255, 255, 255) else Color.argb(26, 11, 42, 67)
    val glass: Int = if (dark) Color.argb(23, 255, 255, 255) else Color.argb(140, 255, 255, 255)
    val glassB: Int = if (dark) Color.argb(46, 255, 255, 255) else Color.argb(242, 255, 255, 255)
    val tile: Int = if (dark) Color.argb(18, 255, 255, 255) else Color.argb(153, 255, 255, 255)
    val sheet: Int = if (dark) Color.argb(245, 9, 26, 43) else Color.argb(244, 255, 255, 255)
    val scrim: Int = if (dark) Color.argb(140, 0, 0, 0) else Color.argb(102, 11, 42, 67)
    val track: Int = glass
    val tin: Int = if (dark) Color.argb(89, 0, 0, 0) else Color.argb(31, 11, 42, 67)
    val knob: Int = Color.argb(235, 255, 255, 255)
    val knobIcon: Int = if (dark) opaque(0x6b8197) else opaque(0x9bb3c6)
    val ok: Int = if (dark) opaque(0x2fd884) else opaque(0x1fc46f)
    val warn: Int = opaque(0xff9a3d)
    val err: Int = opaque(0xeb4e4e)
    val sky: Int = if (dark) pal.accDark else pal.acc
    val skyLight: Int = pal.accLight

    /** Three background orb colours; green while connected. */
    fun orbColors(connected: Boolean): IntArray = when {
        connected && dark -> intArrayOf(opaque(0x17834b), opaque(0x0e6a5a), opaque(0x1a7a4a))
        connected -> intArrayOf(opaque(0x86e8b0), opaque(0x5fd89a), opaque(0xb2f2cf))
        dark -> intArrayOf(pal.d1, pal.d2, pal.d3)
        else -> intArrayOf(pal.p1, pal.p2, pal.p3)
    }
}

/** UI-only preferences (theme, palette, language, auto-connect, session start). */
class UiPrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("saman_ui", Context.MODE_PRIVATE)

    /** "system" | "light" | "dark" */
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(value) { sp.edit().putString("theme", value).apply() }

    var palette: Palette
        get() = Palette.from(sp.getString("palette", "blue"))
        set(value) { sp.edit().putString("palette", value.key).apply() }

    /** "auto" | "fa" | "en" */
    var lang: String
        get() = sp.getString("lang", "auto") ?: "auto"
        set(value) { sp.edit().putString("lang", value).apply() }

    var autoConnect: Boolean
        get() = sp.getBoolean("auto_connect", false)
        set(value) { sp.edit().putBoolean("auto_connect", value).apply() }

    /** Wall-clock millis when the current connected session was first observed, 0 if none. */
    var since: Long
        get() = sp.getLong("since", 0L)
        set(value) { sp.edit().putLong("since", value).apply() }
}

package com.saman.tunnel

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogStore {
    private const val APP_LOG = "saman-tunnel-app.log"
    private const val CORE_LOG = "aether-core.log"
    private const val CORE_OLD_LOG = "aether-core.previous.log"
    private const val MAX_APP_BYTES = 1024 * 1024L

    @Synchronized
    fun append(context: Context, tag: String, message: String) {
        runCatching {
            val file = File(context.filesDir, APP_LOG)

            if (file.exists() && file.length() > MAX_APP_BYTES) {
                val text = file.readText()
                file.writeText(text.takeLast(512 * 1024))
            }

            val ts = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
            ).format(Date())

            file.appendText("[$ts] [$tag] $message\n")
        }
    }

    fun diagnostics(context: Context): String {
        val pkg = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()

        val appVersion = pkg?.versionName ?: "unknown"
        val prefs = context.getSharedPreferences(
            AetherService.PREFS,
            Context.MODE_PRIVATE
        )
        val status = prefs.getString(AetherService.KEY_STATUS, "unknown")
        val mode = prefs.getString(AetherService.KEY_MODE, "—")

        val header = buildString {
            appendLine("Saman Tunnel diagnostics")
            appendLine("========================")
            appendLine("App version: $appVersion")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Mode: $mode")
            appendLine("Status: $status")
            appendLine()
        }

        // Save TXT is the full diagnostic report.
        // Export every log line currently retained by the app.
        val app = readAll(File(context.filesDir, APP_LOG))
        val core = readAll(File(context.filesDir, CORE_LOG))
        val previousCore = readAll(File(context.filesDir, CORE_OLD_LOG))

        return buildString {
            append(header)
            appendLine("---- APP LOG ----")
            appendLine(if (app.isBlank()) "(empty)" else app)
            appendLine()
            appendLine("---- AETHER CORE LOG ----")
            appendLine(if (core.isBlank()) "(empty)" else core)

            if (previousCore.isNotBlank()) {
                appendLine()
                appendLine("---- PREVIOUS AETHER CORE LOG ----")
                appendLine(previousCore)
            }
        }
    }

    fun quickLog(context: Context, lineCount: Int): String {
        val prefs = context.getSharedPreferences(
            AetherService.PREFS,
            Context.MODE_PRIVATE
        )
        val status = prefs.getString(AetherService.KEY_STATUS, "unknown")
        val mode = prefs.getString(AetherService.KEY_MODE, "—")

        val coreFile = File(context.filesDir, CORE_LOG)
        val appFile = File(context.filesDir, APP_LOG)

        val coreLines = tailLines(coreFile, lineCount)

        return buildString {
            appendLine("Saman Tunnel — quick log")
            appendLine("Mode: $mode")
            appendLine("Status: $status")
            appendLine("------------------------")

            if (coreLines.isNotBlank()) {
                append(coreLines)
            } else {
                append(tailLines(appFile, lineCount).ifBlank { "(no logs yet)" })
            }
        }
    }

    @Synchronized
    fun clear(context: Context) {
        listOf(APP_LOG, CORE_LOG, CORE_OLD_LOG).forEach {
            runCatching { File(context.filesDir, it).delete() }
        }
        append(context, "APP", "Diagnostics cleared by user")
    }

    private fun readAll(file: File): String = runCatching {
        if (!file.exists()) return@runCatching ""
        file.readText()
    }.getOrDefault("")

    private fun tailLines(file: File, count: Int): String = runCatching {
        if (!file.exists()) return@runCatching ""
        file.readLines()
            .takeLast(count.coerceAtLeast(1))
            .joinToString("\n")
    }.getOrDefault("")
}

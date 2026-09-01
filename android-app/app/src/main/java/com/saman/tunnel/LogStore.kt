package com.saman.tunnel

import android.content.Context
import android.os.Build
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogStore {
    private const val APP_LOG = "saman-tunnel-app.log"
    private const val CORE_LOG = "aether-core.log"
    private const val CORE_OLD_LOG = "aether-core.previous.log"


    private const val SAFE_APP_BYTES = 256 * 1024
    private const val SAFE_CORE_BYTES = 1400 * 1024
    private const val SAFE_PREVIOUS_BYTES = 256 * 1024
    private const val SAFE_SESSION_COUNT = 10
    private const val PHASE_TAIL_BYTES = 96 * 1024


    @Synchronized
    fun append(context: Context, tag: String, message: String) {
        runCatching {
            val file = File(context.filesDir, APP_LOG)

            if (file.exists() && LogRotationPolicy.shouldRotate(file.length(), LogRotationPolicy.MAX_APP_BYTES)) {
                file.writeText(readTailBytes(file, 512 * 1024))
            }

            val ts = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
            ).format(Date())

            file.appendText("[$ts] [$tag] $message\n")
        }
    }

    fun diagnostics(context: Context): String =
        buildDiagnostics(
            context = context,
            fullHistory = false,
            sanitized = true
        )

    fun fullDiagnostics(context: Context): String =
        buildDiagnostics(
            context = context,
            fullHistory = true,
            sanitized = true
        )

    private fun buildDiagnostics(
        context: Context,
        fullHistory: Boolean,
        sanitized: Boolean
    ): String {
        val pkg = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()

        val appVersion = pkg?.versionName ?: "unknown"

        val prefs = context.getSharedPreferences(
            AetherService.PREFS,
            Context.MODE_PRIVATE
        )

        val status =
            prefs.getString(AetherService.KEY_STATUS, "unknown")

        val mode =
            prefs.getString(AetherService.KEY_MODE, "—")

        val header = buildString {
            appendLine("Saman Tunnel diagnostics")
            appendLine("========================")
            appendLine("App version: $appVersion")
            appendLine(
                "Report: " +
                    if (fullHistory) {
                        "FULL HISTORY (sanitized)"
                    } else {
                        "SAFE RECENT (sanitized, last $SAFE_SESSION_COUNT sessions)"
                    }
            )
            appendLine(
                "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
            )
            appendLine(
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}"
            )
            appendLine(
                "ABI: ${Build.SUPPORTED_ABIS.joinToString()}"
            )
            appendLine("Mode: $mode")
            appendLine("Status: $status")
            appendLine()
        }

        val appFile = File(context.filesDir, APP_LOG)
        val coreFile = File(context.filesDir, CORE_LOG)
        val previousFile = File(context.filesDir, CORE_OLD_LOG)

        var app = readAll(appFile)
        var core = readAll(coreFile)
        var previous = readAll(previousFile)

        if (!fullHistory) {
            app = limitTailUtf8(app, SAFE_APP_BYTES)

            core = recentSessions(
                core,
                SAFE_SESSION_COUNT,
                SAFE_CORE_BYTES
            )

            previous = recentSessions(
                previous,
                SAFE_SESSION_COUNT,
                SAFE_PREVIOUS_BYTES
            )
        }

        if (sanitized) {
            app = sanitize(app)
            core = sanitize(core)
            previous = sanitize(previous)
        }

        return buildString {
            append(header)

            appendLine("---- APP LOG ----")
            appendLine(
                if (app.isBlank()) "(empty)" else app
            )

            appendLine()
            appendLine("---- AETHER CORE LOG ----")
            appendLine(
                if (core.isBlank()) "(empty)" else core
            )

            if (previous.isNotBlank()) {
                appendLine()
                appendLine(
                    "---- PREVIOUS AETHER CORE LOG ----"
                )
                appendLine(previous)
            }
        }
    }

    fun quickLog(context: Context, lineCount: Int): String {
        val prefs = context.getSharedPreferences(
            AetherService.PREFS,
            Context.MODE_PRIVATE
        )

        val status =
            prefs.getString(AetherService.KEY_STATUS, "unknown")

        val mode =
            prefs.getString(AetherService.KEY_MODE, "—")

        val coreFile = File(context.filesDir, CORE_LOG)
        val appFile = File(context.filesDir, APP_LOG)

        val coreLines =
            sanitize(tailLines(coreFile, lineCount))

        return buildString {
            appendLine("Saman Tunnel — quick log")
            appendLine("Mode: $mode")
            appendLine("Status: $status")
            appendLine("------------------------")

            if (coreLines.isNotBlank()) {
                append(coreLines)
            } else {
                append(
                    sanitize(
                        tailLines(appFile, lineCount)
                            .ifBlank { "(no logs yet)" }
                    )
                )
            }
        }
    }

    fun connectionPhase(
        context: Context,
        mode: String
    ): String? {
        val file = File(context.filesDir, CORE_LOG)
        val tail = readTailBytes(file, PHASE_TAIL_BYTES)

        if (tail.isBlank()) return null

        val marker =
            "================ AETHER SESSION ================"

        val current =
            tail.substringAfterLast(marker, tail)

        val lines =
            current.lineSequence()
                .filter { it.isNotBlank() }
                .takeLastCompat(80)
                .toList()

        for (line in lines.asReversed()) {
            val value = line.lowercase(Locale.US)

            when {
                value.contains("socks5 server listening") ||
                    value.contains("socks5 listening on") ->
                    return "Starting local proxies"

                value.contains("smart reconnect wg") &&
                    value.contains("rtt") &&
                    value.contains("scanning fresh") ->
                    return "WG cached endpoint too slow — fresh scan"

                value.contains("smart reconnect masque") &&
                    value.contains("slower than") ->
                    return "MASQUE cached gateway too slow — fresh scan"

                value.contains("smart reconnect wg") &&
                    value.contains("died after") ->
                    return "WG cached endpoint was short-lived — fresh scan"

                value.contains("smart reconnect masque") &&
                    value.contains("died after") ->
                    return "MASQUE cached gateway was short-lived — fresh scan"

                value.contains("smart reconnect gool") &&
                    value.contains("died after") ->
                    return "GOOL pair was short-lived — fresh scan"

                value.contains("blacklisting and rescanning") ||
                    value.contains("excluding it for") ||
                    (
                        value.contains("no longer works") &&
                            value.contains("rescan")
                    ) ->
                    return "Endpoint failed — rescanning"

                value.contains("scanning fresh") ->
                    return "Cached endpoint failed — rescanning"

                value.contains("verifying cached") ->
                    return "Checking cached endpoint"

                value.contains("retrying last known-good") ->
                    return "Retrying last endpoint"

                value.contains("hunting for 2 working") ->
                    return "Scanning balanced endpoints"

                value.contains("hunting for a working") ->
                    return "Scanning balanced endpoints"

                value.contains("scan mode=balanced") ->
                    return "Scanning balanced endpoints"

                value.contains("establishing inner warp") ||
                    value.contains("inner endpoint") ->
                    return "Establishing inner tunnel"

                value.contains("establishing outer") ->
                    return "Establishing outer tunnel"

                value.contains("validating") &&
                    value.contains("tunnel") ->
                    return "Validating tunnel"

                value.contains("selected wireguard endpoint") ||
                    value.contains("selected masque") ||
                    value.contains("using cloudflare edge") ->
                    return "Endpoint selected"
            }
        }

        return when (mode.uppercase(Locale.US)) {
            "GOOL" -> "Preparing dual tunnel"
            "MASQUE_H2" -> "Preparing MASQUE H2"
            "MASQUE_H3", "MASQUE" -> "Preparing MASQUE H3"
            else -> "Preparing WireGuard"
        }
    }

    @Synchronized
    fun clear(context: Context) {
        listOf(
            APP_LOG,
            CORE_LOG,
            CORE_OLD_LOG
        ).forEach {
            runCatching {
                File(context.filesDir, it).delete()
            }
        }

        append(
            context,
            "APP",
            "Diagnostics cleared by user"
        )
    }

    private fun sanitize(text: String): String =
        DiagnosticsSanitizer.sanitize(text)

    private fun recentSessions(
        text: String,
        sessionCount: Int,
        maxBytes: Int
    ): String {
        if (text.isBlank()) return ""

        val marker =
            "================ AETHER SESSION ================"

        val matches =
            Regex(
                "(?m)^" +
                    Regex.escape(marker) +
                    "$"
            )
                .findAll(text)
                .toList()

        val recent =
            if (matches.size > sessionCount) {
                text.substring(
                    matches[
                        matches.size - sessionCount
                    ].range.first
                )
            } else {
                text
            }

        return limitTailUtf8(recent, maxBytes)
    }

    private fun readAll(file: File): String =
        runCatching {
            if (!file.exists()) {
                return@runCatching ""
            }

            file.readText()
        }.getOrDefault("")

    private fun readTailBytes(
        file: File,
        maxBytes: Int
    ): String =
        runCatching {
            if (!file.exists() || file.length() <= 0L) {
                return@runCatching ""
            }

            RandomAccessFile(file, "r").use { raf ->
                val size =
                    minOf(
                        raf.length(),
                        maxBytes.toLong()
                    ).toInt()

                val start =
                    (raf.length() - size)
                        .coerceAtLeast(0L)

                raf.seek(start)

                val bytes = ByteArray(size)
                raf.readFully(bytes)

                String(bytes, Charsets.UTF_8)
            }
        }.getOrDefault("")

    private fun tailLines(
        file: File,
        count: Int
    ): String =
        runCatching {
            if (!file.exists()) {
                return@runCatching ""
            }

            file.readLines()
                .takeLast(count.coerceAtLeast(1))
                .joinToString("\n")
        }.getOrDefault("")

    private fun limitTailUtf8(
        text: String,
        maxBytes: Int
    ): String {
        if (text.isBlank()) return text

        if (
            text.toByteArray(Charsets.UTF_8).size <=
            maxBytes
        ) {
            return text
        }

        var low = 0
        var high = text.length

        while (low < high) {
            val mid = (low + high) / 2

            val bytes =
                text.substring(mid)
                    .toByteArray(Charsets.UTF_8)
                    .size

            if (bytes > maxBytes) {
                low = mid + 1
            } else {
                high = mid
            }
        }

        return text.substring(low)
    }

    private fun <T> Sequence<T>.takeLastCompat(
        count: Int
    ): Sequence<T> {
        val buffer = ArrayDeque<T>()

        for (item in this) {
            buffer.addLast(item)

            if (buffer.size > count) {
                buffer.removeFirst()
            }
        }

        return buffer.asSequence()
    }
}

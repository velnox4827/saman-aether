package com.saman.tunnel

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.provider.MediaStore
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlin.math.max

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_SAVE_DIAGNOSTICS = 2002
        private const val RELEASES_API =
            "https://api.github.com/repos/velnox4827/saman-aether/releases/latest"
    }

    private lateinit var modeView: TextView
    private lateinit var statusView: TextView
    private lateinit var proxyStatusView: TextView
    private lateinit var statusBadge: TextView
    private lateinit var versionView: TextView
    private lateinit var batteryView: TextView
    private lateinit var updateView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var lastStartTap = 0L

    private val refresh = object : Runnable {
        override fun run() {
            refreshState()
            handler.postDelayed(this, 900)
        }
    }

    private val isDark: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private val canvas: Int get() =
        if (isDark) Color.rgb(11, 15, 23) else Color.rgb(248, 249, 251)
    private val card: Int get() =
        if (isDark) Color.rgb(27, 33, 44) else Color.WHITE
    private val cardSoft: Int get() =
        if (isDark) Color.rgb(22, 28, 38) else Color.rgb(251, 252, 254)
    private val ink: Int get() =
        if (isDark) Color.rgb(238, 242, 249) else Color.rgb(24, 33, 50)
    private val muted: Int get() =
        if (isDark) Color.rgb(166, 174, 188) else Color.rgb(100, 108, 122)
    private val line: Int get() =
        if (isDark) Color.rgb(54, 62, 76) else Color.rgb(224, 228, 235)

    private val blue = Color.rgb(68, 145, 255)
    private val green = Color.rgb(35, 190, 128)
    private val purple = Color.rgb(158, 89, 255)
    private val red = Color.rgb(235, 78, 78)
    private val orange = Color.rgb(244, 142, 50)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = canvas
        window.navigationBarColor = canvas

        LogStore.append(this, "APP", "MainActivity created")
        buildUi()
        showVersions()
        refreshBatteryStatus()
        requestNotificationsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshBatteryStatus()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(
        fill: Int,
        radius: Int = 18,
        strokeColor: Int? = null,
        strokeWidth: Int = 1
    ): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (strokeColor != null) setStroke(dp(strokeWidth), strokeColor)
    }

    private fun buildUi() {
        val baseLeft = dp(14)
        val baseTop = dp(8)
        val baseRight = dp(14)
        val baseBottom = dp(8)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(canvas)
            setPadding(baseLeft, baseTop, baseRight, baseBottom)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            setOnApplyWindowInsetsListener { view, insets ->
                val topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsets(WindowInsets.Type.statusBars()).top
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetTop
                }

                val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetBottom
                }

                view.setPadding(
                    baseLeft,
                    topInset + baseTop,
                    baseRight,
                    max(baseBottom, bottomInset)
                )
                insets
            }

            requestApplyInsets()
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(5))
        }

        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.saman_tunnel_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            contentDescription = "Saman Tunnel logo"
            layoutParams = LinearLayout.LayoutParams(dp(62), dp(62)).apply {
                marginEnd = dp(10)
            }
        })

        val headerText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        headerText.addView(TextView(this).apply {
            text = if (packageName.endsWith(".beta")) "Saman Tunnel Beta" else "Saman Tunnel"
            textSize = 24f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        })

        versionView = TextView(this).apply {
            text = "App …  •  Aether Core …"
            textSize = 12.5f
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
            includeFontPadding = false
        }

        headerText.addView(versionView)
        header.addView(headerText)
        root.addView(header)

        // Status
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(card, 18, line)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(104)
            ).apply {
                bottomMargin = dp(8)
            }
        }

        statusBadge = TextView(this).apply {
            text = "○"
            gravity = Gravity.CENTER
            textSize = 23f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50)).apply {
                marginEnd = dp(11)
            }
        }
        statusCard.addView(statusBadge)

        val statusColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        modeView = TextView(this).apply {
            text = "Mode: —"
            textSize = 16f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }

        statusView = TextView(this).apply {
            text = "Status: Stopped"
            textSize = 13.2f
            setTextColor(muted)
            setPadding(0, dp(5), 0, 0)
            includeFontPadding = false
            maxLines = 1
            isSingleLine = true
        }

        proxyStatusView = TextView(this).apply {
            text = ""
            textSize = 11.8f
            setTextColor(green)
            setPadding(0, dp(3), 0, 0)
            includeFontPadding = false
            maxLines = 2
            isSingleLine = false
            visibility = View.GONE
        }

        statusColumn.addView(modeView)
        statusColumn.addView(statusView)
        statusColumn.addView(proxyStatusView)
        statusCard.addView(statusColumn)
        root.addView(statusCard)

        // Modes
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(70)
            ).apply { bottomMargin = dp(7) }
        }

        modeRow.addView(modeTile("◎", "MASQUE", blue, tinted(blue)) {
            chooseMasqueMode()
        })
        modeRow.addView(modeTile("◇", "WireGuard", green, tinted(green)) {
            start("WG")
        })
        modeRow.addView(modeTile("◉", "GOOL", purple, tinted(purple)) {
            start("GOOL")
        })
        root.addView(modeRow)

        // Stop / Copy
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply { bottomMargin = dp(7) }
        }

        actionRow.addView(
            actionTile("■  STOP", red, if (isDark) Color.rgb(54, 29, 33) else Color.rgb(255, 245, 245)) {
                stop()
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = dp(5)
            }
        )

        actionRow.addView(
            actionTile("▣  Copy proxies", ink, card) {
                copySocks()
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(5)
            }
        )
        root.addView(actionRow)

        // Diagnostics
        val diagnosticsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(card, 18, line)
            setPadding(dp(9), dp(7), dp(9), dp(8))
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(108)
            ).apply { bottomMargin = dp(7) }
        }

        diagnosticsCard.addView(TextView(this).apply {
            text = "Diagnostics"
            textSize = 14.2f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            setPadding(dp(2), 0, 0, dp(6))
        })

        val diagnosticsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        diagnosticsRow.addView(diagTile("≡", "Logs", blue) { showQuickLog(40) })
        diagnosticsRow.addView(diagTile("⇩", "Save TXT", green) { saveDiagnosticsTxt() })
        diagnosticsCard.addView(diagnosticsRow)
        root.addView(diagnosticsCard)

        // Utilities: battery + updater
        val utilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply { bottomMargin = dp(7) }
        }

        batteryView = utilityTile("Battery: …") { openBatterySettings() }
        utilityRow.addView(
            batteryView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = dp(5)
            }
        )

        updateView = utilityTile("Check update") { checkForUpdates() }
        utilityRow.addView(
            updateView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(5)
            }
        )

        root.addView(utilityRow)

        // SOCKS
        val socksCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(
                if (isDark) Color.rgb(19, 35, 57) else Color.rgb(244, 248, 255),
                18,
                if (isDark) Color.rgb(48, 87, 133) else Color.rgb(177, 205, 247)
            )
            setPadding(dp(13), dp(7), dp(13), dp(7))
            isClickable = true
            isFocusable = true
            setOnClickListener { copySocks() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
            ).apply { bottomMargin = dp(4) }
        }

        socksCard.addView(TextView(this).apply {
            text = "▦"
            gravity = Gravity.CENTER
            textSize = 25f
            setTextColor(blue)
            background = rounded(
                if (isDark) Color.rgb(29, 53, 83) else Color.rgb(227, 238, 255),
                14
            )
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                marginEnd = dp(11)
            }
        })

        val socksText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        socksText.addView(TextView(this).apply {
            text = "LOCAL PROXIES"
            textSize = 12.5f
            setTextColor(blue)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        })

        socksText.addView(TextView(this).apply {
            text = "SOCKS5  127.0.0.1:1819\nHTTP    127.0.0.1:1820"
            textSize = 12.8f
            setTextColor(ink)
            setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
            includeFontPadding = false
            maxLines = 2
        })

        socksCard.addView(socksText)
        root.addView(socksCard)

        root.addView(TextView(this).apply {
            text = if (packageName.endsWith(".beta")) {
                "β  Beta — do not run Stable and Beta at the same time."
            } else {
                "♢  Bypass only Saman Tunnel in your VPN app."
            }
            textSize = 10.8f
            setTextColor(muted)
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(22)
            )
        })

        setContentView(root)
        refreshState()
    }

    private fun tinted(accent: Int): Int =
        if (isDark) Color.rgb(
            (Color.red(accent) * 0.16).toInt(),
            (Color.green(accent) * 0.16).toInt(),
            (Color.blue(accent) * 0.16).toInt()
        ) else Color.argb(18, Color.red(accent), Color.green(accent), Color.blue(accent))

    private fun modeTile(
        symbol: String,
        label: String,
        accent: Int,
        fill: Int,
        action: () -> Unit
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = rounded(fill, 17, withAlpha(accent, if (isDark) 135 else 85))
        isClickable = true
        isFocusable = true
        setOnClickListener {
            LogStore.append(this@MainActivity, "UI", "Mode tile tapped: $label")
            action()
        }
        layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        }

        addView(TextView(this@MainActivity).apply {
            text = symbol
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(accent)
            includeFontPadding = false
        })

        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = if (label == "WireGuard") 13.2f else 13.8f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            setPadding(0, dp(3), 0, 0)
        })
    }

    private fun actionTile(
        label: String,
        accent: Int,
        fill: Int,
        action: () -> Unit
    ): TextView = TextView(this).apply {
        text = label
        textSize = 13.8f
        gravity = Gravity.CENTER
        setTextColor(accent)
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(fill, 17, if (accent == ink) line else withAlpha(accent, 100))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun diagTile(
        symbol: String,
        label: String,
        accent: Int,
        action: () -> Unit
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = rounded(cardSoft, 14, line)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        }

        addView(TextView(this@MainActivity).apply {
            text = symbol
            textSize = if (symbol.length <= 2 && symbol.all { it.isDigit() }) 15f else 19f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        })

        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 10.8f
            gravity = Gravity.CENTER
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            setPadding(0, dp(3), 0, 0)
        })
    }

    private fun utilityTile(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 11.3f
            gravity = Gravity.CENTER
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(card, 15, line)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun showVersions() {
        val appVersion = currentVersion()
        val coreVersion = runCatching {
            val reply = JSONObject(NativeBridge.version())
            if (reply.optBoolean("ok")) reply.optString("version", "unknown") else "unavailable"
        }.getOrElse {
            LogStore.append(this, "NATIVE", "Could not read Aether version: ${it.stackTraceToString()}")
            "unavailable"
        }

        versionView.text = "App v$appVersion  •  Aether Core v$coreVersion"
        LogStore.append(this, "VERSION", "app=$appVersion aether=$coreVersion")
    }

    private fun chooseMasqueMode() {
        if (isBusy()) {
            Toast.makeText(this, "Please wait for the current action to finish", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("MASQUE mode")
            .setItems(
                arrayOf(
                    "HTTP/3 (QUIC) — default",
                    "HTTP/2 — alternative network mode"
                )
            ) { _, which ->
                when (which) {
                    0 -> start("MASQUE_H3")
                    1 -> start("MASQUE_H2")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isBusy(): Boolean {
        val status = getSharedPreferences(AetherService.PREFS, MODE_PRIVATE)
            .getString(AetherService.KEY_STATUS, "") ?: ""

        return status.startsWith("Starting", true) ||
            status.startsWith("Connecting", true) ||
            status.startsWith("Switching", true) ||
            status.startsWith("Stopping", true)
    }

    private fun start(mode: String) {
        if (isBusy()) {
            Toast.makeText(
                this,
                "Connection action already in progress",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastStartTap < 1400L) return
        lastStartTap = now

        val prefs =
            getSharedPreferences(
                AetherService.PREFS,
                MODE_PRIVATE
            )

        prefs.edit()
            .putString(
                AetherService.KEY_LAST_MODE,
                mode
            )
            .apply()

        val status =
            prefs.getString(
                AetherService.KEY_STATUS,
                "Stopped"
            ).orEmpty()

        val active =
            status.startsWith("Connected", true) ||
                status.startsWith("Connection unstable", true)

        if (active) {
            LogStore.append(
                this,
                "UI",
                "Mode switch requested -> $mode; restarting isolated core"
            )

            stop()

            handler.postDelayed(
                { launchCoreService(mode) },
                1000L
            )
        } else {
            launchCoreService(mode)
        }
    }

    private fun launchCoreService(mode: String) {
        LogStore.append(
            this,
            "UI",
            "Start requested: $mode"
        )

        val intent =
            Intent(
                this,
                AetherService::class.java
            ).apply {
                action = AetherService.ACTION_START
                putExtra(AetherService.EXTRA_MODE, mode)
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stop() {
        LogStore.append(this, "UI", "Stop requested")
        startService(Intent(this, AetherService::class.java).apply {
            action = AetherService.ACTION_STOP
        })
    }

    private fun copySocks() {
        val socks = "127.0.0.1:1819"
        val http = "127.0.0.1:1820"
        AlertDialog.Builder(this)
            .setTitle("Copy local proxy")
            .setItems(arrayOf("SOCKS5  $socks", "HTTP CONNECT  $http", "Copy both")) { _, which ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val (label, value) = when (which) {
                    0 -> "SOCKS5" to socks
                    1 -> "HTTP CONNECT" to http
                    else -> "Saman Tunnel proxies" to "SOCKS5 $socks\nHTTP CONNECT $http"
                }
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showQuickLog(lines: Int) {
        val text = LogStore.quickLog(this, lines)

        val textView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 10.5f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextColor(ink)
            setTextIsSelectable(true)
            this.text = text
        }

        AlertDialog.Builder(this)
            .setTitle("Last $lines log lines")
            .setView(ScrollView(this).apply { addView(textView) })
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Saman Tunnel log", text))
                Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Clear") { _, _ ->
                LogStore.clear(this)
                Toast.makeText(this, "Diagnostics cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun saveDiagnosticsTxt() {
        LogStore.append(
            this,
            "UI",
            "Save diagnostics TXT requested"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveDiagnosticsToDownloads()
            return
        }

        val intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TITLE,
                    "Saman-Tunnel-diagnostics.txt"
                )
            }

        startActivityForResult(
            intent,
            REQUEST_SAVE_DIAGNOSTICS
        )
    }

    private fun saveDiagnosticsToDownloads() {
        runCatching {
            val report = LogStore.diagnostics(this)
            val bytes = report.toByteArray(Charsets.UTF_8)

            require(bytes.isNotEmpty()) {
                "Diagnostics report was empty"
            }

            val fileName =
                "Saman-Tunnel-diagnostics-${System.currentTimeMillis()}.txt"

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri =
                contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                )
                    ?: error("Could not create file in Downloads")

            try {
                val descriptor =
                    contentResolver.openFileDescriptor(uri, "w")
                        ?: error("Could not open Downloads file")

                descriptor.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { output ->
                        output.write(bytes)
                        output.flush()
                        output.fd.sync()
                    }
                }

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)

                val verifiedSize =
                    contentResolver
                        .openFileDescriptor(uri, "r")
                        ?.use { it.statSize }
                        ?: -1L

                require(verifiedSize != 0L) {
                    "Android saved a zero-byte diagnostics file"
                }

                LogStore.append(
                    this,
                    "UI",
                    "Diagnostics saved to Downloads bytes=${bytes.size} verified=$verifiedSize"
                )

                Toast.makeText(
                    this,
                    "Diagnostics saved to Downloads ($verifiedSize bytes)",
                    Toast.LENGTH_LONG
                ).show()
            } catch (t: Throwable) {
                contentResolver.delete(uri, null, null)
                throw t
            }
        }.onFailure {
            LogStore.append(
                this,
                "ERROR",
                "Saving diagnostics failed: ${it.stackTraceToString()}"
            )

            Toast.makeText(
                this,
                "Could not save diagnostics: ${it.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_SAVE_DIAGNOSTICS || resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        runCatching {
            val report = LogStore.diagnostics(this)
            val bytes = report.toByteArray(Charsets.UTF_8)

            require(bytes.isNotEmpty()) {
                "Diagnostics report was empty"
            }

            val descriptor =
                contentResolver.openFileDescriptor(uri, "w")
                    ?: error("Could not open selected file")

            descriptor.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { output ->
                    output.write(bytes)
                    output.flush()
                    output.fd.sync()
                }
            }

            val verifiedSize =
                contentResolver
                    .openFileDescriptor(uri, "r")
                    ?.use { it.statSize }
                    ?: -1L

            require(verifiedSize != 0L) {
                "Android saved a zero-byte diagnostics file"
            }

            LogStore.append(
                this,
                "UI",
                "Diagnostics saved as TXT bytes=${bytes.size} verified=$verifiedSize"
            )

            Toast.makeText(
                this,
                "Diagnostics TXT saved ($verifiedSize bytes)",
                Toast.LENGTH_SHORT
            ).show()
        }.onFailure {
            LogStore.append(this, "ERROR", "Saving diagnostics TXT failed: ${it.stackTraceToString()}")
            Toast.makeText(this, "Could not save TXT", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshBatteryStatus() {
        if (!::batteryView.isInitialized) return

        val unrestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }

        batteryView.text =
            if (unrestricted) "Battery: Unrestricted" else "Battery: Optimized"
        batteryView.setTextColor(if (unrestricted) green else orange)
    }

    private fun openBatterySettings() {
        val packageUri = Uri.parse("package:$packageName")

        val batteryIntent: Intent =
            Intent("android.settings.APP_BATTERY_SETTINGS").apply {
                data = packageUri
            }

        val appInfoIntent: Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = packageUri
            }

        when {
            batteryIntent.resolveActivity(packageManager) != null -> {
                startActivity(batteryIntent)
            }

            appInfoIntent.resolveActivity(packageManager) != null -> {
                startActivity(appInfoIntent)
            }

            else -> {
                Toast.makeText(
                    this,
                    "Battery settings are not available on this device",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun checkForUpdates() {
        if (updateView.text.toString().startsWith("Checking")) return

        updateView.text = "Checking…"
        LogStore.append(this, "UPDATE", "Manual update check started")

        Thread {
            val result = runCatching { fetchLatestRelease() }

            runOnUiThread {
                updateView.text = "Check update"

                result.onSuccess { release ->
                    val current = currentVersion()
                    val latest = release.first.removePrefix("v")

                    if (compareVersions(latest, current) > 0) {
                        LogStore.append(this, "UPDATE", "Update available current=$current latest=$latest")

                        AlertDialog.Builder(this)
                            .setTitle("Update available")
                            .setMessage("Installed: v$current\nLatest: v$latest")
                            .setPositiveButton("Open GitHub") { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.second)))
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    } else {
                        LogStore.append(this, "UPDATE", "Already current=$current latest=$latest")

                        AlertDialog.Builder(this)
                            .setTitle("Saman Tunnel is up to date")
                            .setMessage("Installed: v$current\nLatest release: v$latest")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }.onFailure {
                    LogStore.append(this, "UPDATE", "Update check failed: ${it.stackTraceToString()}")
                    AlertDialog.Builder(this)
                        .setTitle("Could not check for updates")
                        .setMessage(it.message ?: "GitHub could not be reached.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun fetchLatestRelease(): Pair<String, String> {
        val connected = getSharedPreferences(AetherService.PREFS, MODE_PRIVATE)
            .getString(AetherService.KEY_STATUS, "")
            ?.startsWith("Connected", true) == true

        val attempts = mutableListOf<Proxy?>()
        if (connected) {
            attempts += Proxy(
                Proxy.Type.SOCKS,
                InetSocketAddress.createUnresolved("127.0.0.1", 1819)
            )
        }
        attempts += null

        var lastError: Throwable? = null

        for (proxy in attempts) {
            try {
                val connection = if (proxy == null) {
                    URL(RELEASES_API).openConnection()
                } else {
                    URL(RELEASES_API).openConnection(proxy)
                } as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 9_000
                connection.readTimeout = 9_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "Saman-Tunnel/${currentVersion()}")

                val code = connection.responseCode
                if (code !in 200..299) {
                    connection.disconnect()
                    error("GitHub returned HTTP $code")
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(body)
                val tag = json.optString("tag_name")
                val url = json.optString(
                    "html_url",
                    "https://github.com/velnox4827/saman-aether/releases/latest"
                )

                if (tag.isBlank()) error("Latest GitHub release has no tag.")
                return tag to url
            } catch (t: Throwable) {
                lastError = t
            }
        }

        throw lastError ?: IllegalStateException("Unable to reach GitHub.")
    }

    private fun currentVersion(): String =
        runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")

    private fun compareVersions(a: String, b: String): Int {
        fun parts(v: String): List<Int> =
            v.removePrefix("v")
                .split(".")
                .map { token -> token.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

        val pa = parts(a)
        val pb = parts(b)
        val count = max(pa.size, pb.size)

        for (i in 0 until count) {
            val av = pa.getOrElse(i) { 0 }
            val bv = pb.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }

        return 0
    }

    private fun prettyMode(mode: String): String = when (mode.uppercase()) {
        "MASQUE_H3", "MASQUE" -> "MASQUE H3"
        "MASQUE_H2" -> "MASQUE H2"
        "WG" -> "WG"
        "GOOL" -> "GOOL"
        else -> mode.ifBlank { "—" }
    }

    private fun refreshState() {
        val prefs = getSharedPreferences(AetherService.PREFS, MODE_PRIVATE)
        val mode = prefs.getString(AetherService.KEY_MODE, "") ?: ""
        val rawStatus = prefs.getString(AetherService.KEY_STATUS, "Stopped") ?: "Stopped"
        val status = rawStatus.trim()

        modeView.text = "Mode: ${prettyMode(mode)}"

        val connected = status.startsWith("Connected", true)
        val unstable = status.startsWith("Connection unstable", true)
        val error = status.startsWith("Error", true)
        val starting = status.startsWith("Starting", true)
        val connecting = status.startsWith("Connecting", true)
        val switching = status.startsWith("Switching", true)
        val stopping = status.startsWith("Stopping", true)
        val stopped = status.startsWith("Stopped", true)

        val detail: String

        when {
            connected -> {
                statusView.text = "Status: Connected"
                detail = if (status.contains("HTTP", true)) {
                    "SOCKS5 :1819 + HTTP :1820"
                } else {
                    "SOCKS5 :1819"
                }
            }

            unstable -> {
                statusView.text = "Status: Connection unstable"
                detail = "Checking SOCKS5 127.0.0.1:1819"
            }

            error -> {
                statusView.text = "Status: Error"
                detail = status
                    .substringAfter("Error:", "")
                    .trim()
                    .ifBlank { "Aether core reported an error" }
            }

            switching -> {
                statusView.text = "Status: Switching mode"
                detail = "Resetting previous connection"
            }

            starting -> {
                statusView.text = "Status: Starting"
                detail = "Preparing ${prettyMode(mode)}"
            }

            connecting -> {
                statusView.text = "Status: Connecting"
                detail = "Waiting for local proxies"
            }

            stopping -> {
                statusView.text = "Status: Stopping"
                detail = "Closing local proxies"
            }

            stopped -> {
                statusView.text = "Status: Stopped"
                detail = ""
            }

            else -> {
                val parts = status.split("—", limit = 2)
                statusView.text =
                    "Status: ${parts.firstOrNull()?.trim().orEmpty().ifBlank { "Unknown" }}"
                detail = parts.getOrNull(1)?.trim().orEmpty()
            }
        }

        proxyStatusView.text = detail
        proxyStatusView.visibility =
            if (detail.isBlank()) View.GONE else View.VISIBLE

        when {
            connected -> {
                statusBadge.text = "✓"
                statusBadge.setTextColor(green)
                statusBadge.background = rounded(
                    if (isDark) Color.rgb(22, 60, 48) else Color.rgb(235, 250, 242),
                    16,
                    if (isDark) Color.rgb(42, 112, 87) else Color.rgb(190, 231, 211)
                )
                statusView.setTextColor(green)
                proxyStatusView.setTextColor(green)
            }

            error -> {
                statusBadge.text = "!"
                statusBadge.setTextColor(red)
                statusBadge.background = rounded(
                    if (isDark) Color.rgb(67, 30, 34) else Color.rgb(255, 239, 239),
                    16,
                    if (isDark) Color.rgb(128, 54, 61) else Color.rgb(244, 191, 191)
                )
                statusView.setTextColor(red)
                proxyStatusView.setTextColor(red)
            }

            unstable -> {
                statusBadge.text = "…"
                statusBadge.setTextColor(orange)
                statusBadge.background = rounded(
                    if (isDark) Color.rgb(63, 45, 24) else Color.rgb(255, 247, 235),
                    16,
                    if (isDark) Color.rgb(126, 86, 38) else Color.rgb(242, 207, 158)
                )
                statusView.setTextColor(orange)
                proxyStatusView.setTextColor(muted)
            }

            starting || connecting || switching -> {
                statusBadge.text = "…"
                statusBadge.setTextColor(blue)
                statusBadge.background = rounded(
                    if (isDark) Color.rgb(24, 47, 76) else Color.rgb(238, 246, 255),
                    16,
                    if (isDark) Color.rgb(51, 91, 139) else Color.rgb(188, 213, 245)
                )
                statusView.setTextColor(blue)
                proxyStatusView.setTextColor(muted)
            }

            stopping -> {
                statusBadge.text = "…"
                statusBadge.setTextColor(orange)
                statusBadge.background = rounded(
                    if (isDark) Color.rgb(63, 45, 24) else Color.rgb(255, 247, 235),
                    16,
                    if (isDark) Color.rgb(126, 86, 38) else Color.rgb(242, 207, 158)
                )
                statusView.setTextColor(orange)
                proxyStatusView.setTextColor(muted)
            }

            else -> {
                statusBadge.text = "○"
                statusBadge.setTextColor(muted)
                statusBadge.background = rounded(cardSoft, 16, line)
                statusView.setTextColor(muted)
                proxyStatusView.setTextColor(muted)
            }
        }
    }

    private fun requestNotificationsIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}

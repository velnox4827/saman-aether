package com.saman.tunnel

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_SAVE_DIAGNOSTICS = 2002
    }


    private lateinit var modeView: TextView
    private lateinit var statusView: TextView
    private lateinit var statusBadge: TextView
    private lateinit var versionView: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            refreshState()
            handler.postDelayed(this, 800)
        }
    }

    private val blue = Color.rgb(45, 111, 218)
    private val green = Color.rgb(22, 157, 101)
    private val purple = Color.rgb(111, 55, 205)
    private val red = Color.rgb(211, 54, 54)
    private val orange = Color.rgb(235, 116, 26)
    private val ink = Color.rgb(24, 33, 50)
    private val muted = Color.rgb(100, 108, 122)
    private val line = Color.rgb(224, 228, 235)
    private val canvas = Color.rgb(248, 249, 251)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.rgb(247, 248, 250)

        LogStore.append(this, "APP", "MainActivity created - compact UI v0.3.2")
        buildUi()
        showVersions()
        requestNotificationsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
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
        if (strokeColor != null) {
            setStroke(dp(strokeWidth), strokeColor)
        }
    }

    private fun buildUi() {
        val baseLeft = dp(14)
        val baseTop = dp(8)
        val baseRight = dp(14)
        val baseBottom = dp(10)

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

                view.setPadding(
                    baseLeft,
                    topInset + baseTop,
                    baseRight,
                    baseBottom
                )

                insets
            }

            requestApplyInsets()
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(6))
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.saman_tunnel_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            contentDescription = "Saman Tunnel logo"
            layoutParams = LinearLayout.LayoutParams(dp(68), dp(68)).apply {
                marginEnd = dp(12)
            }
        }
        header.addView(logo)

        val headerText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        headerText.addView(TextView(this).apply {
            text = "Saman Tunnel"
            textSize = 25f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        })

        versionView = TextView(this).apply {
            text = "App v0.3.0  •  Aether Core …"
            textSize = 12.5f
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
            includeFontPadding = false
        }
        headerText.addView(versionView)
        header.addView(headerText)
        root.addView(header)

        // Status card
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.WHITE, 18, line)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(78)
            ).apply {
                bottomMargin = dp(9)
            }
        }

        statusBadge = TextView(this).apply {
            text = "…"
            gravity = Gravity.CENTER
            textSize = 24f
            setTextColor(green)
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(Color.rgb(235, 250, 242), 16, Color.rgb(190, 231, 211))
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                marginEnd = dp(12)
            }
        }
        statusCard.addView(statusBadge)

        val statusColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
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
            textSize = 13.5f
            setTextColor(muted)
            setPadding(0, dp(5), 0, 0)
            includeFontPadding = false
            maxLines = 2
        }

        statusColumn.addView(modeView)
        statusColumn.addView(statusView)
        statusCard.addView(statusColumn)
        root.addView(statusCard)

        // Modes - one row
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(74)
            ).apply {
                bottomMargin = dp(8)
            }
        }

        modeRow.addView(modeTile("◎", "MASQUE", blue, Color.rgb(242, 247, 255)) {
            start("MASQUE")
        })
        modeRow.addView(modeTile("◇", "WireGuard", green, Color.rgb(239, 251, 246)) {
            start("WG")
        })
        modeRow.addView(modeTile("◉", "GOOL", purple, Color.rgb(248, 244, 255)) {
            start("GOOL")
        })
        root.addView(modeRow)

        // Stop / Copy row
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                bottomMargin = dp(8)
            }
        }

        actionRow.addView(actionTile("■  STOP", red, Color.rgb(255, 245, 245)) {
            stop()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = dp(5)
        })

        actionRow.addView(actionTile("▣  Copy SOCKS5", ink, Color.WHITE) {
            copySocks()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            marginStart = dp(5)
        })

        root.addView(actionRow)

        // Diagnostics compact card
        val diagnosticsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 18, line)
            setPadding(dp(10), dp(8), dp(10), dp(9))
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(112)
            ).apply {
                bottomMargin = dp(8)
            }
        }

        diagnosticsCard.addView(TextView(this).apply {
            text = "Diagnostics"
            textSize = 14.5f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
            setPadding(dp(2), 0, 0, dp(7))
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

        diagnosticsRow.addView(diagTile("⇩", "Save TXT", blue) { saveDiagnosticsTxt() })
        diagnosticsRow.addView(diagTile("⌫", "Clear", orange) { clearLogs() })
        diagnosticsCard.addView(diagnosticsRow)
        root.addView(diagnosticsCard)

        // SOCKS card
        val socksCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(244, 248, 255), 18, Color.rgb(177, 205, 247))
            setPadding(dp(14), dp(8), dp(14), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { copySocks() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(68)
            ).apply {
                bottomMargin = dp(6)
            }
        }

        socksCard.addView(TextView(this).apply {
            text = "▦"
            gravity = Gravity.CENTER
            textSize = 27f
            setTextColor(blue)
            background = rounded(Color.rgb(227, 238, 255), 14)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                marginEnd = dp(12)
            }
        })

        val socksText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        socksText.addView(TextView(this).apply {
            text = "SOCKS5"
            textSize = 14f
            setTextColor(blue)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        })

        socksText.addView(TextView(this).apply {
            text = "127.0.0.1:1819"
            textSize = 20f
            setTextColor(ink)
            setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
            includeFontPadding = false
        })

        socksCard.addView(socksText)
        root.addView(socksCard)

        // One-line footer only
        root.addView(TextView(this).apply {
            text = "♢  Bypass only Saman Tunnel in your VPN app."
            textSize = 11.5f
            setTextColor(muted)
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(24)
            )
        })

        setContentView(root)
        refreshState()
    }

    private fun modeTile(
        symbol: String,
        label: String,
        accent: Int,
        fill: Int,
        action: () -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(fill, 17, withAlpha(accent, 90))
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
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(accent)
                includeFontPadding = false
            })

            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = if (label == "WireGuard") 13.5f else 14f
                gravity = Gravity.CENTER
                setTextColor(accent)
                setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
                setPadding(0, dp(3), 0, 0)
            })
        }
    }

    private fun actionTile(
        label: String,
        accent: Int,
        fill: Int,
        action: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(fill, 17, if (accent == ink) line else withAlpha(accent, 90))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun diagTile(
        symbol: String,
        label: String,
        accent: Int,
        action: () -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(251, 252, 254), 14, line)
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
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(accent)
                includeFontPadding = false
            })

            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 11.5f
                gravity = Gravity.CENTER
                setTextColor(ink)
                setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
                setPadding(0, dp(3), 0, 0)
            })
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun showVersions() {
        val appVersion = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "unknown"

        val coreVersion = runCatching {
            val reply = JSONObject(NativeBridge.version())
            if (reply.optBoolean("ok")) {
                reply.optString("version", "unknown")
            } else {
                "unavailable"
            }
        }.getOrElse {
            LogStore.append(this, "NATIVE", "Could not read Aether version: ${it.stackTraceToString()}")
            "unavailable"
        }

        versionView.text = "App v$appVersion  •  Aether Core v$coreVersion"
    }

    private fun start(mode: String) {
        LogStore.append(this, "UI", "Start requested: $mode")
        val intent = Intent(this, AetherService::class.java).apply {
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
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SOCKS5", "127.0.0.1:1819"))
        Toast.makeText(this, "127.0.0.1:1819 copied", Toast.LENGTH_SHORT).show()
    }

    private fun saveDiagnosticsTxt() {
        LogStore.append(this, "UI", "Save diagnostics TXT requested")

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "Saman-Tunnel-diagnostics.txt")
        }

        startActivityForResult(intent, REQUEST_SAVE_DIAGNOSTICS)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_SAVE_DIAGNOSTICS || resultCode != RESULT_OK) {
            return
        }

        val uri = data?.data ?: return

        runCatching {
            contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8).use { writer ->
                requireNotNull(writer) { "Could not open selected file" }
                writer.write(LogStore.diagnostics(this))
            }

            LogStore.append(this, "UI", "Diagnostics saved as TXT")
            Toast.makeText(this, "Diagnostics TXT saved", Toast.LENGTH_SHORT).show()
        }.onFailure {
            LogStore.append(this, "ERROR", "Saving diagnostics TXT failed: ${it.stackTraceToString()}")
            Toast.makeText(this, "Could not save TXT", Toast.LENGTH_LONG).show()
        }
    }

    private fun viewLogs() {
        val textView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 10.5f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            text = LogStore.diagnostics(this@MainActivity)
            setTextColor(ink)
        }

        AlertDialog.Builder(this)
            .setTitle("Saman Tunnel diagnostics")
            .setView(ScrollView(this).apply { addView(textView) })
            .setPositiveButton("Close", null)
            .show()
    }

    private fun shareDiagnostics() {
        LogStore.append(this, "UI", "Diagnostics shared by user")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Saman Tunnel diagnostics")
            putExtra(Intent.EXTRA_TEXT, LogStore.diagnostics(this@MainActivity))
        }
        startActivity(Intent.createChooser(intent, "Share diagnostics"))
    }

    private fun clearLogs() {
        LogStore.clear(this)
        Toast.makeText(this, "Diagnostics cleared", Toast.LENGTH_SHORT).show()
    }

    private fun refreshState() {
        val prefs = getSharedPreferences(AetherService.PREFS, MODE_PRIVATE)
        val mode = prefs.getString(AetherService.KEY_MODE, "") ?: ""
        val status = prefs.getString(AetherService.KEY_STATUS, "Stopped") ?: "Stopped"

        modeView.text = if (mode.isBlank()) "Mode: —" else "Mode: $mode"
        statusView.text = "Status: $status"

        when {
            status.startsWith("Connected", ignoreCase = true) -> {
                statusBadge.text = "✓"
                statusBadge.setTextColor(green)
                statusBadge.background = rounded(
                    Color.rgb(235, 250, 242),
                    16,
                    Color.rgb(190, 231, 211)
                )
                statusView.setTextColor(green)
            }

            status.startsWith("Error", ignoreCase = true) -> {
                statusBadge.text = "!"
                statusBadge.setTextColor(red)
                statusBadge.background = rounded(
                    Color.rgb(255, 239, 239),
                    16,
                    Color.rgb(244, 191, 191)
                )
                statusView.setTextColor(red)
            }

            status.contains("Connecting", ignoreCase = true) ||
                status.contains("Starting", ignoreCase = true) ||
                status.contains("Reconnecting", ignoreCase = true) -> {
                statusBadge.text = "…"
                statusBadge.setTextColor(blue)
                statusBadge.background = rounded(
                    Color.rgb(238, 246, 255),
                    16,
                    Color.rgb(188, 213, 245)
                )
                statusView.setTextColor(blue)
            }

            else -> {
                statusBadge.text = "○"
                statusBadge.setTextColor(muted)
                statusBadge.background = rounded(
                    Color.rgb(244, 246, 249),
                    16,
                    line
                )
                statusView.setTextColor(muted)
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


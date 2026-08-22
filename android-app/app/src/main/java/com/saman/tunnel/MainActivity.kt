package com.saman.tunnel

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var modeView: TextView
    private lateinit var versionView: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            refreshState()
            handler.postDelayed(this, 800)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogStore.append(this, "APP", "MainActivity created")
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

    private fun buildUi() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "Saman Tunnel"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "Standalone local proxy core — no Termux required"
            textSize = 15f
            setPadding(0, dp(6), 0, dp(8))
        })

        versionView = TextView(this).apply {
            textSize = 13f
            setPadding(0, 0, 0, dp(18))
        }
        root.addView(versionView)

        modeView = TextView(this).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(modeView)

        statusView = TextView(this).apply {
            textSize = 16f
            setPadding(0, dp(6), 0, dp(20))
        }
        root.addView(statusView)

        root.addView(makeButton("Start MASQUE") { start("MASQUE") })
        root.addView(makeButton("Start WireGuard") { start("WG") })
        root.addView(makeButton("Start GOOL") { start("GOOL") })
        root.addView(makeButton("STOP") { stop() })
        root.addView(makeButton("Copy SOCKS5 address") { copySocks() })

        root.addView(TextView(this).apply {
            text = "Diagnostics"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(8))
        })

        root.addView(makeButton("View logs") { viewLogs() })
        root.addView(makeButton("Share diagnostics") { shareDiagnostics() })
        root.addView(makeButton("Clear logs") { clearLogs() })

        root.addView(TextView(this).apply {
            text = "Logs stay on this device unless you choose Share diagnostics. Logs may contain connection endpoints, IP addresses and error details."
            textSize = 12f
            setPadding(0, dp(4), 0, dp(12))
        })

        root.addView(TextView(this).apply {
            text = "SOCKS5\n127.0.0.1:1819"
            textSize = 18f
            gravity = Gravity.CENTER_HORIZONTAL
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(12))
        })

        root.addView(TextView(this).apply {
            text = "In your VPN app, bypass/exclude only Saman Tunnel. Termux can stay routed normally."
            textSize = 14f
        })

        setContentView(ScrollView(this).apply { addView(root) })
        refreshState()
    }

    private fun makeButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (8 * resources.displayMetrics.density).toInt()
        }
    }

    private fun showVersions() {
        val appVersion = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "unknown"

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

    private fun viewLogs() {
        val textView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(24, 20, 24, 20)
            text = LogStore.diagnostics(this@MainActivity)
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
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}

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
import android.net.VpnService
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
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_SAVE_DIAGNOSTICS = 2002
        private const val REQUEST_VPN_PERMISSION = 2003
        private const val REQUEST_APP_ROUTING = 2004
        private const val RELEASES_API =
            "https://api.github.com/repos/velnox4827/saman-aether/releases?per_page=10"
        private const val PROJECT_CHANNEL = "https://t.me/SamanTunnelOfficial"
        private const val PROJECT_GROUP = "https://t.me/SamanTunnel"
    }

    // ---- UI state (glass redesign) ----
    private lateinit var tk: Tk
    private val ui by lazy { UiPrefs(this) }
    private val corePrefs by lazy { getSharedPreferences(AetherService.PREFS, MODE_PRIVATE) }
    private val vpnPrefs by lazy { getSharedPreferences(SamanVpnService.PREFS, MODE_PRIVATE) }

    private lateinit var orbs: OrbsView
    private lateinit var powerSwitch: PowerSwitchView
    private lateinit var titleView: TextView
    private lateinit var subView: TextView
    private lateinit var detailView: TextView
    private lateinit var timerView: TextView
    private lateinit var badgeView: TextView
    private lateinit var protoValue: TextView
    private lateinit var connValue: TextView
    private lateinit var versionView: TextView
    private lateinit var coreView: TextView
    private lateinit var batteryView: TextView
    private lateinit var updateView: TextView
    private lateinit var routingView: TextView
    private lateinit var connSeg: SegmentView
    private var sheetCtl: SheetController? = null
    private val modeChips = HashMap<String, LinearLayout>()
    private val modeColors = HashMap<String, Int>()
    private var selectedChip = ""
    private var popLayer: View? = null
    private var popVisible = false
    private var topInset = 0
    private var bottomInset = 0
    private var renderKey = ""
    private var connectedSince = 0L
    private var nextDelay = 1000L
    private var resumed = false
    private var updateChecking = false
    private var staleSinceCheck = true
    private var coreVersionCache: String? = null

    private var pendingVpnMode: String? = null
    private var deferredVpnMode: String? = null
    private var vpnPrepareBusy = false
    private var vpnStartDispatched = false

    private val handler = Handler(Looper.getMainLooper())
    private var lastStartTap = 0L
    private var lastStopTap = 0L
    private var pendingDiagnosticsFull = false
    private var modeSwitchGeneration = 0L
    private var pendingModeSwitch: Runnable? = null

    private val refresh = object : Runnable {
        override fun run() {
            refreshState()
            handler.postDelayed(this, nextDelay)
        }
    }

    private val isDark: Boolean
        get() = when (ui.theme) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }

    private val isFa: Boolean
        get() = when (ui.lang) {
            "fa" -> true
            "en" -> false
            else -> Locale.getDefault().language == "fa"
        }

    private fun tr(fa: String, en: String): String = if (isFa) fa else en

    private val ink: Int get() = tk.ink
    private val green: Int get() = tk.ok
    private val orange: Int get() = tk.warn

    private data class ModeDef(val id: String, val label: String, val color: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashRecorder.install(this)
        deferredVpnMode = savedInstanceState?.getString("deferred_vpn")
        pendingVpnMode = savedInstanceState?.getString("pending_vpn")

        // v1.10.0 mapped legacy GOOL choices to WG, losing the user's choice.
        // Version gate keeps intentional v1.9.2 WG selections unchanged.
        getSharedPreferences(AetherService.PREFS, MODE_PRIVATE).let { prefs ->
            val migrationKey = "gool_restored_from_v110"
            if (!prefs.getBoolean(migrationKey, false)) {
                val previousVersion = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(packageName, 0).versionName
                    }
                }.getOrNull()
                if (previousVersion == "1.10.0") {
                    val edit = prefs.edit()
                    if (prefs.getString(AetherService.KEY_LAST_MODE, "") == "WG") {
                        edit.putString(AetherService.KEY_LAST_MODE, "GOOL")
                    }
                    if (prefs.getString(AetherService.KEY_MODE, "") == "WG") {
                        edit.putString(AetherService.KEY_MODE, "GOOL")
                    }
                    edit.putBoolean(migrationKey, true).apply()
                }
            }
        }

        tk = Tk(isDark, ui.palette)
        applyWindow()

        LogStore.append(this, "APP", "MainActivity created")
        buildUi()
        showVersions()
        refreshBatteryStatus()
        requestNotificationsIfNeeded()

        if (savedInstanceState == null && ui.autoConnect && corePhase() == TunnelPhase.STOPPED) {
            handler.postDelayed({
                if (!isFinishing && !isDestroyed && corePhase() == TunnelPhase.STOPPED) {
                    start(lastModeValue())
                }
            }, 700L)
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (::orbs.isInitialized) orbs.start()
        refreshBatteryStatus()
        handler.post(refresh)
        if (intent?.action == "com.saman.tunnel.QUICK_CONNECT") {
            intent.action = Intent.ACTION_MAIN
            val mode = AetherArguments.canonicalMode(
                getSharedPreferences(AetherService.PREFS, MODE_PRIVATE)
                    .getString(AetherService.KEY_LAST_MODE, "WG").orEmpty().ifBlank { "WG" }
            )
            start(mode)
        }
        if (getSharedPreferences(SamanVpnService.PREFS, MODE_PRIVATE)
                .getBoolean(SamanVpnService.KEY_RUNNING, false)) {
            runCatching { startService(Intent(this, SamanVpnService::class.java).apply {
                action = SamanVpnService.ACTION_QUERY
            }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("deferred_vpn", deferredVpnMode)
        outState.putString("pending_vpn", pendingVpnMode)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        LogStore.append(this, "APP", "MainActivity paused")
        resumed = false
        if (::orbs.isInitialized) orbs.stop()
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onDestroy() {
        cancelPendingModeSwitch()
        super.onDestroy()
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

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    @Suppress("DEPRECATION")
    private fun applyWindow() {
        window.statusBarColor = tk.bg
        window.navigationBarColor = tk.bg
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (tk.dark) 0 else mask, mask)
        } else {
            val decor = window.decorView
            var f = decor.systemUiVisibility
            f = if (tk.dark) f and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            else f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                f = if (tk.dark) f and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                else f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            decor.systemUiVisibility = f
        }
    }

    private fun readInsets(insets: WindowInsets) {
        topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.statusBars()).top
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetTop
        }
        bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
    }

    private fun buildUi() {
        tk = Tk(isDark, ui.palette)
        applyWindow()
        modeChips.clear()
        modeColors.clear()
        selectedChip = ""
        popVisible = false
        renderKey = ""

        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val screenW = resources.displayMetrics.widthPixels
        val maxW = minOf(screenW, dp(420))

        val root = FrameLayout(this).apply {
            setBackgroundColor(tk.bg)
            layoutDirection = if (isFa) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }
        orbs = OrbsView(this)
        root.addView(orbs, FrameLayout.LayoutParams(mp, mp))

        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(main, FrameLayout.LayoutParams(maxW, mp, Gravity.CENTER_HORIZONTAL))

        // ---- header ----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        brand.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.saman_tunnel_logo)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = "Saman Tunnel"
            },
            lp(dp(36), dp(36)).apply { marginEnd = dp(8) }
        )
        brand.addView(TextView(this).apply {
            text = if (packageName.endsWith(".beta")) "SAMAN TUNNEL β" else "SAMAN TUNNEL"
            textSize = 15f
            setTextColor(tk.ink)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.12f
            includeFontPadding = false
        })
        badgeView = TextView(this).apply {
            text = "WG"
            textSize = 11f
            setTextColor(tk.sky)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.08f
            includeFontPadding = false
            setPadding(dp(9), dp(3), dp(9), dp(3))
            background = rounded(withAlpha(tk.sky, 41), 99)
        }
        brand.addView(badgeView, lp(wrap, wrap).apply { marginStart = dp(8) })
        header.addView(brand, lp(wrap, wrap))
        header.addView(View(this), lp(0, 1, 1f))
        header.addView(
            TextView(this).apply {
                text = "◐"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(tk.ink)
                background = rounded(tk.glass, 22, tk.glassB)
                isClickable = true
                isFocusable = true
                contentDescription = tr("ظاهر برنامه", "Appearance")
                setOnClickListener { if (popVisible) hidePop() else showPop() }
            },
            lp(dp(44), dp(44))
        )
        main.addView(header, lp(mp, dp(52)))

        // ---- hero ----
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        titleView = TextView(this).apply {
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(tk.ink)
            setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = false
        }
        subView = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(tk.muted)
        }
        timerView = TextView(this).apply {
            text = "00:00:00"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(tk.ok)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            fontFeatureSettings = "tnum"
            visibility = View.INVISIBLE
        }
        detailView = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(tk.muted)
            textDirection = View.TEXT_DIRECTION_LTR
            visibility = View.GONE
        }
        hero.addView(titleView, lp(mp, wrap))
        hero.addView(subView, lp(mp, wrap).apply { topMargin = dp(8) })
        hero.addView(timerView, lp(mp, wrap).apply { topMargin = dp(6) })
        hero.addView(detailView, lp(mp, wrap).apply { topMargin = dp(4) })
        powerSwitch = PowerSwitchView(this).apply {
            applyTheme(tk)
            setOnClickListener { onSwitchTap() }
        }
        hero.addView(powerSwitch, lp(wrap, wrap).apply { topMargin = dp(14) })
        main.addView(hero, lp(mp, 0, 1f))

        // ---- scrim + bottom sheet ----
        val scrim = View(this).apply {
            setBackgroundColor(tk.scrim)
            alpha = 0f
        }
        root.addView(scrim, FrameLayout.LayoutParams(mp, mp))

        val sheetH = minOf((resources.displayMetrics.heightPixels * 0.88f).toInt(), dp(720))
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.INVISIBLE
            elevation = dp(16).toFloat()
            background = GradientDrawable().apply {
                setColor(tk.sheet)
                val r = dp(28).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setStroke(dp(1), tk.glassB)
            }
        }
        root.addView(
            sheet,
            FrameLayout.LayoutParams(maxW, sheetH, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        )

        val grab = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            contentDescription = tr("تنظیمات", "Settings")
            isFocusable = true
        }
        grab.addView(
            View(this).apply { background = rounded(withAlpha(tk.muted, 115), 3) },
            lp(dp(44), dp(5)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(12)
            }
        )
        protoValue = valueLabel()
        connValue = valueLabel()
        val tiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tiles.addView(tile(tr("پروتکل", "Protocol"), protoValue), lp(0, wrap, 1f).apply { marginEnd = dp(5) })
        tiles.addView(tile(tr("اتصال", "Connection"), connValue), lp(0, wrap, 1f).apply { marginStart = dp(5) })
        grab.addView(tiles, lp(mp, wrap))
        sheet.addView(grab, lp(mp, wrap))

        val body = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(content, ViewGroup.LayoutParams(mp, wrap))
        sheet.addView(body, lp(mp, 0, 1f))
        fillSettings(content)
        sheetCtl = SheetController(sheet, grab, scrim, body)

        // ---- appearance popover ----
        val edge = (screenW - maxW) / 2
        val layer = FrameLayout(this).apply {
            visibility = View.GONE
            isClickable = true
            setOnClickListener { hidePop() }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(tk.sheet, 22, tk.glassB)
            elevation = dp(8).toFloat()
            isClickable = true
        }
        val themeSeg = SegmentView(this, tk).apply {
            setItems(listOf(tr("روشن", "Light"), tr("تاریک", "Dark"), tr("خودکار", "Auto")))
            select(
                when (ui.theme) {
                    "light" -> 0
                    "dark" -> 1
                    else -> 2
                }
            )
            onSelect = { i ->
                ui.theme = arrayOf("light", "dark", "system")[i]
                rebuildUi()
            }
        }
        card.addView(themeSeg, lp(mp, wrap))
        val pals = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Palette.values().forEachIndexed { i, p ->
            val sel = p == ui.palette
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(10), dp(2), dp(10))
                background = rounded(tk.tile, 16, if (sel) tk.sky else tk.line, if (sel) 2 else 1)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    ui.palette = p
                    rebuildUi()
                }
            }
            item.addView(SwatchView(this, p.swA, p.swB), lp(dp(28), dp(28)))
            item.addView(
                TextView(this).apply {
                    text = paletteName(p)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(tk.ink)
                },
                lp(wrap, wrap).apply { topMargin = dp(6) }
            )
            pals.addView(item, lp(0, wrap, 1f).apply { if (i > 0) marginStart = dp(6) })
        }
        card.addView(pals, lp(mp, wrap).apply { topMargin = dp(8) })
        layer.addView(
            card,
            FrameLayout.LayoutParams(
                minOf(dp(300), maxW - dp(40)), wrap, Gravity.TOP or Gravity.END
            ).apply { marginEnd = edge + dp(20) }
        )
        root.addView(layer, FrameLayout.LayoutParams(mp, mp))
        popLayer = layer

        fun applyInsets() {
            main.setPadding(dp(20), topInset + dp(6), dp(20), bottomInset + dp(150))
            grab.setPadding(dp(18), dp(10), dp(18), dp(14) + bottomInset)
            content.setPadding(dp(20), dp(6), dp(20), dp(24) + bottomInset)
            (card.layoutParams as FrameLayout.LayoutParams).topMargin = topInset + dp(64)
            card.requestLayout()
        }
        root.setOnApplyWindowInsetsListener { _, insets ->
            readInsets(insets)
            applyInsets()
            insets
        }
        applyInsets()
        setContentView(root)
        root.requestApplyInsets()
        if (resumed) orbs.start()
        refreshState(true)
    }

    private fun rebuildUi() {
        val sheetOpen = sheetCtl?.open == true
        val popOpen = popVisible
        if (::orbs.isInitialized) orbs.stop()
        buildUi()
        showVersions()
        refreshBatteryStatus()
        sheetCtl?.open = sheetOpen
        if (popOpen) showPop()
    }

    private fun showPop() {
        popLayer?.visibility = View.VISIBLE
        popVisible = true
    }

    private fun hidePop() {
        popLayer?.visibility = View.GONE
        popVisible = false
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        when {
            popVisible -> hidePop()
            sheetCtl?.open == true -> sheetCtl?.snap(false)
            else -> super.onBackPressed()
        }
    }

    private fun paletteName(p: Palette): String = when (p) {
        Palette.BLUE -> tr("آبی و سفید", "Blue & white")
        Palette.PURPLE -> tr("بنفش نئونی", "Neon purple")
        Palette.ORANGE -> tr("نارنجی و سفید", "Orange & white")
    }

    private fun valueLabel(): TextView = TextView(this).apply {
        textSize = 16f
        setTextColor(tk.ink)
        setTypeface(typeface, Typeface.BOLD)
        textDirection = View.TEXT_DIRECTION_LTR
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        includeFontPadding = false
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun tile(label: String, value: TextView): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = rounded(tk.tile, 18, tk.line)
        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 12.5f
            setTextColor(tk.muted)
        })
        addView(value, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2)
        })
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(tk.muted)
        setPadding(0, dp(16), 0, dp(8))
    }

    private fun groupTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        letterSpacing = 0.08f
        setTextColor(tk.muted)
        setTypeface(typeface, Typeface.BOLD)
        textDirection = View.TEXT_DIRECTION_LTR
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        setPadding(dp(2), dp(4), 0, dp(6))
    }

    private fun divider(): View = View(this).apply { setBackgroundColor(tk.line) }

    private fun chip(text: String, action: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(tk.ink)
        setPadding(dp(16), 0, dp(16), 0)
        minHeight = dp(38)
        background = rounded(tk.tile, 19, tk.line)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun valueText(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(tk.muted)
        textDirection = View.TEXT_DIRECTION_LTR
    }

    private fun row(title: String, sub: String?, trailing: View): LinearLayout {
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(tk.ink)
        })
        if (sub != null) {
            col.addView(
                TextView(this).apply {
                    text = sub
                    textSize = 12.5f
                    setTextColor(tk.muted)
                },
                lp(wrap, wrap).apply { topMargin = dp(2) }
            )
        }
        r.addView(col, lp(0, wrap, 1f))
        r.addView(trailing, lp(wrap, wrap).apply { marginStart = dp(12) })
        return r
    }

    private fun modeGroups(): List<Pair<String, List<ModeDef>>> {
        val masque = Color.rgb(58, 141, 255)
        val wg = Color.rgb(47, 194, 122)
        val gool = Color.rgb(139, 108, 255)
        val psi = Color.rgb(232, 93, 155)
        val tor = Color.rgb(255, 154, 61)
        return listOf(
            "MASQUE" to listOf(
                ModeDef("MASQUE_H3", "MASQUE H3", masque),
                ModeDef("MASQUE_H2", "MASQUE H2", masque),
                ModeDef("MIM_H3", "MiM H3", masque),
                ModeDef("MIM_H2", "MiM H2", masque)
            ),
            "WireGuard" to listOf(
                ModeDef("WG", "WireGuard", wg),
                ModeDef("GOOL", "GOOL", gool)
            ),
            "Psiphon" to listOf(ModeDef("PSIPHON_ONLY", "Psiphon", psi)),
            "Tor" to listOf(
                ModeDef("TOR_ONLY", "Tor only", tor),
                ModeDef("TOR_INSIDE_MASQUE_H3", "MASQUE H3 → Tor", tor),
                ModeDef("TOR_INSIDE_MASQUE_H2", "MASQUE H2 → Tor", tor),
                ModeDef("TOR_INSIDE_WG", "WireGuard → Tor", tor),
                ModeDef("TOR_INSIDE_GOOL", "GOOL → Tor", tor),
                ModeDef("TOR_REVERSE", "Tor → MASQUE H2", tor)
            )
        )
    }

    private fun modeChip(def: ModeDef): View {
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(14), 0, dp(10), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { onModeChip(def.id) }
        }
        v.addView(
            View(this).apply { background = rounded(def.color, 5) },
            lp(dp(10), dp(10)).apply { marginEnd = dp(10) }
        )
        v.addView(
            TextView(this).apply {
                text = def.label
                textSize = 13.5f
                setTextColor(tk.ink)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 2
            },
            lp(0, wrap, 1f)
        )
        modeChips[def.id] = v
        modeColors[def.id] = def.color
        styleChip(def.id, false)
        return v
    }

    private fun styleChip(id: String, selected: Boolean) {
        val v = modeChips[id] ?: return
        val c = modeColors[id] ?: tk.sky
        v.background = if (selected) rounded(withAlpha(c, 41), 18, c, 2)
        else rounded(tk.tile, 18, tk.line)
    }

    private fun fillSettings(c: LinearLayout) {
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        c.addView(
            TextView(this).apply {
                text = tr("تنظیمات", "Settings")
                textSize = 19f
                setTextColor(tk.ink)
                setTypeface(typeface, Typeface.BOLD)
            },
            lp(mp, wrap).apply {
                topMargin = dp(6)
                bottomMargin = dp(2)
            }
        )

        c.addView(label(tr("مود اتصال", "Connection mode")))
        for ((group, defs) in modeGroups()) {
            c.addView(groupTitle(group))
            var i = 0
            while (i < defs.size) {
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                r.addView(modeChip(defs[i]), lp(0, dp(52), 1f).apply { marginEnd = dp(5) })
                if (i + 1 < defs.size) {
                    r.addView(modeChip(defs[i + 1]), lp(0, dp(52), 1f).apply { marginStart = dp(5) })
                } else {
                    r.addView(View(this), lp(0, dp(52), 1f).apply { marginStart = dp(5) })
                }
                c.addView(r, lp(mp, wrap).apply { bottomMargin = dp(10) })
                i += 2
            }
        }

        c.addView(label(tr("نوع اتصال", "Connection type")))
        connSeg = SegmentView(this, tk).apply {
            setItems(listOf(tr("پراکسی", "Proxy"), tr("وی‌پی‌ان", "VPN")))
            onSelect = { i -> applyConnectionMode(i) }
        }
        c.addView(connSeg, lp(mp, wrap))
        routingView = chip("…") {
            startActivityForResult(Intent(this, AppRoutingActivity::class.java), REQUEST_APP_ROUTING)
        }
        c.addView(row(tr("برنامه‌ها", "Apps"), null, routingView))

        val proxyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(tk.tile, 18, tk.line)
        }
        proxyCard.addView(TextView(this).apply {
            text = tr("پراکسی محلی", "Local proxy")
            textSize = 13f
            setTextColor(tk.sky)
            setTypeface(typeface, Typeface.BOLD)
        })
        for ((k, v) in listOf("SOCKS5" to "127.0.0.1:1819", "HTTP" to "127.0.0.1:1820")) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                setPadding(0, dp(5), 0, dp(1))
            }
            line.addView(TextView(this).apply {
                text = k
                textSize = 14f
                typeface = Typeface.MONOSPACE
                setTextColor(tk.muted)
            }, lp(0, wrap, 1f))
            line.addView(TextView(this).apply {
                text = v
                textSize = 14f
                typeface = Typeface.MONOSPACE
                setTextColor(tk.ink)
            })
            proxyCard.addView(line, lp(mp, wrap))
        }
        proxyCard.addView(
            chip(tr("کپی پراکسی‌ها", "Copy proxies")) { copySocks() },
            lp(mp, wrap).apply { topMargin = dp(8) }
        )
        c.addView(proxyCard, lp(mp, wrap).apply { topMargin = dp(8) })

        c.addView(label(tr("عمومی", "General")))
        val auto = ToggleView(this, tk).apply {
            setChecked(ui.autoConnect, false)
            onChange = { v -> ui.autoConnect = v }
        }
        c.addView(row(
            tr("اتصال خودکار", "Auto-connect"),
            tr("با باز شدن برنامه وصل شو", "Connect when the app opens"),
            auto
        ))
        c.addView(divider(), lp(mp, dp(1)))
        val langSeg = SegmentView(this, tk).apply {
            setItems(listOf(tr("خودکار", "Auto"), "فارسی", "English"))
            select(
                when (ui.lang) {
                    "fa" -> 1
                    "en" -> 2
                    else -> 0
                }
            )
            onSelect = { i ->
                ui.lang = arrayOf("auto", "fa", "en")[i]
                rebuildUi()
            }
        }
        c.addView(label(tr("زبان", "Language")))
        c.addView(langSeg, lp(mp, wrap))

        c.addView(label(tr("عیب‌یابی", "Diagnostics")))
        val diag = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        diag.addView(chip(tr("لاگ‌ها", "Logs")) { showQuickLog(40) }, lp(0, dp(42), 1f).apply { marginEnd = dp(5) })
        diag.addView(chip(tr("ذخیره TXT", "Save TXT")) { chooseDiagnosticsExport() }, lp(0, dp(42), 1f).apply { marginStart = dp(5) })
        c.addView(diag, lp(mp, wrap))

        c.addView(label(tr("درباره", "About")))
        versionView = valueText("…")
        coreView = valueText("…")
        batteryView = chip("…") { openBatterySettings() }
        updateView = chip(tr("بررسی", "Check")) { checkForUpdates() }
        c.addView(row(tr("نسخه برنامه", "App version"), null, versionView))
        c.addView(divider(), lp(mp, dp(1)))
        c.addView(row(tr("هسته", "Core"), null, coreView))
        c.addView(divider(), lp(mp, dp(1)))
        c.addView(row(tr("باتری", "Battery"), null, batteryView))
        c.addView(divider(), lp(mp, dp(1)))
        c.addView(row(tr("به‌روزرسانی", "Updates"), null, updateView))
        val links = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        links.addView(chip(tr("کانال", "Channel")) { openProjectLink(PROJECT_CHANNEL) }, lp(0, dp(42), 1f).apply { marginEnd = dp(5) })
        links.addView(chip(tr("گروه", "Group")) { openProjectLink(PROJECT_GROUP) }, lp(0, dp(42), 1f).apply { marginStart = dp(5) })
        c.addView(links, lp(mp, wrap).apply { topMargin = dp(10) })
    }

    private fun corePhase(): TunnelPhase =
        TunnelPhase.fromStatus(corePrefs.getString(AetherService.KEY_STATUS, "Stopped").orEmpty())

    private fun lastModeValue(): String = AetherArguments.canonicalMode(
        corePrefs.getString(AetherService.KEY_LAST_MODE, "WG").orEmpty().ifBlank { "WG" }
    )

    private fun normMode(m: String): String = when (val u = m.trim().uppercase()) {
        "MASQUE" -> "MASQUE_H3"
        "MIM" -> "MIM_H3"
        "TOR_INSIDE_MASQUE" -> "TOR_INSIDE_MASQUE_H3"
        else -> u
    }

    private fun onModeChip(mode: String) {
        if (isBusy()) {
            Toast.makeText(this, tr("لطفاً صبر کن تا کار فعلی تمام شود", "Please wait for the current action to finish"), Toast.LENGTH_SHORT).show()
            return
        }
        LogStore.append(this, "UI", "Mode chip tapped: $mode")
        val phase = corePhase()
        if (phase == TunnelPhase.CONNECTED || phase == TunnelPhase.DEGRADED) {
            start(mode)
        } else {
            corePrefs.edit().putString(AetherService.KEY_LAST_MODE, mode).apply()
            refreshState(true)
        }
    }

    private fun onSwitchTap() {
        val raw = corePrefs.getString(AetherService.KEY_STATUS, "Stopped").orEmpty()
        val display = ConnectionStatus.display(
            raw.trim(), isVpnMode(),
            vpnPrefs.getBoolean(SamanVpnService.KEY_RUNNING, false),
            vpnPrefs.getString(SamanVpnService.KEY_STATUS, "Preparing VPN").orEmpty()
        ).lowercase()
        val phase = TunnelPhase.fromStatus(raw)
        LogStore.append(this, "UI", "Power switch tapped status=$display")
        when {
            display.startsWith("connected") || display.startsWith("connection unstable") -> stop()
            display.startsWith("connecting") || display.startsWith("starting") ||
                display.startsWith("switching") -> stop()
            display.startsWith("stopping") ->
                Toast.makeText(this, tr("در حال قطع اتصال…", "Disconnecting…"), Toast.LENGTH_SHORT).show()
            display.startsWith("error") &&
                (phase == TunnelPhase.CONNECTED || phase == TunnelPhase.DEGRADED) && isVpnMode() ->
                start(corePrefs.getString(AetherService.KEY_MODE, "").orEmpty().ifBlank { lastModeValue() })
            else -> start(lastModeValue())
        }
    }

    private fun openProjectLink(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show() }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun showVersions() {
        val appVersion = currentVersion()
        val coreVersion = coreVersionCache ?: runCatching {
            val reply = JSONObject(NativeBridge.version())
            if (reply.optBoolean("ok")) reply.optString("version", "unknown") else "unavailable"
        }.getOrElse {
            LogStore.append(this, "NATIVE", "Could not read Aether version: ${it.stackTraceToString()}")
            "unavailable"
        }.also {
            coreVersionCache = it
            LogStore.append(this, "VERSION", "app=$appVersion aether=$it")
        }

        versionView.text = "v$appVersion"
        coreView.text = "Aether Core v$coreVersion"
    }

    private fun isBusy(): Boolean {
        val phase = getSharedPreferences(AetherService.PREFS, MODE_PRIVATE)
            .getString(AetherService.KEY_PHASE, TunnelPhase.STOPPED.name)
            ?.let { runCatching { TunnelPhase.valueOf(it) }.getOrNull() }
            ?: TunnelPhase.STOPPED
        return phase.isBusy
    }

    private fun start(mode: String) {
        val mode = AetherArguments.canonicalMode(mode)
        if (isBusy()) {
            Toast.makeText(
                this,
                "Connection action already in progress",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        cancelPendingModeSwitch()

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

        val phase = TunnelPhase.fromStatus(status)
        if (phase == TunnelPhase.CONNECTED || phase == TunnelPhase.DEGRADED) {
            val current = prefs.getString(AetherService.KEY_MODE, "")
            if (current == mode) {
                val vpn = getSharedPreferences(SamanVpnService.PREFS, MODE_PRIVATE)
                if (isVpnMode() && !vpn.getBoolean(SamanVpnService.KEY_RUNNING, false)) {
                    vpnStartDispatched = false
                    requestVpnPermissionAndStart(mode)
                } else Toast.makeText(this, "Already connected — use Stop to disconnect", Toast.LENGTH_SHORT).show()
                return
            }
            LogStore.append(
                this,
                "UI",
                "Mode switch requested -> $mode; waiting for confirmed stop"
            )

            val switchGeneration = modeSwitchGeneration
            stop(cancelPendingSwitch = false)
            restartWhenStopped(mode, 30, switchGeneration)
        } else {
            launchCoreService(mode)
        }
    }

    private fun restartWhenStopped(
        mode: String,
        attemptsRemaining: Int,
        switchGeneration: Long,
        oldProcessStopped: Boolean = false
    ) {
        val callback = Runnable {
            if (switchGeneration != modeSwitchGeneration || isFinishing || isDestroyed) return@Runnable

            if (oldProcessStopped) {
                pendingModeSwitch = null
                launchCoreService(mode)
                return@Runnable
            }

            val status = getSharedPreferences(AetherService.PREFS, MODE_PRIVATE)
                .getString(AetherService.KEY_STATUS, "Stopped")
                .orEmpty()
            if (TunnelPhase.fromStatus(status) == TunnelPhase.STOPPED) {
                restartWhenStopped(mode, attemptsRemaining, switchGeneration, oldProcessStopped = true)
                return@Runnable
            }
            if (attemptsRemaining <= 0) {
                pendingModeSwitch = null
                LogStore.append(this, "ERROR", "Mode switch timed out waiting for stop")
                Toast.makeText(this, "Previous tunnel did not stop in time", Toast.LENGTH_LONG).show()
                return@Runnable
            }
            restartWhenStopped(mode, attemptsRemaining - 1, switchGeneration)
        }
        pendingModeSwitch = callback
        handler.postDelayed(callback, if (oldProcessStopped) 500L else 200L)
    }

    private fun cancelPendingModeSwitch() {
        modeSwitchGeneration++
        pendingModeSwitch?.let(handler::removeCallbacks)
        pendingModeSwitch = null
    }

    private fun launchCoreService(mode: String) {
        LogStore.append(
            this,
            "UI",
            "Start requested: $mode"
        )

        getSharedPreferences(AetherService.PREFS, MODE_PRIVATE).edit()
            .putString(AetherService.KEY_STATUS, "Starting…")
            .putString(AetherService.KEY_MODE, mode)
            .putString(AetherService.KEY_PHASE, TunnelPhase.STARTING.name).apply()
        val intent =
            Intent(
                this,
                AetherService::class.java
            ).apply {
                action = AetherService.ACTION_START
                putExtra(AetherService.EXTRA_MODE, mode)
            }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (t: Throwable) {
            LogStore.append(
                this,
                "ERROR",
                "Starting AetherService failed: ${t.javaClass.name}: ${t.message.orEmpty()}"
            )
            getSharedPreferences(AetherService.PREFS, MODE_PRIVATE).edit()
                .putString(AetherService.KEY_STATUS, "Error: Core service could not start")
                .putString(AetherService.KEY_PHASE, TunnelPhase.FAILED.name).apply()
            Toast.makeText(
                this,
                "Aether service could not start: ${t.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (isVpnMode()) {
            deferredVpnMode = mode
            vpnPrepareBusy = false
            vpnStartDispatched = false
            getSharedPreferences(SamanVpnService.PREFS, MODE_PRIVATE).edit()
                .putString(SamanVpnService.KEY_STATUS, "Error: VPN could not start — tap mode to retry").apply()
            LogStore.append(
                this,
                "VPN_START",
                "Deferred until Aether reports Connected: ${prettyMode(mode)}"
            )
        }
    }

    private fun stop(cancelPendingSwitch: Boolean = true) {
        if (cancelPendingSwitch) cancelPendingModeSwitch()
        deferredVpnMode = null
        pendingVpnMode = null
        vpnPrepareBusy = false
        vpnStartDispatched = false
        val now = SystemClock.elapsedRealtime()
        if (now - lastStopTap < 800L) return
        lastStopTap = now
        getSharedPreferences(AetherService.PREFS, MODE_PRIVATE).edit()
            .putString(AetherService.KEY_STATUS, "Stopping…")
            .putString(AetherService.KEY_PHASE, TunnelPhase.STOPPING.name).apply()
        LogStore.append(this, "UI", "Stop requested")
        stopVpnService()
        startService(Intent(this, AetherService::class.java).apply {
            action = AetherService.ACTION_STOP
        })
    }

    private fun isVpnMode(): Boolean =
        getSharedPreferences(
            SamanVpnService.PREFS,
            MODE_PRIVATE
        ).getString(
            SamanVpnService.KEY_CONNECTION_MODE,
            SamanVpnService.CONNECTION_PROXY
        ) == SamanVpnService.CONNECTION_VPN

    /** which: 0 = Proxy, 1 = VPN (same behaviour as the former connection-mode dialog). */
    private fun applyConnectionMode(which: Int) {
        if (which == 1) {
            vpnPrefs.edit()
                .putString(SamanVpnService.KEY_CONNECTION_MODE, SamanVpnService.CONNECTION_VPN)
                .apply()

            val status = corePrefs.getString(AetherService.KEY_STATUS, "Stopped").orEmpty()
            val runningMode = corePrefs.getString(AetherService.KEY_MODE, "").orEmpty()

            if (status.startsWith("Connected", true) && runningMode.isNotBlank()) {
                requestVpnPermissionAndStart(runningMode)
            } else {
                if (TunnelPhase.fromStatus(status).isActive) deferredVpnMode = runningMode
                Toast.makeText(
                    this,
                    tr(
                        "وی‌پی‌ان انتخاب شد — برای اتصال کلید را بزن",
                        "VPN selected — tap the power switch to connect"
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            vpnPrefs.edit()
                .putString(SamanVpnService.KEY_CONNECTION_MODE, SamanVpnService.CONNECTION_PROXY)
                .apply()

            deferredVpnMode = null
            pendingVpnMode = null
            vpnPrepareBusy = false
            vpnStartDispatched = false
            stopVpnService()

            Toast.makeText(this, tr("حالت پراکسی انتخاب شد", "Proxy mode selected"), Toast.LENGTH_SHORT).show()
        }

        refreshState(true)
    }

    private fun requestVpnPermissionAndStart(mode: String) {
        if (!isVpnMode() || vpnPrepareBusy || vpnStartDispatched) return
        deferredVpnMode = null
        getSharedPreferences(SamanVpnService.PREFS, MODE_PRIVATE).edit()
            .putBoolean(SamanVpnService.KEY_RUNNING, false)
            .putString(SamanVpnService.KEY_STATUS, "Waiting for VPN permission").apply()

        vpnPrepareBusy = true
        vpnStartDispatched = true

        LogStore.append(
            this,
            "VPN_PERMISSION",
            "prepare begin mode=${prettyMode(mode)}"
        )

        val prepareIntent = try {
            VpnService.prepare(this)
        } catch (t: Throwable) {
            vpnPrepareBusy = false
            vpnStartDispatched = false
            getSharedPreferences(SamanVpnService.PREFS, MODE_PRIVATE).edit()
                .putString(SamanVpnService.KEY_STATUS, "Error: VPN could not start — tap mode to retry").apply()
            LogStore.append(
                this,
                "ERROR",
                "VpnService.prepare failed: ${t.javaClass.name}: ${t.message.orEmpty()}"
            )
            Toast.makeText(
                this,
                "VPN preparation failed: ${t.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        LogStore.append(
            this,
            "VPN_PERMISSION",
            if (prepareIntent == null) {
                "prepare result=already-granted"
            } else {
                "prepare result=consent-required"
            }
        )

        if (prepareIntent == null) {
            vpnPrepareBusy = false
            startVpnService(mode)
            return
        }

        pendingVpnMode = mode

        try {
            startActivityForResult(
                prepareIntent,
                REQUEST_VPN_PERMISSION
            )
        } catch (t: Throwable) {
            pendingVpnMode = null
            vpnPrepareBusy = false
            vpnStartDispatched = false
            getSharedPreferences(SamanVpnService.PREFS, MODE_PRIVATE).edit()
                .putString(SamanVpnService.KEY_STATUS, "Error: VPN could not start — tap mode to retry").apply()
            LogStore.append(
                this,
                "ERROR",
                "VPN consent activity failed: ${t.javaClass.name}: ${t.message.orEmpty()}"
            )
            Toast.makeText(
                this,
                "Could not open Android VPN permission screen",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startVpnService(mode: String, reconfigure: Boolean = false) {
        pendingVpnMode = null
        vpnPrepareBusy = false
        deferredVpnMode = null
        val coreStatus = getSharedPreferences(AetherService.PREFS, MODE_PRIVATE)
            .getString(AetherService.KEY_STATUS, "Stopped").orEmpty()
        if (!isVpnMode() || TunnelPhase.fromStatus(coreStatus) !in
                listOf(TunnelPhase.CONNECTED, TunnelPhase.DEGRADED)) {
            vpnStartDispatched = false
            return
        }
        vpnStartDispatched = true

        val routingPrefs = getSharedPreferences(
            SamanVpnService.PREFS,
            MODE_PRIVATE
        )

        val routingMode =
            routingPrefs.getString(
                SamanVpnService.KEY_ROUTING_MODE,
                SamanVpnService.ROUTING_ALL
            ) ?: SamanVpnService.ROUTING_ALL

        val selectedApps =
            routingPrefs.getStringSet(
                SamanVpnService.KEY_SELECTED_APPS,
                emptySet()
            )?.toSet() ?: emptySet()

        LogStore.append(
            this,
            "VPN_START",
            "service dispatch begin for ${prettyMode(mode)} routing=$routingMode selectedCount=${selectedApps.size}"
        )

        val intent = Intent(
            this,
            SamanVpnService::class.java
        ).apply {
            action = if (reconfigure) SamanVpnService.ACTION_RECONFIGURE else SamanVpnService.ACTION_START
            putExtra(AetherService.EXTRA_MODE, mode)
            putExtra(
                SamanVpnService.EXTRA_ROUTING_MODE,
                routingMode
            )
            putStringArrayListExtra(
                SamanVpnService.EXTRA_SELECTED_APPS,
                ArrayList(selectedApps)
            )
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            LogStore.append(
                this,
                "VPN_START",
                "service dispatch returned successfully"
            )
        } catch (t: Throwable) {
            vpnStartDispatched = false
            getSharedPreferences(SamanVpnService.PREFS, MODE_PRIVATE).edit()
                .putString(SamanVpnService.KEY_STATUS, "Error: VPN could not start — tap mode to retry").apply()
            LogStore.append(
                this,
                "ERROR",
                "Starting SamanVpnService failed: ${t.javaClass.name}: ${t.message.orEmpty()}"
            )
            Toast.makeText(
                this,
                "VPN service failed to start: ${t.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun reapplyVpnRouting() {
        if (!isVpnMode()) return

        val coreStatus = getSharedPreferences(AetherService.PREFS, MODE_PRIVATE)
            .getString(AetherService.KEY_STATUS, "Stopped").orEmpty()
        if (TunnelPhase.fromStatus(coreStatus) !in listOf(TunnelPhase.CONNECTED, TunnelPhase.DEGRADED)) {
            Toast.makeText(
                this,
                "Routing saved — it will apply on the next VPN connection",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val aetherPrefs = getSharedPreferences(
            AetherService.PREFS,
            MODE_PRIVATE
        )

        val mode =
            aetherPrefs.getString(AetherService.KEY_MODE, "")
                .orEmpty()
                .ifBlank {
                    aetherPrefs.getString(AetherService.KEY_LAST_MODE, "WG")
                        .orEmpty()
                        .ifBlank { "WG" }
                }

        LogStore.append(
            this,
            "APP_ROUTING",
            "Hot-applying routing without restarting Aether mode=$mode"
        )

        // Reconfiguration closes and recreates the bridge on its single worker;
        // no fixed-delay restart can overtake a previous Stop.
        startVpnService(mode, reconfigure = true)
    }

    private fun stopVpnService() {
        getSharedPreferences(SamanVpnService.PREFS, MODE_PRIVATE).edit()
            .putBoolean(SamanVpnService.KEY_RUNNING, false)
            .putString(SamanVpnService.KEY_STATUS, "Stopping…").apply()
        runCatching {
            startService(
                Intent(
                    this,
                    SamanVpnService::class.java
                ).apply {
                    action = SamanVpnService.ACTION_STOP
                }
            )
        }
    }

    private fun refreshVpnControls() {
        if (!::connSeg.isInitialized || !::routingView.isInitialized || !::connValue.isInitialized) return

        val vpnSelected = vpnPrefs.getString(
            SamanVpnService.KEY_CONNECTION_MODE,
            SamanVpnService.CONNECTION_PROXY
        ) == SamanVpnService.CONNECTION_VPN
        val vpnRunning = vpnPrefs.getBoolean(SamanVpnService.KEY_RUNNING, false)

        connSeg.select(if (vpnSelected) 1 else 0)
        val conn = when {
            vpnSelected && vpnRunning -> "VPN ✓"
            vpnSelected -> tr("وی‌پی‌ان", "VPN")
            else -> tr("پراکسی", "Proxy")
        }
        if (connValue.text.toString() != conn) connValue.text = conn

        val routingMode = vpnPrefs.getString(
            SamanVpnService.KEY_ROUTING_MODE,
            SamanVpnService.ROUTING_ALL
        ) ?: SamanVpnService.ROUTING_ALL
        val selectedCount = vpnPrefs.getStringSet(
            SamanVpnService.KEY_SELECTED_APPS,
            emptySet()
        )?.size ?: 0

        val routing = when (routingMode) {
            SamanVpnService.ROUTING_ONLY -> tr("فقط $selectedCount", "Only $selectedCount")
            SamanVpnService.ROUTING_BYPASS -> tr("بایپس $selectedCount", "Bypass $selectedCount")
            else -> tr("همه", "All")
        }
        if (routingView.text.toString() != routing) routingView.text = routing
    }

    private fun maybeStartDeferredVpn(
        mode: String,
        rawStatus: String
    ) {
        if (!isVpnMode()) return
        if (!rawStatus.startsWith("Connected", ignoreCase = true)) return
        if (vpnPrepareBusy || vpnStartDispatched) return

        val targetMode = deferredVpnMode ?: return
        if (targetMode.isBlank() || targetMode != mode) return

        LogStore.append(
            this,
            "VPN_START",
            "Aether connected; preparing built-in VPN for ${prettyMode(targetMode)}"
        )

        requestVpnPermissionAndStart(targetMode)
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

    private fun chooseDiagnosticsExport() {
        AlertDialog.Builder(this)
            .setTitle("Save diagnostics")
            .setItems(
                arrayOf(
                    "Safe report — recommended\nRecent logs · IDs redacted · compact",
                    "Full history\nAll retained logs · may include identifiers"
                )
            ) { _, which ->
                when (which) {
                    0 -> saveDiagnosticsTxt(
                        fullHistory = false
                    )

                    1 -> confirmFullDiagnosticsExport()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmFullDiagnosticsExport() {
        AlertDialog.Builder(this)
            .setTitle("Save full history?")
            .setMessage(
                "Full diagnostics include all retained history and may contain " +
                    "network metadata even after automatic redaction. Use Safe report when sharing."
            )
            .setPositiveButton("Save full") { _, _ ->
                saveDiagnosticsTxt(
                    fullHistory = true
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveDiagnosticsTxt(
        fullHistory: Boolean
    ) {
        pendingDiagnosticsFull = fullHistory

        LogStore.append(
            this,
            "UI",
            if (fullHistory) {
                "Save FULL diagnostics TXT requested"
            } else {
                "Save SAFE diagnostics TXT requested"
            }
        )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            saveDiagnosticsToDownloads(
                fullHistory
            )
            return
        }

        val suffix =
            if (fullHistory) {
                "full"
            } else {
                "safe"
            }

        val intent =
            Intent(
                Intent.ACTION_CREATE_DOCUMENT
            ).apply {
                addCategory(
                    Intent.CATEGORY_OPENABLE
                )
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TITLE,
                    "Saman-Tunnel-diagnostics-$suffix.txt"
                )
            }

        startActivityForResult(
            intent,
            REQUEST_SAVE_DIAGNOSTICS
        )
    }

    private fun diagnosticsReport(
        fullHistory: Boolean
    ): String =
        if (fullHistory) {
            LogStore.fullDiagnostics(this)
        } else {
            LogStore.diagnostics(this)
        }

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    private fun saveDiagnosticsToDownloads(
        fullHistory: Boolean
    ) {
        runCatching {
            val report =
                diagnosticsReport(
                    fullHistory
                )

            val bytes =
                report.toByteArray(
                    Charsets.UTF_8
                )

            require(
                bytes.isNotEmpty()
            ) {
                "Diagnostics report was empty"
            }

            val suffix =
                if (fullHistory) {
                    "full"
                } else {
                    "safe"
                }

            val fileName =
                "Saman-Tunnel-diagnostics-$suffix-${System.currentTimeMillis()}.txt"

            val values =
                ContentValues().apply {
                    put(
                        MediaStore.Downloads.DISPLAY_NAME,
                        fileName
                    )
                    put(
                        MediaStore.Downloads.MIME_TYPE,
                        "text/plain"
                    )
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS
                    )
                    put(
                        MediaStore.Downloads.IS_PENDING,
                        1
                    )
                }

            val uri =
                contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                )
                    ?: error(
                        "Could not create file in Downloads"
                    )

            try {
                val descriptor =
                    contentResolver.openFileDescriptor(
                        uri,
                        "w"
                    )
                        ?: error(
                            "Could not open Downloads file"
                        )

                descriptor.use { pfd ->
                    FileOutputStream(
                        pfd.fileDescriptor
                    ).use { output ->
                        output.write(bytes)
                        output.flush()
                        output.fd.sync()
                    }
                }

                values.clear()
                values.put(
                    MediaStore.Downloads.IS_PENDING,
                    0
                )

                contentResolver.update(
                    uri,
                    values,
                    null,
                    null
                )

                val verifiedSize =
                    contentResolver
                        .openFileDescriptor(
                            uri,
                            "r"
                        )
                        ?.use {
                            it.statSize
                        }
                        ?: -1L

                require(
                    verifiedSize != 0L
                ) {
                    "Android saved a zero-byte diagnostics file"
                }

                LogStore.append(
                    this,
                    "UI",
                    "Diagnostics saved type=$suffix bytes=${bytes.size} verified=$verifiedSize"
                )

                Toast.makeText(
                    this,
                    if (fullHistory) {
                        "Full diagnostics saved to Downloads ($verifiedSize bytes)"
                    } else {
                        "Safe diagnostics saved to Downloads ($verifiedSize bytes)"
                    },
                    Toast.LENGTH_LONG
                ).show()
            } catch (t: Throwable) {
                contentResolver.delete(
                    uri,
                    null,
                    null
                )
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

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode == REQUEST_APP_ROUTING) {
            refreshVpnControls()

            if (resultCode == RESULT_OK) {
                reapplyVpnRouting()
            }
            return
        }

        if (requestCode == REQUEST_VPN_PERMISSION) {
            vpnPrepareBusy = false
            if (resultCode == RESULT_OK) {
                val mode = pendingVpnMode
                if (mode == null || !isVpnMode()) {
                    pendingVpnMode = null
                    vpnStartDispatched = false
                    return
                }

                LogStore.append(
                    this,
                    "VPN_PERMISSION",
                    "Android VPN permission granted"
                )

                startVpnService(mode)
            } else {
                pendingVpnMode = null
                deferredVpnMode = null
                vpnStartDispatched = false
                getSharedPreferences(SamanVpnService.PREFS, MODE_PRIVATE).edit()
                    .putBoolean(SamanVpnService.KEY_RUNNING, false)
                    .putString(SamanVpnService.KEY_STATUS, "Permission denied — tap mode to retry").apply()

                LogStore.append(
                    this,
                    "VPN_PERMISSION",
                    "Android VPN permission denied"
                )

                Toast.makeText(
                    this,
                    "VPN permission was not granted",
                    Toast.LENGTH_LONG
                ).show()
            }

            return
        }

        if (
            requestCode != REQUEST_SAVE_DIAGNOSTICS ||
            resultCode != RESULT_OK
        ) {
            return
        }

        val uri = data?.data ?: return
        val fullHistory = pendingDiagnosticsFull
        pendingDiagnosticsFull = false

        runCatching {
            val report =
                diagnosticsReport(
                    fullHistory
                )

            val bytes =
                report.toByteArray(
                    Charsets.UTF_8
                )

            require(
                bytes.isNotEmpty()
            ) {
                "Diagnostics report was empty"
            }

            val descriptor =
                contentResolver.openFileDescriptor(
                    uri,
                    "w"
                )
                    ?: error(
                        "Could not open selected file"
                    )

            descriptor.use { pfd ->
                FileOutputStream(
                    pfd.fileDescriptor
                ).use { output ->
                    output.write(bytes)
                    output.flush()
                    output.fd.sync()
                }
            }

            val verifiedSize =
                contentResolver
                    .openFileDescriptor(
                        uri,
                        "r"
                    )
                    ?.use {
                        it.statSize
                    }
                    ?: -1L

            require(
                verifiedSize != 0L
            ) {
                "Android saved a zero-byte diagnostics file"
            }

            val suffix =
                if (fullHistory) {
                    "full"
                } else {
                    "safe"
                }

            LogStore.append(
                this,
                "UI",
                "Diagnostics saved type=$suffix bytes=${bytes.size} verified=$verifiedSize"
            )

            Toast.makeText(
                this,
                "Diagnostics TXT saved ($verifiedSize bytes)",
                Toast.LENGTH_SHORT
            ).show()
        }.onFailure {
            LogStore.append(
                this,
                "ERROR",
                "Saving diagnostics TXT failed: ${it.stackTraceToString()}"
            )

            Toast.makeText(
                this,
                "Could not save TXT",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun refreshBatteryStatus() {
        if (!::batteryView.isInitialized) return

        val androidUnrestricted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(packageName)
            } else {
                true
            }

        val xiaomiUnrestricted = isXiaomiPowerKeeperUnrestricted()
        val unrestricted = androidUnrestricted || xiaomiUnrestricted

        batteryView.text =
            if (unrestricted) tr("بدون محدودیت", "Unrestricted") else tr("بهینه‌شده", "Optimized")

        batteryView.setTextColor(if (unrestricted) green else orange)

        batteryView.contentDescription =
            when {
                androidUnrestricted -> "Battery unrestricted by Android"
                xiaomiUnrestricted -> "Battery unrestricted by Xiaomi PowerKeeper"
                else -> "Battery optimization is enabled"
            }
    }

    private fun isXiaomiPowerKeeperUnrestricted(): Boolean {
        val device = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        val isXiaomiFamily =
            device.contains("xiaomi") ||
                device.contains("redmi") ||
                device.contains("poco")

        if (!isXiaomiFamily) return false

        val settingKeys = listOf(
            "MILLET_NO_RESTRICT_APP",
            "millet_no_restrict_app"
        )

        return settingKeys.any { key ->
            val raw = runCatching {
                Settings.System.getString(contentResolver, key)
            }.getOrNull()?.trim().orEmpty()

            raw.isNotBlank() && raw.split(',', ';', ':', '\n', '\r', ' ')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .any { it == packageName }
        }
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
        if (updateChecking) return
        updateChecking = true

        updateView.text = tr("در حال بررسی…", "Checking…")
        LogStore.append(this, "UPDATE", "Manual update check started")

        Thread {
            val result = runCatching { fetchLatestRelease() }

            runOnUiThread {
                updateChecking = false
                updateView.text = tr("بررسی", "Check")

                result.onSuccess { release ->
                    val current = currentVersion()
                    val latest = release.first.removePrefix("v")

                    if (ReleaseVersion.compare(latest, current) > 0) {
                        LogStore.append(this, "UPDATE", "Update available current=$current latest=$latest")

                        AlertDialog.Builder(this)
                            .setTitle("Update available")
                            .setMessage("Installed: v$current\nLatest: v$latest")
                            .setPositiveButton("Download APK") { _, _ ->
                                runCatching {
                                    val request = android.app.DownloadManager.Request(Uri.parse(release.third))
                                        .setTitle("Saman Tunnel v$latest")
                                        .setMimeType("application/vnd.android.package-archive")
                                        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        request.setDestinationInExternalPublicDir(
                                            Environment.DIRECTORY_DOWNLOADS,
                                            "Saman-Tunnel-v$latest-universal-arm-${System.currentTimeMillis()}.apk"
                                        )
                                    }
                                    val id = getSystemService(android.app.DownloadManager::class.java).enqueue(request)
                                    LogStore.append(this, "UPDATE", "APK download queued id=$id version=$latest")
                                    Toast.makeText(this, "Downloading APK — see Downloads notification", Toast.LENGTH_LONG).show()
                                }.onFailure {
                                    LogStore.append(this, "UPDATE", "Download failed: ${it.message}")
                                    Toast.makeText(this, "Could not start download; use GitHub", Toast.LENGTH_LONG).show()
                                }
                            }
                            .setNeutralButton("Open GitHub") { _, _ -> openProjectLink(release.second) }
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

    private fun fetchLatestRelease(): Triple<String, String, String> {
        val connected = getSharedPreferences(AetherService.PREFS, MODE_PRIVATE)
            .getString(AetherService.KEY_STATUS, "")
            ?.startsWith("Connected", true) == true

        val attempts = if (connected) {
            listOf(
                Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress.createUnresolved("127.0.0.1", 1819)
                )
            )
        } else {
            listOf<Proxy?>(null)
        }

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

                val releases = JSONArray(body)
                var newest: Triple<String, String, String>? = null
                for (index in 0 until releases.length()) {
                    val release = releases.optJSONObject(index) ?: continue
                    if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
                    val tag = release.optString("tag_name")
                    val url = release.optString("html_url")
                    if (!ReleaseVersion.isAndroidReleaseTag(tag) || tag.contains("-rc.")) continue
                    if (!ReleaseVersion.isTrustedReleaseUrl(url)) continue
                    val expectedName = "Saman-Tunnel-$tag-universal-arm.apk"
                    val expectedUrl = "https://github.com/velnox4827/saman-aether/releases/download/$tag/$expectedName"
                    val assets = release.optJSONArray("assets") ?: continue
                    for (assetIndex in 0 until assets.length()) {
                        val asset = assets.optJSONObject(assetIndex) ?: continue
                        val download = asset.optString("browser_download_url")
                        if (asset.optString("name") != expectedName || asset.optLong("size") <= 0) continue
                        if (download != expectedUrl || !ReleaseVersion.isTrustedReleaseUrl(download)) continue
                        if (newest == null || ReleaseVersion.compare(tag, newest.first) > 0) {
                            newest = Triple(tag, url, download)
                        }
                    }
                }
                return newest ?: error("No trusted stable Android APK was found.")
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

    private fun prettyMode(mode: String): String = when (mode.uppercase()) {
        "MASQUE_H3", "MASQUE" -> "MASQUE H3"
        "MIM_H2" -> "MASQUE-in-MASQUE H2"
        "MIM_H3", "MIM" -> "MASQUE-in-MASQUE H3"
        "MASQUE_H2" -> "MASQUE H2"
        "WG" -> "WireGuard"
        "GOOL" -> "GOOL"
        "PSIPHON_ONLY" -> "Psiphon"
        "TOR_ONLY" -> "Tor"
        "TOR_INSIDE_MASQUE_H3", "TOR_INSIDE_MASQUE" -> "MASQUE H3 → Tor"
        "TOR_INSIDE_MASQUE_H2" -> "MASQUE H2 → Tor"
        "TOR_INSIDE_WG" -> "WireGuard → Tor"
        "TOR_INSIDE_GOOL" -> "GOOL → Tor"
        "TOR_REVERSE" -> "Tor → MASQUE H2"
        else -> mode.ifBlank { "—" }
    }

    private fun badgeFor(mode: String): String = when {
        mode.startsWith("TOR") -> "TOR"
        mode.startsWith("MIM") -> "MIM"
        mode.startsWith("MASQUE") -> "MASQUE"
        mode == "PSIPHON_ONLY" -> "PSIPHON"
        else -> mode.ifBlank { "WG" }
    }

    private fun fmtTime(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0L)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    }

    private fun refreshState(force: Boolean = false) {
        refreshVpnControls()
        if (!::titleView.isInitialized) return

        val mode = corePrefs.getString(AetherService.KEY_MODE, "") ?: ""
        val rawStatus = corePrefs.getString(AetherService.KEY_STATUS, "Stopped") ?: "Stopped"

        maybeStartDeferredVpn(mode, rawStatus)
        val vpnRunning = vpnPrefs.getBoolean(SamanVpnService.KEY_RUNNING, false)
        val vpnSelected = isVpnMode()
        val status = ConnectionStatus.display(
            rawStatus.trim(), vpnSelected, vpnRunning,
            vpnPrefs.getString(SamanVpnService.KEY_STATUS, "Preparing VPN").orEmpty()
        )

        val connected = status.startsWith("Connected", true)
        val unstable = status.startsWith("Connection unstable", true)
        val error = status.startsWith("Error", true)
        val starting = status.startsWith("Starting", true)
        val connecting = status.startsWith("Connecting", true)
        val switching = status.startsWith("Switching", true)
        val stopping = status.startsWith("Stopping", true)
        val stopped = status.startsWith("Stopped", true)
        val working = starting || connecting || switching

        // Session timer (persisted so it survives activity recreation).
        if (connected || unstable) {
            if (connectedSince == 0L) {
                connectedSince = ui.since.takeIf { it > 0L } ?: System.currentTimeMillis()
                ui.since = connectedSince
            }
        } else if (connectedSince != 0L || staleSinceCheck) {
            connectedSince = 0L
            staleSinceCheck = false
            ui.since = 0L
        }
        val showTimer = connected || unstable
        if (showTimer) {
            timerView.text = fmtTime((System.currentTimeMillis() - connectedSince) / 1000L)
        }
        if (timerView.visibility != (if (showTimer) View.VISIBLE else View.INVISIBLE)) {
            timerView.visibility = if (showTimer) View.VISIBLE else View.INVISIBLE
        }
        nextDelay = when {
            showTimer -> 1000L
            working || stopping -> 600L
            else -> 1500L
        }

        val lastMode = lastModeValue()
        val active = connected || unstable || working || stopping
        val shownMode = normMode(if (active && mode.isNotBlank()) mode else lastMode)

        val key = "$status|$mode|$lastMode|$vpnRunning|$vpnSelected|${tk.dark}|${tk.pal.key}|${ui.lang}"
        if (!force && key == renderKey) return
        renderKey = key

        val modeName = prettyMode(shownMode)
        val title: String
        val sub: String
        var detail = ""
        val state: SwState
        when {
            connected -> {
                title = tr("متصل شدی", "Connected")
                sub = tr("اینترنتت با $modeName امن شد", "Secured with $modeName")
                detail = if (vpnRunning) "${prettyMode(mode)} VPN"
                else if (status.contains("HTTP", true)) "SOCKS5 :1819 + HTTP :1820"
                else "SOCKS5 :1819"
                state = SwState.ON
            }
            unstable -> {
                title = tr("اتصال ناپایدار", "Connection unstable")
                sub = tr("در حال بررسی SOCKS5 محلی", "Checking local SOCKS5")
                detail = "127.0.0.1:1819"
                state = SwState.WARN
            }
            error -> {
                title = tr("خطا", "Error")
                sub = status.substringAfter("Error:", "").trim()
                    .ifBlank { tr("هسته خطا گزارش کرد", "Aether core reported an error") }
                state = SwState.ERROR
            }
            working -> {
                title = tr("در حال اتصال…", "Connecting…")
                sub = status.substringAfter("—", "").trim()
                    .ifBlank { tr("چند لحظه صبر کن", "Just a moment") }
                state = SwState.BUSY
            }
            stopping -> {
                title = tr("در حال قطع اتصال…", "Disconnecting…")
                sub = tr("بستن پروکسی‌های محلی", "Closing local proxies")
                state = SwState.BUSY
            }
            else -> {
                title = tr("متصل نیستی", "Not connected")
                sub = tr("برای محافظت از اینترنتت وصل شو", "Tap to protect your internet")
                state = SwState.OFF
            }
        }
        titleView.text = title
        subView.text = sub
        detailView.text = detail
        detailView.visibility = if (detail.isBlank()) View.GONE else View.VISIBLE
        subView.setTextColor(if (error) tk.err else tk.muted)
        powerSwitch.setState(state)
        powerSwitch.contentDescription = "$title. $sub"
        orbs.setColors(tk.orbColors(connected), true)
        badgeView.text = badgeFor(shownMode)
        protoValue.text = modeName

        if (shownMode != selectedChip) {
            if (selectedChip.isNotEmpty()) styleChip(selectedChip, false)
            styleChip(shownMode, true)
            selectedChip = shownMode
        }
        if (stopped) timerView.text = "00:00:00"
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

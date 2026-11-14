package com.saman.tunnel

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class SubscriptionActivity : Activity() {
    companion object {
        const val EXTRA_IMPORT_TEXT = "import_text"
        const val EXTRA_SELECTED_NODE = "selected_node"
        private const val FILE_REQUEST = 2201
        private const val QR_REQUEST = 2202
        private const val BOT = "https://t.me/TunnelYarBot"
    }
    private val store by lazy { ConfigStore(this) }
    private val ui by lazy { UiPrefs(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var tk: Tk
    private lateinit var body: LinearLayout
    private var busy = false
    private var lastError: String? = null
    private var prompt: AlertDialog? = null
    private val seen by lazy { getSharedPreferences("subscription_import", MODE_PRIVATE) }
    private val fa get() = ui.lang == "fa" || (ui.lang == "auto" && Locale.getDefault().language == "fa")
    private fun tr(f: String, e: String) = if (fa) f else e
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val dark = ui.theme == "dark" || (ui.theme == "system" && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        tk = Tk(dark, ui.palette)
        window.statusBarColor = tk.bg
        window.navigationBarColor = tk.bg
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
            setBackgroundColor(tk.bg)
            layoutDirection = if (fa) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }
        val scroll = ScrollView(this).apply { addView(body); fitsSystemWindows = true; setBackgroundColor(tk.bg) }
        setContentView(scroll)
        render()
        if (savedInstanceState == null) acceptIntent(intent)
        runCatching { SubscriptionRefreshJob.schedule(this) }
    }
    override fun onResume() { super.onResume(); if (::body.isInitialized) render() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); acceptIntent(intent) }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !busy && prompt == null) clipboardOffer()
    }
    override fun onDestroy() { prompt?.dismiss(); executor.shutdownNow(); super.onDestroy() }

    private fun label(value: String, heading: Boolean = false) {
        body.addView(TextView(this).apply {
            text = value; if (value == lastError || busy) accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            textSize = if (heading) 22f else 15f; setTextColor(if (heading) tk.ink else tk.muted)
            setPadding(0, dp(12), 0, dp(10))
        })
    }
    private fun button(value: String, action: () -> Unit) {
        body.addView(Button(this).apply {
            text = value; isAllCaps = false; setTextColor(tk.ink); minHeight = dp(48)
            isEnabled = !busy; alpha = if (busy) 0.5f else 1f
            background = GradientDrawable().apply { setColor(tk.tile); cornerRadius = dp(18).toFloat(); setStroke(dp(1).coerceAtLeast(1), tk.line) }
            setOnClickListener { if (!busy) action() }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }
    private fun render() {
        body.removeAllViews()
        label(tr("اشتراک من", "My subscription"), true)
        if (busy) label(tr("در حال پردازش…", "Working…"))
        lastError?.let { label(it) }
        val connection = getSharedPreferences(AetherService.PREFS, MODE_PRIVATE).getString("status", "Stopped")
        label(tr("وضعیت اتصال: ", "Connection state: ") + connection)
        label(tr("IP خروجی واقعی و تأخیر درخواست HTTP در صفحه اصلی نمایش داده می‌شود (نه پینگ ICMP).", "Actual exit IP and HTTP request latency appear on the main screen (not ICMP ping)."))
        button(tr("افزودن / چسباندن متن یا لینک", "Add / paste text or link")) { addDialog() }
        button(tr("اسکن کد QR", "Scan QR code")) { startActivityForResult(Intent(this, QrScanActivity::class.java), QR_REQUEST) }
        button(tr("ورود از فایل", "Import file")) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, FILE_REQUEST)
        }
        label(tr("VLESS، VMess، Trojan، Shadowsocks و WireGuard INI تک‌همتا. Hysteria2 و TUIC پشتیبانی نمی‌شود؛ ورودی نامعتبر نادیده گرفته نمی‌شود.", "VLESS, VMess, Trojan, Shadowsocks and one-peer WireGuard INI. Hysteria2 and TUIC are unsupported; invalid entries are never silently skipped."))
        try {
            val state = store.snapshot()
            if (state.optBoolean("selectionLost")) label(tr("گره انتخاب‌شده دیگر در اشتراک نیست؛ گره دیگری را برای اتصال انتخاب کنید.", "The selected node is no longer available. Select another node to connect."))
            val sources = state.optJSONArray("subscriptions")
            if (sources == null || sources.length() == 0) label(tr("هنوز اشتراک یا گره‌ای اضافه نشده", "No subscriptions or nodes yet"))
            if (sources != null) for (s in 0 until sources.length()) {
                val source = sources.getJSONObject(s)
                label(if (source.has("url")) tr("اشتراک من", "My subscription") + " ${s + 1}" else tr("پیکربندی‌های واردشده", "Imported configurations"), true)
                val info = source.optJSONObject("info")
                fun counter(key: String) = if (info?.has(key) == true) "${info.getLong(key)} B" else tr("نامشخص", "Unknown")
                label(tr("ارسال: ", "Upload: ") + counter("upload") + "\n" + tr("دریافت: ", "Download: ") + counter("download") + "\n" + tr("حجم کل: ", "Total: ") + counter("total"))
                val metadata = info?.let { SubscriptionInfo.parse(listOf("upload", "download", "total", "expire").filter(it::has).joinToString(";") { key -> "$key=${it.getLong(key)}" }) }
                val usage = SubscriptionUsage.from(metadata, System.currentTimeMillis() / 1000)
                fun gib(value: java.math.BigInteger?): String = value?.toBigDecimal()?.divide(java.math.BigDecimal("1073741824"), 2, java.math.RoundingMode.HALF_UP)?.toPlainString()?.plus(" GiB") ?: tr("نامشخص", "Unknown")
                label(tr("مصرف: ", "Consumed: ") + gib(usage.consumedBytes) + "\n" + tr("باقی‌مانده: ", "Remaining: ") + gib(usage.remainingBytes))
                label(tr("روز باقی‌مانده: ", "Days remaining: ") + (usage.daysRemaining?.toString() ?: tr("نامشخص", "Unknown")))
                val expires = if (info?.has("expire") == true && info.optLong("expire") > 0) {
                    val seconds = info.getLong("expire")
                    if (seconds <= Long.MAX_VALUE / 1000) DateFormat.getDateTimeInstance().format(Date(seconds * 1000)) else tr("خارج از محدوده نمایش", "Outside display range")
                } else tr("نامشخص", "Unknown")
                label(tr("انقضا: ", "Expiry: ") + expires)
                if (source.has("url")) button(tr("به‌روزرسانی اشتراک", "Refresh subscription")) {
                    background { SubscriptionFetcher.refresh(store, source.getString("id"), source.getString("url")) }
                }
                val nodes = source.getJSONArray("nodes")
                for (n in 0 until nodes.length()) {
                    val node = nodes.getJSONObject(n)
                    val id = node.getString("id")
                    button((if (state.optString("selected") == id) "✓ " else "") + node.getString("name") + " — " + tr("انتخاب و اتصال", "Select and connect")) { select(id) }
                }
            }
        } catch (_: Exception) { label(tr("خواندن اطلاعات ذخیره‌شده ممکن نیست؛ اطلاعات حفظ شده است.", "Saved configurations cannot be read; existing data retained.")) }
        for (title in listOf(tr("خرید", "Buy"), tr("دریافت تست", "Get test"), tr("تمدید", "Renew"))) button(title) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BOT))) } catch (_: Exception) { notice(tr("مرورگری پیدا نشد", "No browser available")) }
        }
        button(tr("صفحه اصلی / IP و تأخیر", "Main screen / IP and latency")) {
            startActivity(Intent().setClassName(this, "com.saman.tunnel.MainActivity").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish()
        }
        button(tr("بازگشت", "Back")) { finish() }
    }
    private fun select(id: String) {
        val phase = getSharedPreferences(AetherService.PREFS, MODE_PRIVATE).getString("phase", "STOPPED")
        if (phase in listOf("STARTING", "CONNECTING", "STOPPING")) { notice(tr("لطفاً منتظر پایان اتصال بمانید", "Please wait for the current connection operation")); return }
        try {
            store.select(id)
            check(getSharedPreferences(AetherService.PREFS, MODE_PRIVATE).edit().putString(AetherService.KEY_LAST_MODE, "XRAY").commit())
            if (callingActivity != null) setResult(RESULT_OK, Intent().putExtra(EXTRA_SELECTED_NODE, id))
            else startActivity(Intent().setClassName(this, "com.saman.tunnel.MainActivity").setAction("com.saman.tunnel.QUICK_CONNECT").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        } catch (_: Exception) { notice(tr("ذخیره انتخاب ممکن نشد", "Could not save selection")) }
    }
    private fun addDialog() {
        val input = EditText(this).apply { hint = tr("لینک HTTPS یا متن پیکربندی", "HTTPS URL or configuration text"); minLines = 3; maxLines = 8; setTextColor(tk.ink); setHintTextColor(tk.muted) }
        prompt = AlertDialog.Builder(this).setTitle(tr("افزودن", "Add" )).setView(input)
            .setPositiveButton(tr("وارد کردن", "Import")) { _, _ -> importValue(input.text.toString()) }
            .setNeutralButton(tr("چسباندن", "Paste"), null).setNegativeButton(tr("لغو", "Cancel"), null).create().also { dialog ->
                dialog.setOnDismissListener { prompt = null }
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { clipboardText()?.let { input.setText(it) } }
            }
    }
    private fun clipboardText(): String? = try {
        val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString()?.takeIf { it.length <= SubscriptionFetcher.MAX_BYTES } else null
    } catch (_: Exception) { null }
    private fun clipboardOffer() { clipboardText()?.trim()?.takeIf { recognized(it) }?.let { offer(it) } }
    private fun recognized(text: String): Boolean = Regex("(?is)^(https?://|vless://|vmess://|trojan://|ss://|hysteria2?://|hy2://|tuic://|wireguard://|\\[Interface]).*").matches(text)
    private fun rememberOffer(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        seen.edit().putString("last_offer", digest).apply()
        return digest
    }
    private fun offer(text: String) {
        if (busy || prompt != null || text.length > SubscriptionFetcher.MAX_BYTES) return
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        if (seen.getString("last_offer", null) == digest) return
        seen.edit().putString("last_offer", digest).apply()
        prompt = AlertDialog.Builder(this).setTitle(tr("وارد کردن پیکربندی؟", "Import configuration?"))
            .setMessage(tr("یک لینک یا پیکربندی شناسایی شد. فقط در صورت اعتماد به منبع وارد کنید.", "A link or configuration was detected. Import only if you trust its source."))
            .setPositiveButton(tr("وارد کردن", "Import")) { _, _ -> importValue(text) }.setNegativeButton(tr("لغو", "Cancel"), null)
            .create().also { it.setOnDismissListener { prompt = null }; it.show() }
    }
    private fun acceptIntent(value: Intent?) {
        val text = value?.getStringExtra(EXTRA_IMPORT_TEXT) ?: (if (value?.action == Intent.ACTION_SEND) value.getStringExtra(Intent.EXTRA_TEXT) else value?.dataString)
        text?.let { raw ->
            try {
                val uri = Uri.parse(raw.trim())
                val value = if (uri.scheme == "saman-tunnel") {
                    require(uri.host == "import" && uri.path.orEmpty().isEmpty() && uri.fragment == null && uri.userInfo == null && uri.port == -1 && uri.queryParameterNames == setOf("url") && uri.getQueryParameters("url").size == 1)
                    val url = uri.getQueryParameter("url") ?: throw IllegalArgumentException()
                    SubscriptionFetcher.validateUrl(url); url
                } else raw.trim()
                if (intent?.getBooleanExtra("confirmed_import", false) == true && callingActivity?.packageName == packageName) {
                    rememberOffer(value)
                    importValue(value)
                } else offer(value)
            } catch (_: Exception) { notice(tr("لینک ورود نامعتبر است", "Invalid import link")) }
        }
    }
    private fun importValue(raw: String) {
        val text = raw.trim()
        background {
            if (text.startsWith("https://", true) || text.startsWith("http://", true)) {
                SubscriptionFetcher.refresh(store, UUID.randomUUID().toString(), text)
            } else store.importText(text)
        }
    }
    private fun background(action: () -> Unit) {
        if (busy) return
        busy = true; lastError = null; render()
        executor.submit {
            var message: String? = null
            try { action() } catch (e: Exception) {
                // Parser messages are defined to omit secrets; arbitrary IO exceptions are not safe to display.
                message = if (e is IllegalArgumentException) e.message else tr("ورود یا به‌روزرسانی ناموفق بود؛ داده قبلی حفظ شد.", "Import or refresh failed; previous data retained.")
            }
            runOnUiThread { if (!isDestroyed && !isFinishing) { busy = false; lastError = message; render(); notice(message ?: tr("ذخیره شد", "Saved")) } }
        }
    }
    private fun notice(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == QR_REQUEST) data?.getStringExtra(QrScanActivity.EXTRA_TEXT)?.let { offer(it) }
        if (requestCode == FILE_REQUEST) data?.data?.let { uri ->
            prompt = AlertDialog.Builder(this).setTitle(tr("ورود فایل؟", "Import document?"))
                .setMessage(tr("فقط پیکربندی منبع مورد اعتماد را وارد کنید.", "Import configurations only from a trusted source."))
                .setNegativeButton(tr("لغو", "Cancel"), null).setPositiveButton(tr("وارد کردن", "Import")) { _, _ -> background {
                    val text = contentResolver.openInputStream(uri)?.use { SubscriptionFetcher.readBounded(it) }
                        ?: throw IllegalArgumentException("Cannot read selected document")
                    store.importText(text)
                } }.create().also { it.setOnDismissListener { prompt = null }; it.show() }
        }
    }
}

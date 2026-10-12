package com.saman.tunnel

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** The one place for logs: filter, copy, share, save, clear. Reached from Settings > Support. */
class DebugActivity : Activity() {
    private companion object {
        const val REQUEST_SAVE = 3001
        const val LINES = 400
        val ERROR = Regex("error|exception|failed|fatal", RegexOption.IGNORE_CASE)
    }

    private lateinit var tk: Tk
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var seg: SegmentView
    private var filter = 0
    private var pendingFull = false
    private val ui by lazy { UiPrefs(this) }
    private val fa: Boolean
        get() = when (ui.lang) { "fa" -> true; "en" -> false; else -> Locale.getDefault().language == "fa" }
    private fun tr(f: String, e: String) = if (fa) f else e
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun dark(): Boolean = when (ui.theme) {
        "dark" -> true
        "light" -> false
        else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun rounded(fill: Int, r: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(r).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun chip(text: String, action: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(tk.ink)
        minHeight = dp(42)
        background = rounded(tk.tile, 19, tk.line)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tk = Tk(dark(), ui.palette)
        window.statusBarColor = tk.bg
        window.navigationBarColor = tk.bg
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tk.bg)
            layoutDirection = if (fa) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(chip("←") { finish() }.apply { contentDescription = tr("بازگشت", "Back") }, LinearLayout.LayoutParams(dp(44), dp(44)))
        head.addView(TextView(this).apply {
            text = tr("عیب‌یابی", "Debug")
            textSize = 20f
            setTextColor(tk.ink)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, wrap, 1f).apply { marginStart = dp(12) })
        root.addView(head, LinearLayout.LayoutParams(mp, wrap))
        root.addView(TextView(this).apply {
            text = tr("لاگ‌های برنامه و هسته. شناسه‌ها پنهان می‌شوند.", "App and core logs. Identifiers are redacted.")
            textSize = 12.5f
            setTextColor(tk.muted)
        }, LinearLayout.LayoutParams(mp, wrap).apply { topMargin = dp(6); bottomMargin = dp(10) })

        seg = SegmentView(this, tk).apply {
            setItems(listOf(tr("برنامه", "App"), tr("هسته", "Core"), tr("خطاها", "Errors")))
            select(0)
            onSelect = { filter = it; render() }
        }
        root.addView(seg, LinearLayout.LayoutParams(mp, wrap))

        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(tk.ink)
            setTextIsSelectable(true)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        scroll = ScrollView(this).apply {
            background = rounded(tk.tile, 16, tk.line)
            addView(logView, ViewGroup.LayoutParams(mp, wrap))
        }
        root.addView(scroll, LinearLayout.LayoutParams(mp, 0, 1f).apply { topMargin = dp(10); bottomMargin = dp(10) })

        val row = LinearLayout(this)
        listOf(
            tr("کپی", "Copy") to { copy() },
            tr("اشتراک", "Share") to { share() },
            tr("ذخیره", "Save") to { chooseSave() },
            tr("پاک", "Clear") to { confirmClear() }
        ).forEachIndexed { i, (label, action) ->
            row.addView(chip(label, action), LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (i > 0) marginStart = dp(6) })
        }
        root.addView(row, LinearLayout.LayoutParams(mp, wrap))

        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(dp(16), insets.systemWindowInsetTop + dp(8), dp(16), insets.systemWindowInsetBottom + dp(12))
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun text(): String = when (filter) {
        0 -> LogStore.tail(this, false, LINES)
        1 -> LogStore.tail(this, true, LINES)
        else -> (LogStore.tail(this, false, LINES) + "\n" + LogStore.tail(this, true, LINES))
            .lines().filter { ERROR.containsMatchIn(it) }.takeLast(LINES).joinToString("\n")
    }.ifBlank { tr("(لاگی نیست)", "(no logs yet)") }

    private fun render() {
        val sb = SpannableStringBuilder()
        for (line in text().lines()) {
            val start = sb.length
            sb.append(line).append('\n')
            if (ERROR.containsMatchIn(line)) sb.setSpan(ForegroundColorSpan(tk.err), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        logView.text = sb
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun copy() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Saman Tunnel log", text()))
        Toast.makeText(this, tr("لاگ کپی شد", "Log copied"), Toast.LENGTH_SHORT).show()
    }

    private fun share() {
        val body = LogStore.diagnostics(this)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Saman Tunnel diagnostics")
                .putExtra(Intent.EXTRA_TEXT, body.takeLast(100_000)),
            tr("اشتراک لاگ", "Share log")
        ))
    }

    private fun chooseSave() {
        AlertDialog.Builder(this)
            .setTitle(tr("ذخیره TXT", "Save TXT"))
            .setItems(arrayOf(
                tr("گزارش امن (پیشنهادی)", "Safe report (recommended)"),
                tr("کل تاریخچه — ممکن است شامل شناسه باشد", "Full history — may include identifiers")
            )) { _, which -> save(which == 1) }
            .setNegativeButton(tr("لغو", "Cancel"), null)
            .show()
    }

    private fun save(full: Boolean) {
        pendingFull = full
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
                .putExtra(Intent.EXTRA_TITLE, "Saman-Tunnel-diagnostics-${if (full) "full" else "safe"}.txt"),
            REQUEST_SAVE
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != REQUEST_SAVE || resultCode != RESULT_OK) return
        runCatching {
            val report = if (pendingFull) LogStore.fullDiagnostics(this) else LogStore.diagnostics(this)
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(report.toByteArray()) }
        }.onSuccess { Toast.makeText(this, tr("ذخیره شد", "Saved"), Toast.LENGTH_SHORT).show() }
            .onFailure {
                LogStore.append(this, "ERROR", "Saving diagnostics failed: ${it.message}")
                Toast.makeText(this, tr("ذخیره نشد", "Could not save"), Toast.LENGTH_LONG).show()
            }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage(tr("همه لاگ‌ها پاک شود؟", "Delete all logs?"))
            .setPositiveButton(tr("پاک کن", "Delete")) { _, _ -> LogStore.clear(this); render() }
            .setNegativeButton(tr("لغو", "Cancel"), null)
            .show()
    }
}

package com.saman.tunnel

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/** Three slowly drifting colour orbs behind the UI (cheap radial gradients, ~30 fps, pausable). */
class OrbsView(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val paints = Array(3) { Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 230 } }
    private val stops = floatArrayOf(0f, 0.68f, 1f)
    private var fromColors = IntArray(3)
    private var toColors = IntArray(3)
    private var curColors = IntArray(3)
    private var blendStart = 0L
    private var blending = false
    private var running = false
    private val origin = SystemClock.uptimeMillis()
    private val argb = ArgbEvaluator()

    fun setColors(colors: IntArray, animated: Boolean) {
        if (colors.contentEquals(toColors)) return
        val first = toColors.all { it == 0 }
        fromColors = curColors.copyOf()
        toColors = colors.copyOf()
        if (first || !animated) {
            curColors = toColors.copyOf()
            blending = false
            rebuild()
        } else {
            blendStart = SystemClock.uptimeMillis()
            blending = true
        }
        invalidate()
    }

    fun start() {
        val scale = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        )
        running = scale != 0f
        invalidate()
    }

    fun stop() {
        running = false
    }

    private fun rebuild() {
        for (i in 0..2) {
            val c = curColors[i]
            val clear = c and 0x00FFFFFF
            paints[i].shader = RadialGradient(
                0f, 0f, 1f, intArrayOf(c, clear, clear), stops, Shader.TileMode.CLAMP
            )
        }
    }

    private fun pingPong(ms: Long, period: Long): Float {
        val x = (ms % (2 * period)).toFloat() / period
        val f = if (x <= 1f) x else 2f - x
        return f * f * (3f - 2f * f)
    }

    private fun orb(canvas: Canvas, i: Int, cx: Float, cy: Float, dx: Float, dy: Float, endScale: Float, t: Float) {
        val r = 170f * density * (1f + (endScale - 1f) * t)
        canvas.save()
        canvas.translate(cx + dx * t, cy + dy * t)
        canvas.scale(r, r)
        canvas.drawCircle(0f, 0f, 1f, paints[i])
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        if (toColors.all { it == 0 }) return
        if (blending) {
            val t = ((SystemClock.uptimeMillis() - blendStart) / 1000f).coerceIn(0f, 1f)
            for (i in 0..2) curColors[i] = argb.evaluate(t, fromColors[i], toColors[i]) as Int
            rebuild()
            if (t >= 1f) blending = false
        }
        val w = width.toFloat()
        val h = height.toFloat()
        val d = density
        val ms = SystemClock.uptimeMillis() - origin
        orb(canvas, 0, 70f * d, 60f * d, 120f * d, 170f * d, 1.25f, pingPong(ms, 17000))
        orb(canvas, 1, w - 20f * d, 0.3f * h + 170f * d, -150f * d, -130f * d, 0.85f, pingPong(ms, 21000))
        orb(canvas, 2, 0.1f * w + 170f * d, h - 50f * d, 110f * d, -150f * d, 1.2f, pingPong(ms, 19000))
        if (running || blending) postInvalidateDelayed(33L)
    }
}

enum class SwState { OFF, BUSY, ON, WARN, ERROR }

/** The big power switch: track + sliding knob, glow when connected, blink while connecting. */
class PowerSwitchView(context: Context) : View(context) {
    private val d = context.resources.displayMetrics.density
    private fun dp(v: Float): Float = v * d

    private val pad = dp(16f)
    private val trackW = dp(228f)
    private val trackH = dp(116f)
    private val knobD = dp(98f)
    private val knobInset = dp(8f)
    private val travel = dp(112f)

    private var trackFill = 0
    private var trackStroke = 0
    private var knobFill = 0
    private var iconOff = 0
    private var skyC = 0
    private var okC = 0
    private var warnC = 0
    private var errC = 0
    private var busyShader: Shader? = null
    private var okShader: Shader? = null
    private var warnShader: Shader? = null

    var state: SwState = SwState.OFF
        private set
    private var initialized = false

    private var knobFrom = 0f
    private var knobTo = 0f
    private var knobCur = 0f
    private val aFrom = FloatArray(3)
    private val aTo = FloatArray(3)
    private val aCur = FloatArray(3)
    private var glowFrom = 0f
    private var glowTo = 0f
    private var glowCur = 0f
    private var iconFrom = 0
    private var iconTo = 0
    private var iconCur = 0

    private val argb = ArgbEvaluator()
    private val overshoot = OvershootInterpolator(1.3f)
    private val morph = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 450L
        addUpdateListener { onMorph(it.animatedFraction) }
    }
    private var blink = 1f
    private val blinker = ValueAnimator.ofFloat(0.45f, 1f).apply {
        duration = 1000L
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            blink = it.animatedValue as Float
            invalidate()
        }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlay = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(2.6f)
    }
    private val rect = RectF()
    private val arc = RectF()

    init {
        // BlurMaskFilter shadows need a software layer; the view is small and rarely redrawn.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        glowPaint.maskFilter = BlurMaskFilter(dp(18f), BlurMaskFilter.Blur.NORMAL)
        shadowPaint.maskFilter = BlurMaskFilter(dp(10f), BlurMaskFilter.Blur.NORMAL)
        shadowPaint.color = Color.BLACK
        isClickable = true
        isFocusable = true
    }

    fun applyTheme(tk: Tk) {
        trackFill = tk.track
        trackStroke = tk.glassB
        knobFill = tk.knob
        iconOff = tk.knobIcon
        skyC = tk.sky
        okC = tk.ok
        warnC = tk.warn
        errC = tk.err
        val x0 = pad
        val y0 = pad
        val x1 = pad + trackW
        val y1 = pad + trackH
        busyShader = LinearGradient(x0, y0, x1, y1, tk.skyLight, tk.sky, Shader.TileMode.CLAMP)
        okShader = LinearGradient(x0, y0, x1, y1, 0xFF6fe6a6.toInt(), tk.ok, Shader.TileMode.CLAMP)
        warnShader = LinearGradient(x0, y0, x1, y1, 0xFFffc07a.toInt(), tk.warn, Shader.TileMode.CLAMP)
        initialized = false
        invalidate()
    }

    fun setState(s: SwState, animated: Boolean = true) {
        if (initialized && s == state) return
        val first = !initialized
        state = s
        initialized = true
        morph.cancel()
        knobFrom = knobCur
        glowFrom = glowCur
        iconFrom = if (first) 0 else iconCur
        for (i in 0..2) aFrom[i] = aCur[i]
        knobTo = when (s) {
            SwState.OFF, SwState.ERROR -> 0f
            SwState.BUSY -> 0.5f
            SwState.ON, SwState.WARN -> 1f
        }
        aTo[0] = if (s == SwState.BUSY) 1f else 0f
        aTo[1] = if (s == SwState.ON) 1f else 0f
        aTo[2] = if (s == SwState.WARN) 1f else 0f
        glowTo = if (s == SwState.ON) 1f else 0f
        iconTo = when (s) {
            SwState.OFF -> iconOff
            SwState.BUSY -> skyC
            SwState.ON -> okC
            SwState.WARN -> warnC
            SwState.ERROR -> errC
        }
        if (first || !animated || !isAttachedToWindow) {
            iconFrom = iconTo
            knobFrom = knobTo
            for (i in 0..2) aFrom[i] = aTo[i]
            glowFrom = glowTo
            onMorph(1f)
        } else {
            morph.start()
        }
        updateBlinker()
    }

    private fun onMorph(f: Float) {
        knobCur = knobFrom + (knobTo - knobFrom) * overshoot.getInterpolation(f)
        for (i in 0..2) aCur[i] = aFrom[i] + (aTo[i] - aFrom[i]) * f
        glowCur = glowFrom + (glowTo - glowFrom) * f
        iconCur = argb.evaluate(f, iconFrom, iconTo) as Int
        invalidate()
    }

    private fun updateBlinker() {
        if (state == SwState.BUSY && isAttachedToWindow) {
            if (!blinker.isStarted) blinker.start()
        } else {
            blinker.cancel()
            blink = 1f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateBlinker()
    }

    override fun onDetachedFromWindow() {
        blinker.cancel()
        morph.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((trackW + 2 * pad).toInt(), (trackH + 2 * pad).toInt())
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"
        info.isCheckable = true
        info.isChecked = state == SwState.ON || state == SwState.WARN
    }

    private fun drawOverlay(canvas: Canvas, shader: Shader?, alpha: Float, r: Float) {
        if (shader == null || alpha <= 0.01f) return
        overlay.shader = shader
        overlay.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, r, r, overlay)
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(pad, pad, pad + trackW, pad + trackH)
        val r = trackH / 2f

        if (glowCur > 0.01f) {
            glowPaint.color = okC
            glowPaint.alpha = (127f * glowCur).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(
                rect.left, rect.top + dp(10f), rect.right, rect.bottom + dp(10f), r, r, glowPaint
            )
        }

        fill.color = trackFill
        canvas.drawRoundRect(rect, r, r, fill)
        drawOverlay(canvas, busyShader, aCur[0] * blink, r)
        drawOverlay(canvas, okShader, aCur[1], r)
        drawOverlay(canvas, warnShader, aCur[2], r)

        stroke.color = trackStroke
        stroke.strokeWidth = dp(1f)
        canvas.drawRoundRect(rect, r, r, stroke)

        val cx = pad + knobInset + knobD / 2f + knobCur * travel
        val cy = pad + trackH / 2f
        shadowPaint.alpha = 56
        canvas.drawCircle(cx, cy + dp(7f), knobD / 2f, shadowPaint)
        fill.color = knobFill
        canvas.drawCircle(cx, cy, knobD / 2f, fill)
        stroke.color = Color.WHITE
        canvas.drawCircle(cx, cy, knobD / 2f, stroke)

        iconPaint.color = iconCur
        val ir = dp(12.5f)
        arc.set(cx - ir, cy - ir + dp(1.5f), cx + ir, cy + ir + dp(1.5f))
        canvas.drawArc(arc, -50f, 280f, false, iconPaint)
        canvas.drawLine(cx, cy - dp(14f), cx, cy - dp(2f), iconPaint)
    }
}

/** Small iOS-style toggle (52x30 dp). */
class ToggleView(context: Context, private val tk: Tk) : View(context) {
    private val d = context.resources.displayMetrics.density
    private val argb = ArgbEvaluator()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var pos = 0f
    var checked = false
        private set
    var onChange: ((Boolean) -> Unit)? = null
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 250L
        addUpdateListener {
            pos = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            setChecked(!checked, true)
            onChange?.invoke(checked)
        }
    }

    fun setChecked(v: Boolean, animated: Boolean) {
        checked = v
        anim.cancel()
        if (animated && isAttachedToWindow) {
            anim.setFloatValues(pos, if (v) 1f else 0f)
            anim.start()
        } else {
            pos = if (v) 1f else 0f
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((52f * d).toInt(), (30f * d).toInt())
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"
        info.isCheckable = true
        info.isChecked = checked
    }

    override fun onDraw(canvas: Canvas) {
        val w = 52f * d
        val h = 30f * d
        paint.color = argb.evaluate(pos, tk.tin, tk.sky) as Int
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
        paint.color = Color.WHITE
        val cx = 15f * d + pos * 22f * d
        canvas.drawCircle(cx, h / 2f, 12f * d, paint)
    }
}

/** Two-colour round swatch used by the palette picker. */
class SwatchView(context: Context, private val a: Int, private val b: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        rect.set(0f, 0f, s, s)
        paint.color = b
        canvas.drawArc(rect, 0f, 360f, true, paint)
        paint.color = a
        canvas.drawArc(rect, 135f, 180f, true, paint)
    }
}

/** Segmented control (Light/Dark/Auto, Proxy/VPN, languages). */
class SegmentView(context: Context, private val tk: Tk) : LinearLayout(context) {
    private val d = context.resources.displayMetrics.density
    private val buttons = ArrayList<TextView>()
    var selectedIndex = -1
        private set
    var onSelect: ((Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        val p = (4f * d).toInt()
        setPadding(p, p, p, p)
        background = GradientDrawable().apply {
            setColor(tk.tile)
            cornerRadius = 18f * d
            setStroke((1f * d).toInt().coerceAtLeast(1), tk.line)
        }
    }

    fun setItems(labels: List<String>) {
        removeAllViews()
        buttons.clear()
        labels.forEachIndexed { i, label ->
            val b = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 14f
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    select(i)
                    onSelect?.invoke(i)
                }
            }
            val lp = LinearLayout.LayoutParams(0, (40f * d).toInt(), 1f)
            if (i > 0) lp.marginStart = (4f * d).toInt()
            addView(b, lp)
            buttons.add(b)
        }
        restyle()
    }

    fun select(i: Int) {
        if (i == selectedIndex) return
        selectedIndex = i
        restyle()
    }

    private fun restyle() {
        buttons.forEachIndexed { i, b ->
            val sel = i == selectedIndex
            b.setTextColor(if (sel) Color.WHITE else tk.muted)
            b.setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
            b.background = if (sel) {
                GradientDrawable().apply {
                    setColor(tk.sky)
                    cornerRadius = 14f * d
                }
            } else null
        }
    }
}

/** Drag / tap controller for the bottom settings sheet. */
class SheetController(
    private val sheet: View,
    private val grab: View,
    private val scrim: View,
    private val body: View
) {
    var open = false
    private var closedY = 0f
    private var y = 0f
    private var downY = 0f
    private var y0 = 0f
    private var downTime = 0L
    private var moved = false
    private var tracker: VelocityTracker? = null
    private val slop = grab.resources.displayMetrics.density * 6f

    init {
        grab.setOnClickListener { snap(!open) }
        grab.setOnTouchListener { v, e -> onTouch(v, e) }
        scrim.setOnClickListener { if (open) snap(false) }
        sheet.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> measure() }
    }

    private fun measure() {
        val c = (sheet.height - grab.height).toFloat().coerceAtLeast(0f)
        if (c != closedY || sheet.visibility != View.VISIBLE) {
            closedY = c
            applyNow()
            sheet.visibility = View.VISIBLE
        }
    }

    fun applyNow() {
        sheet.animate().cancel()
        scrim.animate().cancel()
        y = if (open) 0f else closedY
        sheet.translationY = y
        scrim.alpha = if (open) 1f else 0f
        scrim.isClickable = open
        body.importantForAccessibility =
            if (open) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun snap(o: Boolean) {
        open = o
        y = if (o) 0f else closedY
        sheet.animate().translationY(y).setDuration(380L)
            .setInterpolator(DecelerateInterpolator(2f)).start()
        scrim.animate().alpha(if (o) 1f else 0f).setDuration(380L).start()
        scrim.isClickable = o
        body.importantForAccessibility =
            if (o) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private fun drag(v: Float) {
        y = v
        sheet.translationY = v
        scrim.alpha = if (closedY > 0f) (1f - v / closedY).coerceIn(0f, 1f) else 0f
    }

    private fun onTouch(v: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sheet.animate().cancel()
                scrim.animate().cancel()
                tracker?.recycle()
                tracker = VelocityTracker.obtain()
                tracker?.addMovement(e)
                downY = e.rawY
                y0 = sheet.translationY
                downTime = e.eventTime
                moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(e)
                val dy = e.rawY - downY
                if (kotlin.math.abs(dy) > slop) moved = true
                if (moved) drag((y0 + dy).coerceIn(0f, closedY))
                return true
            }
            MotionEvent.ACTION_UP -> {
                tracker?.addMovement(e)
                tracker?.computeCurrentVelocity(1000)
                val vy = tracker?.yVelocity ?: 0f
                tracker?.recycle()
                tracker = null
                if (!moved) {
                    v.performClick()
                } else {
                    snap(if (vy < -400f) true else if (vy > 400f) false else y < closedY / 2f)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                tracker?.recycle()
                tracker = null
                snap(open)
                return true
            }
        }
        return false
    }
}

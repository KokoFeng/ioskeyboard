package com.feng.ioskeyboard

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Komponen UI bergaya iOS (kelompok berlatar abu, sel membulat, switch/slider halus). Tanpa efek liquid glass. */
class Palette(val dark: Boolean) {
    val bg = if (dark) 0xFF000000.toInt() else 0xFFF2F2F7.toInt()
    val card = if (dark) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
    val text = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    val sub = if (dark) 0xFF8E8E93.toInt() else 0xFF8A8A8E.toInt()
    val sep = if (dark) 0xFF38383A.toInt() else 0xFFC6C6C8.toInt()
    val accent = if (dark) 0xFF0A84FF.toInt() else 0xFF007AFF.toInt()
    val green = if (dark) 0xFF30D158.toInt() else 0xFF34C759.toInt()
    val red = if (dark) 0xFFFF453A.toInt() else 0xFFFF3B30.toInt()
    val fill = if (dark) 0xFF39393D.toInt() else 0xFFE9E9EA.toInt()
    val press = if (dark) 0xFF2C2C2E.toInt() else 0xFFD1D1D6.toInt()
    val segSel = if (dark) 0xFF636366.toInt() else 0xFFFFFFFF.toInt()
    val segBg = if (dark) 0xFF2C2C2E.toInt() else 0xFFEEEEF0.toInt()
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.sp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

fun Context.palette(): Palette =
    Palette((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)

class IosSwitch(c: Context, private val pal: Palette, on: Boolean, private val onChange: (Boolean) -> Unit) : View(c) {
    var isOn = on
        private set
    private var t = if (on) 1f else 0f
    private var anim: ValueAnimator? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()

    init { setOnClickListener { set(!isOn, true) } }

    fun set(v: Boolean, notify: Boolean) {
        isOn = v
        anim?.cancel()
        anim = ValueAnimator.ofFloat(t, if (v) 1f else 0f).apply {
            duration = 220; interpolator = DecelerateInterpolator()
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            start()
        }
        if (notify) onChange(v)
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(context.dp(51f).toInt(), context.dp(31f).toInt())

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.color = ArgbEvaluator().evaluate(t, pal.fill, pal.green) as Int
        rf.set(0f, 0f, w, h); cv.drawRoundRect(rf, h / 2, h / 2, p)
        val r = h / 2 - context.dp(2f)
        val cx = h / 2 + (w - h) * t
        p.color = 0x26000000; cv.drawCircle(cx, h / 2 + context.dp(1.2f), r + context.dp(0.6f), p)
        p.color = Color.WHITE; cv.drawCircle(cx, h / 2, r, p)
    }
}

class IosSlider(c: Context, private val pal: Palette, private val min: Int, private val max: Int, v: Int,
                private val onChange: (Int) -> Unit) : View(c) {
    var value = v
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), context.dp(40f).toInt())

    override fun onDraw(cv: Canvas) {
        val pad = context.dp(15f); val th = context.dp(4f); val cy = height / 2f
        val l = pad; val r = width - pad
        val x = l + (value - min).toFloat() / (max - min) * (r - l)
        p.color = pal.fill; rf.set(l, cy - th / 2, r, cy + th / 2); cv.drawRoundRect(rf, th / 2, th / 2, p)
        p.color = pal.accent; rf.set(l, cy - th / 2, x, cy + th / 2); cv.drawRoundRect(rf, th / 2, th / 2, p)
        p.color = 0x30000000; cv.drawCircle(x, cy + context.dp(1.2f), context.dp(14.5f), p)
        p.color = Color.WHITE; cv.drawCircle(x, cy, context.dp(14f), p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        val pad = context.dp(15f)
        val f = ((e.x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
        val nv = min + Math.round(f * (max - min))
        if (nv != value) { value = nv; onChange(nv) }
        invalidate()
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) parent?.requestDisallowInterceptTouchEvent(false)
        return true
    }
}

class IosSegment(c: Context, private val pal: Palette, private val labels: Array<String>, sel: Int,
                 private val onChange: (Int) -> Unit) : View(c) {
    private var pos = sel.toFloat()
    private var cur = sel
    private var anim: ValueAnimator? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = context.sp(14f) }
    private val rf = RectF()

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), context.dp(34f).toInt())

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = context.dp(9f)
        p.color = pal.segBg; rf.set(0f, 0f, w, h); cv.drawRoundRect(rf, r, r, p)
        val sw = w / labels.size; val m = context.dp(2f)
        p.color = 0x22000000; rf.set(pos * sw + m, m + context.dp(1f), (pos + 1) * sw - m, h - m + context.dp(1f)); cv.drawRoundRect(rf, r - m, r - m, p)
        p.color = pal.segSel; rf.set(pos * sw + m, m, (pos + 1) * sw - m, h - m); cv.drawRoundRect(rf, r - m, r - m, p)
        for (i in labels.indices) {
            tp.color = pal.text; tp.isFakeBoldText = i == cur
            cv.drawText(labels[i], i * sw + sw / 2, h / 2 + tp.textSize * 0.35f, tp)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val i = (e.x / (width / labels.size.toFloat())).toInt().coerceIn(0, labels.size - 1)
            if (i != cur) {
                cur = i
                anim?.cancel()
                anim = ValueAnimator.ofFloat(pos, i.toFloat()).apply {
                    duration = 220; interpolator = DecelerateInterpolator()
                    addUpdateListener { pos = it.animatedValue as Float; invalidate() }; start()
                }
                onChange(i)
            }
        }
        return true
    }
}

class Chevron(c: Context, private val color: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = c.dp(2.2f); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(context.dp(12f).toInt(), context.dp(20f).toInt())
    override fun onDraw(cv: Canvas) {
        p.color = color
        val w = width.toFloat(); val h = height.toFloat()
        cv.drawLine(w * 0.2f, h * 0.22f, w * 0.78f, h * 0.5f, p); cv.drawLine(w * 0.78f, h * 0.5f, w * 0.2f, h * 0.78f, p)
    }
}

class CheckMark(c: Context, private val color: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = c.dp(2.6f); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(context.dp(22f).toInt(), context.dp(22f).toInt())
    override fun onDraw(cv: Canvas) {
        p.color = color
        val w = width.toFloat(); val h = height.toFloat()
        cv.drawLine(w * 0.12f, h * 0.55f, w * 0.40f, h * 0.82f, p); cv.drawLine(w * 0.40f, h * 0.82f, w * 0.90f, h * 0.20f, p)
    }
}

/** Pembangun layar ala Pengaturan iOS. */
@Suppress("DEPRECATION")
class Screen(private val act: Activity, val pal: Palette) {
    private val ctx: Context = act
    val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(ctx)

    init {
        scroll.setBackgroundColor(pal.bg)
        scroll.isFillViewport = true
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.fitsSystemWindows = true
        scroll.addView(list, ViewGroup.LayoutParams(-1, -2))
        list.setPadding(0, 0, 0, ctx.dp(40f).toInt())
        act.window.statusBarColor = pal.bg
        act.window.navigationBarColor = pal.bg
        var f = act.window.decorView.systemUiVisibility
        if (!pal.dark) f = f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if (android.os.Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        act.window.decorView.systemUiVisibility = f
    }

    fun show() = act.setContentView(scroll)

    private fun lpH(top: Float = 0f, side: Float = 16f) =
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(top).toInt(); leftMargin = ctx.dp(side).toInt(); rightMargin = ctx.dp(side).toInt() }

    fun title(t: String, sub: String? = null) {
        list.addView(TextView(ctx).apply {
            text = t; setTextColor(pal.text); textSize = 34f; typeface = Fonts.heavy(ctx)
        }, lpH(24f, 20f))
        if (sub != null) list.addView(TextView(ctx).apply { text = sub; setTextColor(pal.sub); textSize = 15f }, lpH(2f, 20f))
    }

    fun section(t: String) {
        list.addView(TextView(ctx).apply { text = t.uppercase(); setTextColor(pal.sub); textSize = 13f; setPadding(ctx.dp(16f).toInt(), 0, 0, 0) }, lpH(26f, 20f).apply { bottomMargin = ctx.dp(7f).toInt() })
    }

    fun footer(t: String): TextView {
        val tv = TextView(ctx).apply { text = t; setTextColor(pal.sub); textSize = 13f; setPadding(ctx.dp(16f).toInt(), 0, ctx.dp(16f).toInt(), 0) }
        list.addView(tv, lpH(8f, 20f)); return tv
    }

    /** Kartu bersudut membulat berisi baris-baris dengan garis pemisah. */
    fun card(vararg rows: View) {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(pal.card); cornerRadius = ctx.dp(14f) }
            clipToOutline = true; outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        rows.forEachIndexed { i, r ->
            if (i > 0) card.addView(View(ctx).apply { setBackgroundColor(pal.sep) },
                LinearLayout.LayoutParams(-1, 1).apply { leftMargin = ctx.dp(16f).toInt() })
            card.addView(r, LinearLayout.LayoutParams(-1, -2))
        }
        list.addView(card, lpH(0f, 16f))
    }

    private fun pressable(v: View, action: (() -> Unit)?) {
        if (action == null) return
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.setBackgroundColor(pal.press)
                MotionEvent.ACTION_UP -> { view.setBackgroundColor(Color.TRANSPARENT); view.performClick() }
                MotionEvent.ACTION_CANCEL -> view.setBackgroundColor(Color.TRANSPARENT)
            }
            true
        }
        v.setOnClickListener { action() }
    }

    private fun rowBase(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ctx.dp(52f).toInt(); setPadding(ctx.dp(16f).toInt(), ctx.dp(8f).toInt(), ctx.dp(16f).toInt(), ctx.dp(8f).toInt())
    }

    private fun label(t: String, color: Int = pal.text) = TextView(ctx).apply { text = t; setTextColor(color); textSize = 17f; typeface = Fonts.main(ctx) }

    /** Baris navigasi: judul, nilai opsional di kanan, panah. */
    fun navRow(t: String, value: TextView? = null, color: Int = pal.text, action: () -> Unit): View {
        val r = rowBase()
        r.addView(label(t, color), LinearLayout.LayoutParams(0, -2, 1f))
        value?.let { r.addView(it) }
        r.addView(Chevron(ctx, pal.sub), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = ctx.dp(8f).toInt() })
        pressable(r, action)
        return r
    }

    /** Baris pilihan dengan centang biru (seperti daftar bahasa iOS). */
    fun checkRow(t: String, sub: String?, selected: Boolean, action: () -> Unit): View {
        val r = rowBase()
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(t))
        if (sub != null) col.addView(TextView(ctx).apply { text = sub; setTextColor(pal.sub); textSize = 13f })
        r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        if (selected) r.addView(CheckMark(ctx, pal.accent))
        pressable(r, action)
        return r
    }

    fun valueText(t: String) = TextView(ctx).apply { text = t; setTextColor(pal.sub); textSize = 17f; typeface = Fonts.main(ctx) }

    fun actionRow(t: String, color: Int, action: () -> Unit): View {
        val r = rowBase(); r.addView(label(t, color), LinearLayout.LayoutParams(0, -2, 1f)); pressable(r, action); return r
    }

    fun switchRow(t: String, pf: android.content.SharedPreferences, key: String, def: Boolean = true): View {
        val r = rowBase()
        r.addView(label(t), LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(IosSwitch(ctx, pal, pf.getBoolean(key, def)) { pf.edit().putBoolean(key, it).apply() })
        return r
    }

    fun toggleRow(t: String, on: Boolean, onChange: (Boolean) -> Unit): View {
        val r = rowBase()
        r.addView(label(t), LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(IosSwitch(ctx, pal, on, onChange))
        return r
    }

    fun inputRow(hint: String, initial: String = "", onText: (String) -> Unit): View {
        val r = rowBase()
        val et = android.widget.EditText(ctx).apply {
            this.hint = hint; setHintTextColor(pal.sub); setTextColor(pal.text); textSize = 17f
            background = null; setSingleLine(true); setPadding(0, 0, 0, 0); setText(initial)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(e: android.text.Editable?) { onText(e?.toString() ?: "") }
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            })
        }
        r.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
        return r
    }

    fun sliderRow(t: String, pf: android.content.SharedPreferences, key: String, min: Int, max: Int, def: Int, unit: String = ""): View {
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(16f).toInt(), ctx.dp(10f).toInt(), ctx.dp(16f).toInt(), ctx.dp(4f).toInt()) }
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val v = valueText("${pf.getInt(key, def)}$unit")
        top.addView(label(t), LinearLayout.LayoutParams(0, -2, 1f)); top.addView(v)
        box.addView(top)
        box.addView(IosSlider(ctx, pal, min, max, pf.getInt(key, def)) { nv -> pf.edit().putInt(key, nv).apply(); v.text = "$nv$unit" }, LinearLayout.LayoutParams(-1, -2))
        return box
    }

    fun segmentRow(t: String, labels: Array<String>, sel: Int, onChange: (Int) -> Unit): View {
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(16f).toInt(), ctx.dp(12f).toInt(), ctx.dp(16f).toInt(), ctx.dp(14f).toInt()) }
        box.addView(label(t), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ctx.dp(10f).toInt() })
        box.addView(IosSegment(ctx, pal, labels, sel, onChange), LinearLayout.LayoutParams(-1, -2))
        return box
    }

    /** Tombol biru penuh. */
    fun primaryButton(t: String, action: () -> Unit) {
        val b = TextView(ctx).apply {
            text = t; setTextColor(Color.WHITE); textSize = 17f; gravity = Gravity.CENTER
            typeface = Fonts.medium(ctx)
            background = GradientDrawable().apply { setColor(pal.accent); cornerRadius = ctx.dp(14f) }
            setPadding(0, ctx.dp(15f).toInt(), 0, ctx.dp(15f).toInt())
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.alpha = 0.7f
                    MotionEvent.ACTION_UP -> { v.alpha = 1f; action() }
                    MotionEvent.ACTION_CANCEL -> v.alpha = 1f
                }
                true
            }
        }
        list.addView(b, lpH(24f, 16f))
    }

    fun addRaw(v: View, top: Float = 16f) = list.addView(v, lpH(top, 16f))

    fun rawCard(content: View) {
        val card = FrameLayout(ctx).apply {
            background = GradientDrawable().apply { setColor(pal.card); cornerRadius = ctx.dp(14f) }
            clipToOutline = true; outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        card.addView(content, FrameLayout.LayoutParams(-1, -2))
        list.addView(card, lpH(0f, 16f))
    }
}

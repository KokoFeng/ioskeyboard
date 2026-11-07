package com.feng.ioskeyboard

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Tombol bulat kecil ala iOS (X, centang, bagikan, titik tiga). */
class IconButton(c: Context, private val pal: Palette, private val type: Int) : View(c) {
    companion object { const val X = 0; const val CHECK = 1; const val SHARE = 2; const val DOTS = 3 }
    var ready = true
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = c.dp(2f) }
    private val path = Path()
    private var down = false

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(context.dp(44f).toInt(), context.dp(44f).toInt())

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { down = true; invalidate() }
            MotionEvent.ACTION_UP -> { down = false; invalidate(); performClick() }
            MotionEvent.ACTION_CANCEL -> { down = false; invalidate() }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val d = context.dp(1f)
        val accent = type == CHECK && ready
        p.color = if (accent) pal.accent else if (pal.dark) 0xFF2C2C2E.toInt() else 0xFFEDEDF2.toInt()
        p.alpha = if (down) 160 else 255
        c.drawCircle(cx, cy, cx, p)
        st.color = if (accent) Color.WHITE else if (type == CHECK) pal.sub else pal.text
        when (type) {
            X -> { c.drawLine(cx - 6 * d, cy - 6 * d, cx + 6 * d, cy + 6 * d, st); c.drawLine(cx + 6 * d, cy - 6 * d, cx - 6 * d, cy + 6 * d, st) }
            CHECK -> { path.reset(); path.moveTo(cx - 7 * d, cy + 0.5f * d); path.lineTo(cx - 2 * d, cy + 6 * d); path.lineTo(cx + 8 * d, cy - 6 * d); c.drawPath(path, st) }
            SHARE -> {
                path.reset(); path.moveTo(cx - 6 * d, cy - 1 * d); path.lineTo(cx - 6 * d, cy + 8 * d); path.lineTo(cx + 6 * d, cy + 8 * d); path.lineTo(cx + 6 * d, cy - 1 * d)
                c.drawPath(path, st)
                c.drawLine(cx, cy + 3 * d, cx, cy - 9 * d, st)
                c.drawLine(cx, cy - 9 * d, cx - 4 * d, cy - 5 * d, st); c.drawLine(cx, cy - 9 * d, cx + 4 * d, cy - 5 * d, st)
            }
            else -> { p.color = pal.sub; p.alpha = 255; for (i in -1..1) c.drawCircle(cx + i * 7 * d, cy, 1.8f * d, p) }
        }
    }
}

/** Panggung animasi: gelembung bergoyang -> pecah -> emoji gabungan muncul. */
class CombineView(c: Context, private val pal: Palette) : View(c) {
    var a: Bitmap? = null
    var b: Bitmap? = null
    private var result: Bitmap? = null
    private var t0 = 0L
    private var loadMs = 1900L
    private val popMs = 460L
    private var fired = false
    var onReady: (() -> Unit)? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rf = RectF()
    private val ang = FloatArray(14) { it * (2 * Math.PI.toFloat() / 14) + (it % 3) * 0.12f }
    private val spd = FloatArray(14) { 0.75f + (it * 37 % 10) / 22f }

    fun start(res: Bitmap, ms: Long) { result = res; loadMs = ms; t0 = SystemClock.uptimeMillis(); fired = false; invalidate() }
    fun ready() = fired

    private fun easeBack(u: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1; val x = u - 1; return 1 + c3 * x * x * x + c1 * x * x }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2; val cy = h * 0.46f; val R = min(w, h) * 0.27f
        val el = SystemClock.uptimeMillis() - t0
        val tone = if (pal.dark) 0xFFFFFFFF.toInt() else 0xFF6B7A99.toInt()
        val d = context.dp(1f)
        if (el < loadMs) {
            val s = el / 1000f
            val rx = R * (1f + 0.035f * sin(s * 6.2f)); val ry = R * (1f + 0.035f * cos(s * 5.1f))
            val ox = sin(s * 2.1f) * 4 * d; val oy = cos(s * 1.7f) * 5 * d
            drawBubble(cv, cx + ox, cy + oy, rx, ry, 1f, tone)
            // dua emoji di dalam gelembung
            val sz = R * 0.78f
            a?.let { rf.set(cx + ox - R * 0.42f - sz / 2, cy + oy - sz / 2 - sin(s * 3f) * 3 * d, cx + ox - R * 0.42f + sz / 2, cy + oy + sz / 2 - sin(s * 3f) * 3 * d); cv.drawBitmap(it, null, rf, p) }
            b?.let { rf.set(cx + ox + R * 0.42f - sz / 2, cy + oy - sz / 2 + sin(s * 3f) * 3 * d, cx + ox + R * 0.42f + sz / 2, cy + oy + sz / 2 + sin(s * 3f) * 3 * d); cv.drawBitmap(it, null, rf, p) }
            // gelembung kecil
            for (i in 0..2) {
                val bx = cx + R * (0.9f + 0.22f * i) * (if (i % 2 == 0) 1 else -1) + sin(s * (1.3f + i)) * 6 * d
                val by = cy - R * (0.35f + 0.28f * i) + cos(s * (1.1f + i)) * 6 * d
                drawBubble(cv, bx, by, R * (0.16f - 0.03f * i), R * (0.16f - 0.03f * i), 0.9f, tone)
            }
            // bilah kemajuan
            val bw = R * 1.5f; val by = cy + R * 1.35f
            val prog = (el / loadMs.toFloat()).let { it * it * (3 - 2 * it) }
            p.shader = null; p.color = if (pal.dark) 0xFF3A3A3C.toInt() else 0xFFD8D8DE.toInt()
            rf.set(cx - bw / 2, by, cx + bw / 2, by + 3 * d); cv.drawRoundRect(rf, 2 * d, 2 * d, p)
            p.color = pal.accent; rf.set(cx - bw / 2, by, cx - bw / 2 + bw * prog, by + 3 * d); cv.drawRoundRect(rf, 2 * d, 2 * d, p)
            postInvalidateOnAnimation(); return
        }
        val u = ((el - loadMs) / popMs.toFloat()).coerceIn(0f, 1f)
        if (u < 1f) {
            // cincin gelembung memuai lalu hilang + percikan
            drawBubble(cv, cx, cy, R * (1f + 0.35f * u), R * (1f + 0.35f * u), (1f - u), tone)
            for (i in ang.indices) {
                val dist = R * (1.0f + 1.0f * spd[i] * (1f - (1f - u) * (1f - u)))
                val px = cx + cos(ang[i]) * dist; val py = cy + sin(ang[i]) * dist
                p.shader = null; p.color = Color.argb(((1f - u) * 200).toInt(), Color.red(tone), Color.green(tone), Color.blue(tone))
                cv.drawCircle(px, py, 6 * d * (1f - u * 0.7f), p)
            }
        }
        if (!fired && u > 0.55f) { fired = true; onReady?.invoke() }
        val sc = 0.5f + 0.5f * easeBack(u)
        val float = if (u >= 1f) sin(el / 700f) * 3 * d else 0f
        val size = R * 2.3f * sc
        // bayangan lembut
        p.shader = null; p.color = Color.argb((38 * min(1f, u * 2)).toInt(), 0, 0, 0)
        rf.set(cx - R * 0.8f, cy + R * 1.25f, cx + R * 0.8f, cy + R * 1.38f); cv.drawOval(rf, p)
        p.alpha = (255 * min(1f, u * 2.5f)).toInt()
        result?.let { rf.set(cx - size / 2, cy - size / 2 + float, cx + size / 2, cy + size / 2 + float); cv.drawBitmap(it, null, rf, p) }
        p.alpha = 255
        postInvalidateOnAnimation()
    }

    private fun drawBubble(cv: Canvas, x: Float, y: Float, rx: Float, ry: Float, alpha: Float, tone: Int) {
        val r = max(rx, ry)
        p.shader = RadialGradient(x - r * 0.25f, y - r * 0.3f, r * 1.2f,
            intArrayOf(Color.argb((10 * alpha).toInt(), 255, 255, 255), Color.argb((22 * alpha).toInt(), Color.red(tone), Color.green(tone), Color.blue(tone)), Color.argb((90 * alpha).toInt(), Color.red(tone), Color.green(tone), Color.blue(tone))),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        rf.set(x - rx, y - ry, x + rx, y + ry); cv.drawOval(rf, p)
        p.shader = null
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        st.strokeWidth = context.dp(1.4f); st.color = Color.argb((120 * alpha).toInt(), Color.red(tone), Color.green(tone), Color.blue(tone))
        cv.drawOval(rf, st)
        // kilau
        st.strokeWidth = context.dp(3f); st.strokeCap = Paint.Cap.ROUND; st.color = Color.argb((200 * alpha).toInt(), 255, 255, 255)
        rf.set(x - rx * 0.78f, y - ry * 0.78f, x + rx * 0.78f, y + ry * 0.78f); cv.drawArc(rf, 200f, 38f, false, st)
        st.color = Color.argb((120 * alpha).toInt(), 255, 150, 220)
        rf.set(x - rx * 0.9f, y - ry * 0.9f, x + rx * 0.9f, y + ry * 0.9f); cv.drawArc(rf, 20f, 50f, false, st)
        st.color = Color.argb((120 * alpha).toInt(), 120, 220, 255)
        cv.drawArc(rf, 200f, 30f, false, st)
    }
}

class CombineActivity : Activity() {
    private lateinit var pal: Palette
    private lateinit var view: CombineView
    private lateinit var check: IconButton
    private lateinit var srcRow: TextView
    private var ea = ""; private var eb = ""
    private var ga: Bitmap? = null; private var gb: Bitmap? = null
    private var opt = Composer.Opt(0)
    private var result: Bitmap? = null
    private var tf: Typeface? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ea = intent.getStringExtra("a") ?: ""; eb = intent.getStringExtra("b") ?: ""
        if (ea.isEmpty() || eb.isEmpty()) { finish(); return }
        pal = palette()
        window.statusBarColor = pal.bg; window.navigationBarColor = pal.bg
        @Suppress("DEPRECATION")
        if (!pal.dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        tf = Composer.face(this)
        val (base, acc, style) = Composer.auto(ea, eb)
        ea = base; eb = acc; opt = Composer.Opt(style)
        ga = Composer.glyph(ea, tf); gb = Composer.glyph(eb, tf)

        val root = FrameLayout(this).apply { setBackgroundColor(pal.bg); fitsSystemWindows = true }
        view = CombineView(this, pal).apply { this.a = ga; this.b = gb }
        root.addView(view, FrameLayout.LayoutParams(-1, -1))

        val pad = dp(16f).toInt()
        val close = IconButton(this, pal, IconButton.X).apply { setOnClickListener { finish() } }
        root.addView(close, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(pad, pad, 0, 0) })
        check = IconButton(this, pal, IconButton.CHECK).apply { ready = false; setOnClickListener { if (view.ready()) showSaveSheet() } }
        root.addView(check, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, pad, pad, 0) })

        srcRow = TextView(this).apply { text = "$ea  $eb"; textSize = 30f; gravity = Gravity.CENTER; typeface = tf; alpha = 0f }
        root.addView(srcRow, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { topMargin = dp(86f).toInt() })

        val share = IconButton(this, pal, IconButton.SHARE).apply { setOnClickListener { share() } }
        root.addView(share, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(pad, 0, 0, pad) })
        val more = IconButton(this, pal, IconButton.DOTS)
        more.setOnClickListener { menu(more) }
        root.addView(more, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, pad, pad) })

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val input = EditText(this).apply {
            hint = "Jelaskan perubahan (mis. besar, kiri, putar)"; setHintTextColor(pal.sub); setTextColor(pal.text); textSize = 16f
            setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_DONE
            background = GradientDrawable().apply { setColor(if (pal.dark) 0xFF2C2C2E.toInt() else 0xFFEDEDF2.toInt()); cornerRadius = dp(22f) }
            setPadding(dp(18f).toInt(), 0, dp(18f).toInt(), 0)
            setOnEditorActionListener { v, _, _ ->
                opt = Composer.applyText(opt, v.text.toString()); v.text = ""
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
                regenerate(850L); true
            }
        }
        box.addView(input, LinearLayout.LayoutParams(-1, dp(44f).toInt()))
        box.addView(TextView(this).apply { text = "Hasil berupa gabungan emoji Apple, bukan gambar buatan AI."; setTextColor(pal.sub); textSize = 12f; gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f).toInt() })
        root.addView(box, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(28f).toInt(), 0, dp(28f).toInt(), dp(84f).toInt()) })

        setContentView(root)
        view.onReady = {
            check.ready = true
            srcRow.animate().alpha(1f).setDuration(300).start()
        }
        regenerate(1900L)
    }

    private fun regenerate(ms: Long) {
        val a0 = ga ?: return; val b0 = gb ?: return
        result = Composer.compose(a0, b0, opt)
        check.ready = false
        srcRow.animate().alpha(0f).setDuration(120).start()
        view.start(result!!, ms)
    }

    private fun menu(anchor: View) {
        val m = PopupMenu(this, anchor)
        Composer.STYLE_NAMES.forEachIndexed { i, n -> m.menu.add(0, i, i, (if (opt.style == i) "✓ " else "") + n) }
        m.menu.add(1, 10, 10, "Tukar urutan"); m.menu.add(1, 11, 11, "Acak ulang")
        m.setOnMenuItemClickListener { it ->
            when (it.itemId) {
                in 0..4 -> opt.style = it.itemId
                10 -> { val t = ea; ea = eb; eb = t; val g = ga; ga = gb; gb = g; view.a = ga; view.b = gb; srcRow.text = "$ea  $eb" }
                11 -> { opt.scaleB = 0.85f + (Math.random().toFloat() * 0.5f); opt.dx = (Math.random().toFloat() - 0.5f) * 90f; opt.dy = (Math.random().toFloat() - 0.5f) * 70f; opt.rot = (Math.random().toFloat() - 0.5f) * 50f; opt.mirror = Math.random() < 0.3 }
            }
            regenerate(1000L); true
        }
        m.show()
    }

    private fun share() {
        val r = result ?: return
        try {
            val f = java.io.File(GenStore.shareDir(this), "gabungan.png")
            FileOutputStream(f).use { r.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Bagikan emoji"))
        } catch (_: Exception) { Toast.makeText(this, "Gagal membagikan", Toast.LENGTH_SHORT).show() }
    }

    /** Popup ala iOS: simpan di emoji atau stiker. */
    private fun showSaveSheet() {
        val r = result ?: return
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val sheet = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10f).toInt(), 0, dp(10f).toInt(), dp(10f).toInt()) }
        fun card(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(if (pal.dark) 0xFF2C2C2E.toInt() else 0xFFF9F9FB.toInt()); cornerRadius = dp(16f) }
        }
        fun row(t: String, color: Int, bold: Boolean, onTap: () -> Unit) = TextView(this).apply {
            text = t; setTextColor(color); textSize = 19f; gravity = Gravity.CENTER
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(17f).toInt(), 0, dp(17f).toInt())
            setOnClickListener { onTap() }
        }
        fun sep() = View(this).apply { setBackgroundColor(pal.sep) }
        val c1 = card()
        c1.addView(TextView(this).apply { text = "Simpan emoji gabungan"; setTextColor(pal.sub); textSize = 13f; gravity = Gravity.CENTER; setPadding(0, dp(14f).toInt(), 0, dp(10f).toInt()) })
        c1.addView(sep(), LinearLayout.LayoutParams(-1, 1))
        c1.addView(row("Simpan di Emoji", pal.accent, false) {
            GenStore.save(this, r); d.dismiss(); Toast.makeText(this, "Tersimpan di Emoji (tab Terbaru)", Toast.LENGTH_SHORT).show(); finish()
        })
        c1.addView(sep(), LinearLayout.LayoutParams(-1, 1))
        c1.addView(row("Simpan di Stiker", pal.accent, false) {
            StickerStore.save(this, r); d.dismiss(); Toast.makeText(this, "Tersimpan di Stiker", Toast.LENGTH_SHORT).show(); finish()
        })
        sheet.addView(c1, LinearLayout.LayoutParams(-1, -2))
        val c2 = card()
        c2.addView(row("Batal", pal.accent, true) { d.dismiss() })
        sheet.addView(c2, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f).toInt() })
        d.setContentView(sheet)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); setDimAmount(0.35f)
        }
        sheet.translationY = dp(320f)
        d.show()
        sheet.animate().translationY(0f).setDuration(280).setInterpolator(DecelerateInterpolator(1.6f)).start()
    }
}

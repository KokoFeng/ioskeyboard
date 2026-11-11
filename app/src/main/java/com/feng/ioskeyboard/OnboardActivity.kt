package com.feng.ioskeyboard

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Ikon besar bersudut membulat ala ikon Pengaturan iOS. */
class Glyph(c: Context, private val type: Int, private val c1: Int, private val c2: Int) : View(c) {
    companion object { const val KEYBOARD = 0; const val TOGGLE = 1; const val GLOBE = 2; const val MIC = 3; const val PERSON = 4; const val CHECK = 5; const val APP = 6 }
    var progress = 1f
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = c.dp(4.5f) }
    private val rf = RectF()
    private val path = Path()
    private val seg = Path()
    private val appIcon: Bitmap? = if (type == APP) android.graphics.BitmapFactory.decodeResource(c.resources, R.drawable.app_icon) else null
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(context.dp(112f).toInt(), context.dp(112f).toInt())

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val u = context.dp(1f)
        appIcon?.let { c.drawBitmap(it, null, RectF(0f, 0f, w, h), bp); return }
        p.shader = LinearGradient(0f, 0f, 0f, h, c1, c2, Shader.TileMode.CLAMP)
        rf.set(0f, 0f, w, h); c.drawRoundRect(rf, 26 * u, 26 * u, p); p.shader = null
        val cx = w / 2; val cy = h / 2
        p.color = Color.WHITE
        when (type) {
            KEYBOARD -> {
                rf.set(cx - 30 * u, cy - 19 * u, cx + 30 * u, cy + 19 * u); c.drawRoundRect(rf, 7 * u, 7 * u, st)
                for (r in 0..1) for (i in 0..3) c.drawCircle(cx - 18 * u + i * 12 * u + r * 6 * u, cy - 8 * u + r * 9 * u, 2 * u, p)
                c.drawLine(cx - 14 * u, cy + 11 * u, cx + 14 * u, cy + 11 * u, st)
            }
            TOGGLE -> {
                rf.set(cx - 30 * u, cy - 17 * u, cx + 30 * u, cy + 17 * u); c.drawRoundRect(rf, 17 * u, 17 * u, st)
                c.drawCircle(cx + 13 * u, cy, 11 * u, p)
            }
            GLOBE -> {
                c.drawCircle(cx, cy, 26 * u, st)
                rf.set(cx - 11 * u, cy - 26 * u, cx + 11 * u, cy + 26 * u); c.drawOval(rf, st)
                c.drawLine(cx - 26 * u, cy, cx + 26 * u, cy, st)
            }
            MIC -> {
                rf.set(cx - 9 * u, cy - 28 * u, cx + 9 * u, cy + 6 * u); c.drawRoundRect(rf, 9 * u, 9 * u, st)
                rf.set(cx - 18 * u, cy - 14 * u, cx + 18 * u, cy + 20 * u); c.drawArc(rf, 0f, 180f, false, st)
                c.drawLine(cx, cy + 20 * u, cx, cy + 30 * u, st); c.drawLine(cx - 8 * u, cy + 30 * u, cx + 8 * u, cy + 30 * u, st)
            }
            PERSON -> {
                c.drawCircle(cx, cy - 10 * u, 12 * u, st)
                rf.set(cx - 26 * u, cy + 8 * u, cx + 26 * u, cy + 58 * u); c.drawArc(rf, 200f, 140f, false, st)
            }
            else -> {
                val a = (progress / 0.6f).coerceIn(0f, 1f)
                rf.set(cx - 29 * u, cy - 29 * u, cx + 29 * u, cy + 29 * u); c.drawArc(rf, -90f, 360f * a, false, st)
                val tp = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
                path.reset(); path.moveTo(cx - 13 * u, cy + 1 * u); path.lineTo(cx - 4 * u, cy + 11 * u); path.lineTo(cx + 15 * u, cy - 11 * u)
                val pm = PathMeasure(path, false); seg.reset(); pm.getSegment(0f, pm.length * tp, seg, true)
                c.drawPath(seg, st)
            }
        }
    }
}

/** Lencana centang hijau kecil. */
class Badge(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = c.dp(3f) }
    private val path = Path()
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(context.dp(36f).toInt(), context.dp(36f).toInt())
    override fun onDraw(c: Canvas) {
        val r = width / 2f; val u = context.dp(1f)
        p.color = Color.WHITE; c.drawCircle(r, r, r, p)
        p.color = 0xFF34C759.toInt(); c.drawCircle(r, r, r - 2.5f * u, p)
        path.reset(); path.moveTo(r - 7 * u, r + 1 * u); path.lineTo(r - 2 * u, r + 6 * u); path.lineTo(r + 8 * u, r - 6 * u); c.drawPath(path, st)
    }
}

class DotsView(c: Context, private val pal: Palette, private val n: Int) : View(c) {
    private var pos = 0f
    private var anim: ValueAnimator? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), context.dp(20f).toInt())
    fun setStep(s: Int) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(pos, s.toFloat()).apply { duration = 320; interpolator = DecelerateInterpolator(); addUpdateListener { pos = it.animatedValue as Float; invalidate() }; start() }
    }
    override fun onDraw(c: Canvas) {
        val u = context.dp(1f); val gap = 10 * u; val d = 7 * u
        val total = n * d + (n - 1) * gap + 14 * u
        var x = (width - total) / 2; val cy = height / 2f
        for (i in 0 until n) {
            val near = (1f - Math.abs(pos - i)).coerceIn(0f, 1f)
            val w = d + 14 * u * near
            p.color = if (near > 0.5f) pal.accent else pal.sep
            p.alpha = (110 + 145 * near).toInt()
            rf.set(x, cy - d / 2, x + w, cy + d / 2); c.drawRoundRect(rf, d / 2, d / 2, p)
            x += w + gap
        }
    }
}

class OnboardActivity : Activity() {
    private class Page(val view: View, val glyph: Glyph, val badge: View, val title: View, val body: View, val extra: View?)

    private lateinit var pal: Palette
    private lateinit var pf: SharedPreferences
    private lateinit var holder: FrameLayout
    private lateinit var dots: DotsView
    private lateinit var primary: TextView
    private lateinit var secondary: TextView
    private var current: Page? = null
    private var step = 0
    private val steps = 6
    private var badgeShown = false
    private var nameField: EditText? = null
    private var nameMode = 3

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Lang.load(this)
        if (Guard.blockIfTampered(this)) return
        pal = palette()
        pf = getSharedPreferences("kb", Context.MODE_PRIVATE)
        window.statusBarColor = pal.bg; window.navigationBarColor = pal.bg
        @Suppress("DEPRECATION")
        if (!pal.dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if (android.os.Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)

        val root = FrameLayout(this).apply { setBackgroundColor(pal.bg); fitsSystemWindows = true }
        dots = DotsView(this, pal, steps)
        root.addView(dots, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { topMargin = dp(20f).toInt() })
        holder = FrameLayout(this)
        root.addView(holder, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(48f).toInt(); bottomMargin = dp(150f).toInt() })

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        primary = TextView(this).apply {
            textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Fonts.medium(this@OnboardActivity)
            background = GradientDrawable().apply { setColor(pal.accent); cornerRadius = dp(14f) }
            setPadding(0, dp(16f).toInt(), 0, dp(16f).toInt())
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.8f).setDuration(80).start()
                    MotionEvent.ACTION_UP -> { v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start(); onPrimary() }
                    MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start()
                }
                true
            }
        }
        secondary = TextView(this).apply {
            text = Lang.tr("Lewati"); textSize = 16f; setTextColor(pal.accent); gravity = Gravity.CENTER
            setPadding(0, dp(14f).toInt(), 0, dp(6f).toInt())
            setOnClickListener { go(step + 1) }
        }
        bottom.addView(primary, LinearLayout.LayoutParams(-1, -2))
        bottom.addView(secondary, LinearLayout.LayoutParams(-1, -2))
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(24f).toInt(), 0, dp(24f).toInt(), dp(20f).toInt()) })
        setContentView(root)
        go(0, first = true)
    }

    // ---------- status ----------
    private fun enabled() = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS) ?: "").contains(packageName)
    private fun picked() = (Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "").contains(packageName)
    private fun micOk() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun done(s: Int) = when (s) { 1 -> enabled(); 2 -> picked(); 3 -> micOk(); else -> false }

    private fun bind() {
        val d = done(step)
        primary.text = when (step) {
            0 -> Lang.tr("Mulai")
            1 -> if (d) Lang.tr("Lanjut") else Lang.tr("Buka Pengaturan")
            2 -> if (d) Lang.tr("Lanjut") else Lang.tr("Pilih Keyboard")
            3 -> if (d) Lang.tr("Lanjut") else Lang.tr("Izinkan")
            4 -> Lang.tr("Lanjut")
            else -> Lang.tr("Selesai")
        }
        secondary.visibility = if (step in 1..3 && !d) View.VISIBLE else View.INVISIBLE
        if (d && step in 1..3 && !badgeShown) { badgeShown = true; showBadge(current) }
    }

    private fun showBadge(pg: Page?) {
        val b = pg?.badge ?: return
        b.visibility = View.VISIBLE; b.scaleX = 0f; b.scaleY = 0f
        b.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(OvershootInterpolator(2.2f)).start()
    }

    private fun onPrimary() {
        val d = done(step)
        when (step) {
            0 -> go(1)
            1 -> if (d) go(2) else startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            2 -> if (d) go(3) else (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            3 -> if (d) go(4) else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            4 -> { saveName(); go(5) }
            else -> { pf.edit().putBoolean("onboarded", true).apply(); startActivity(Intent(this, SetupActivity::class.java)); finish() }
        }
    }

    private fun saveName() {
        pf.edit().putString("user_name", nameField?.text?.toString()?.trim() ?: "").putInt("name_show", nameMode).apply()
    }

    override fun onResume() { super.onResume(); if (current != null) bind() }
    override fun onWindowFocusChanged(f: Boolean) { super.onWindowFocusChanged(f); if (f && current != null) bind() }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { bind() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (step > 0) go(step - 1) else super.onBackPressed() }

    // ---------- halaman ----------
    private fun page(type: Int, c1: Int, c2: Int, title: String, body: String, extra: View? = null): Page {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(32f).toInt(), 0, dp(32f).toInt(), 0) }
        val wrap = FrameLayout(this)
        val g = Glyph(this, type, c1, c2)
        wrap.addView(g, FrameLayout.LayoutParams(-2, -2).apply { setMargins(0, dp(10f).toInt(), dp(10f).toInt(), 0) })
        val badge = Badge(this).apply { visibility = View.INVISIBLE }
        wrap.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        val t = TextView(this).apply { text = title; textSize = 28f; setTextColor(pal.text); gravity = Gravity.CENTER; typeface = Fonts.heavy(this@OnboardActivity) }
        val bd = TextView(this).apply { text = body; textSize = 16f; setTextColor(pal.sub); gravity = Gravity.CENTER; setLineSpacing(0f, 1.15f); typeface = Fonts.main(this@OnboardActivity) }
        col.addView(wrap, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(40f).toInt() })
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(30f).toInt() })
        col.addView(bd, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f).toInt() })
        if (extra != null) col.addView(extra, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(26f).toInt() })
        val sv = android.widget.ScrollView(this).apply { addView(col); isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER; isVerticalScrollBarEnabled = false }
        return Page(sv, g, badge, t, bd, extra)
    }

    private fun nameExtra(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val et = EditText(this).apply {
            hint = Lang.tr("Nama kamu"); setHintTextColor(pal.sub); setTextColor(pal.text); textSize = 18f
            setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = GradientDrawable().apply { setColor(pal.card); cornerRadius = dp(14f) }
            setPadding(dp(18f).toInt(), dp(15f).toInt(), dp(18f).toInt(), dp(15f).toInt())
            setText(pf.getString("user_name", "") ?: "")
        }
        nameField = et
        box.addView(et, LinearLayout.LayoutParams(-1, -2))
        box.addView(TextView(this).apply { text = Lang.tr("Tampilkan nama di"); setTextColor(pal.sub); textSize = 13f }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20f).toInt(); bottomMargin = dp(8f).toInt() })
        nameMode = if (pf.contains("name_show")) pf.getInt("name_show", 3) else 3
        box.addView(IosSegment(this, pal, arrayOf(Lang.tr("Tidak"), Lang.tr("Spasi"), "Header", Lang.tr("Keduanya")), nameMode) { nameMode = it }, LinearLayout.LayoutParams(-1, -2))
        return box
    }

    private fun langExtra(): View =
        IosSegment(this, pal, arrayOf("Indonesia", "English"), if (Lang.en) 1 else 0) { i ->
            holder.post {
                Lang.set(this, i)
                holder.removeAllViews(); current = null
                go(0, first = true)
            }
        }

    private fun buildPage(s: Int): Page = when (s) {
        0 -> page(Glyph.APP, 0xFF4DA3FF.toInt(), 0xFF0A6CDB.toInt(), Lang.tr("Selamat datang"), Lang.tr("Keyboard bergaya iOS untuk Android.\nKita siapkan dalam beberapa langkah singkat."), langExtra())
        1 -> page(Glyph.TOGGLE, 0xFF5BDB7D.toInt(), 0xFF27AE4B.toInt(), Lang.tr("Aktifkan keyboard"), Lang.tr("Nyalakan \"KeyOS\" pada daftar keyboard,\nlalu kembali ke sini."))
        2 -> page(Glyph.GLOBE, 0xFF4DA3FF.toInt(), 0xFF0A6CDB.toInt(), Lang.tr("Pilih keyboard"), Lang.tr("Pilih \"KeyOS\" sebagai keyboard yang dipakai saat mengetik."))
        3 -> page(Glyph.MIC, 0xFFFF7A70.toInt(), 0xFFD92D20.toInt(), Lang.tr("Izin mikrofon"), Lang.tr("Dipakai untuk mengetik dengan suara.\nKamu bisa melewatinya."))
        4 -> page(Glyph.PERSON, 0xFFFFB340.toInt(), 0xFFFF8A00.toInt(), Lang.tr("Siapa namamu?"), Lang.tr("Nama bisa ditampilkan di tombol spasi atau di bagian atas pengaturan."), nameExtra())
        else -> page(Glyph.CHECK, 0xFF5BDB7D.toInt(), 0xFF27AE4B.toInt(), Lang.tr("Siap dipakai"), Lang.tr("Coba ketik di aplikasi apa pun.\nSemua pengaturan ada di halaman utama."))
    }

    private fun go(n: Int, first: Boolean = false) {
        if (n >= steps || n < 0) return
        val old = current
        step = n
        badgeShown = false
        val pg = buildPage(n)
        holder.addView(pg.view, FrameLayout.LayoutParams(-1, -1))
        current = pg
        dots.setStep(n)
        bind()
        // masuk
        val dir = if (old == null) 0f else dp(70f)
        pg.view.alpha = 0f; pg.view.translationX = dir
        pg.view.animate().alpha(1f).translationX(0f).setDuration(if (first) 1 else 380).setInterpolator(DecelerateInterpolator(1.8f)).start()
        pg.glyph.scaleX = 0.5f; pg.glyph.scaleY = 0.5f; pg.glyph.alpha = 0f
        pg.glyph.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(if (first) 120 else 160).setDuration(560).setInterpolator(OvershootInterpolator(1.7f)).start()
        for ((i, v) in listOf(pg.title, pg.body, pg.extra).withIndex()) {
            val view = v ?: continue
            view.alpha = 0f; view.translationY = dp(18f)
            view.animate().alpha(1f).translationY(0f).setStartDelay(260L + i * 90L).setDuration(420).setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
        if (pg.glyph.progress != 1f || n == 5) {
            pg.glyph.progress = 0f
            ValueAnimator.ofFloat(0f, 1f).apply { duration = 800; startDelay = 360; interpolator = DecelerateInterpolator(); addUpdateListener { pg.glyph.progress = it.animatedValue as Float }; start() }
        }
        // keluar
        old?.view?.animate()?.alpha(0f)?.translationX(-dp(70f))?.setDuration(260)?.withEndAction { holder.removeView(old.view) }?.start()
    }
}

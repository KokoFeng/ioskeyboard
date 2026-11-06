package com.feng.ioskeyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowInsets
import android.widget.OverScroller
import android.widget.Toast
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Keyboard ala iPhone (gelap / terang) yang digambar langsung dengan Canvas.
 * Koordinat desain mengikuti gambar referensi 1080 x 914.
 */
class KeyboardView(ctx: Context, private val l: Listener) : View(ctx) {

    interface Listener {
        fun onText(s: String)
        fun onDelete()
        fun onSpace()
        fun onReturn()
        fun onShiftChange(state: Int)
        fun onSuggestion(slot: Int)
        fun onGlobe(long: Boolean)
        fun onMic()
        fun onCursorMove(dir: Int)
        fun onSticker(f: File)
        fun onCreateSticker()
        fun onOpenSettings()
        fun onLanguage(i: Int)
        fun onClipText(s: String)
        fun onClipImage(f: File)
    }

    // ---------- warna (tema) ----------
    private var cBg = 0; private var cKey = 0; private var cFunc = 0; private var cFuncDown = 0
    private var cPopup = 0; private var cDiv = 0; private var cSug = 0; private var cBlue = 0
    private var cBlueDown = 0; private var cHint = 0; private var cFg = Color.WHITE; private var cShadow = 0
    private var cMenuBg = 0; private var cMenuSel = 0; private var cCell = 0; private var cTabSel = 0
    private var cPill = 0
    private var light = false

    companion object {
        const val SHIFT = -1; const val DEL = -2; const val NUM = -3; const val ABC = -4
        const val EMOJI = -5; const val SPACE = -6; const val RET = -7; const val SYM = -8
        const val GLOBE = -9; const val MIC = -10
        const val K_LETTER = 0; const val K_FUNC = 1; const val K_ICON = 2; const val K_PLAIN = 4
        const val DW = 1080f; const val DH = 914f
        private const val ONE_F = 0.82f // lebar keyboard mode satu tangan
        private val TAB_ICONS = arrayOf("🌙", "🕘", "😀", "🐶", "🍎", "⚽", "🚗", "💡", "❤️", "🏁")
    }

    class Key(val label: String, val code: Int, val x: Float, val y: Float, val w: Float, val h: Float, val kind: Int)

    // ---------- state ----------
    var shift = 1
        set(v) { field = v; invalidate() }
    private var page = 0 // 0 huruf, 1 angka, 2 simbol, 3 emoji
    var showReturnArrow = true
    var returnLabel = ""
    var probs: Map<Char, Float> = emptyMap()
    private var sug = arrayOf("", "", "")
    private var sugBold = false
    private var bottomInset = 0
    private var scale = 1f
    private var hf = 1f
    private var iconDy = 0f
    private var iconSz = 1f
    private var oneHand = 0 // 0 normal, 1 kiri, 2 kanan
    private val keysCache = arrayOfNulls<List<Key>>(5)
    private val rf = RectF()
    private val path = Path()
    private val handler = Handler(Looper.getMainLooper())

    // emoji / stiker
    val repo = EmojiRepo(ctx)
    private val sounds = KeySounds(ctx)
    private val prefs = ctx.getSharedPreferences("kb", Context.MODE_PRIVATE)
    private var tab = 2
    var micActive = false
        set(v) { field = v; invalidate() }
    private var searchEnabled = true
    private var searchScale = 1f
    private var lastTapAt = 0L
    private var tapGap = 220f
    private var query = ""
    private var searchMode = false
    private var scrollPx = 0f
    private val scroller = OverScroller(ctx)
    private var vt: VelocityTracker? = null
    private var stickerFiles: List<File> = emptyList()
    private val thumbs = ConcurrentHashMap<String, Bitmap>()
    private val loading = HashSet<String>()
    private val exec = Executors.newSingleThreadExecutor()

    // animasi fade saran kata
    private var fromSug = arrayOf("", "", "")
    private var curSug = arrayOf("", "", "")
    private var fadeStart = 0L
    private var fadeDur = 120L

    // menu tekan-tahan globe
    private var menuOpen = false
    private var menuHover = -1
    private var menuHoverCell = -1
    private var menuFresh = true

    private val pKey = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
    private val pEmoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pTab = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5.5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val globe: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_globe)
    private val mic: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_mic)

    private val catIds = intArrayOf(R.drawable.emoji_cat_0, R.drawable.emoji_cat_1, R.drawable.emoji_cat_2, R.drawable.emoji_cat_3,
        R.drawable.emoji_cat_4, R.drawable.emoji_cat_5, R.drawable.emoji_cat_6, R.drawable.emoji_cat_7, R.drawable.emoji_cat_8, R.drawable.emoji_cat_9)
    private val catBmps = Array(10) { BitmapFactory.decodeResource(resources, catIds[it]) }
    private val kbEmojiWhite: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_kb_emoji_white)
    private val kbEmojiBlack: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_kb_emoji_black)
    private val addLight: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_add_photo_light)
    private val addDark: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_add_photo_dark)
    private val pTint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // clipboard
    private var clipTab = 0 // 0 teks, 1 gambar, 2 prompt
    private var clipItems: List<ClipStore.Item> = emptyList()
    private val clipThumbs = ConcurrentHashMap<String, Bitmap>()
    private val clipLoading = HashSet<String>()
    private var prevItem: ClipStore.Item? = null
    private var prevT0 = 0L
    private var blurBmp: Bitmap? = null
    private var pvLayout: StaticLayout? = null
    private var pvBitmap: Bitmap? = null
    private var capturing = false
    private val pvCard = RectF(); private val pvPin = RectF(); private val pvDel = RectF()
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val layouts = HashMap<Long, StaticLayout>()

    init {
        applyTheme(false)
        repo.onChange = {
            repo.face?.let { pEmoji.typeface = it; pTab.typeface = it }
            invalidate()
        }
        repo.load()
        if (repo.recents().isNotEmpty()) tab = 1
        loadPrefs()
    }

    // ---------- tema ----------
    private fun applyTheme(isLight: Boolean) {
        light = isLight
        if (!isLight) {
            cBg = 0xFF191919.toInt(); cKey = 0xFF3B3B3B.toInt(); cFunc = 0xFF3B3B3B.toInt(); cFuncDown = 0xFF646464.toInt()
            cPopup = 0xFF6A6A6A.toInt(); cDiv = 0xFF424242.toInt(); cSug = 0xFFB0B0B0.toInt()
            cBlue = 0xFF0464DF.toInt(); cBlueDown = 0xFF3A86E8.toInt(); cHint = 0xFF8E8E93.toInt(); cFg = Color.WHITE
            cShadow = 0; cMenuBg = 0xFF3A3A3C.toInt(); cMenuSel = 0xFF59595D.toInt(); cCell = 0xFF262626.toInt()
            cTabSel = 0xFF4A4A4A.toInt(); cPill = 0xFF3B3B3B.toInt()
        } else {
            cBg = 0xFFD2D4DB.toInt(); cKey = 0xFFFFFFFF.toInt(); cFunc = 0xFFADB2BD.toInt(); cFuncDown = 0xFFFFFFFF.toInt()
            cPopup = 0xFFFFFFFF.toInt(); cDiv = 0xFFB7B9C0.toInt(); cSug = 0xFF6E6E73.toInt()
            cBlue = 0xFF007AFF.toInt(); cBlueDown = 0xFF4DA0FF.toInt(); cHint = 0xFF6E6E73.toInt(); cFg = Color.BLACK
            cShadow = 0x55000000; cMenuBg = 0xFFF1F1F5.toInt(); cMenuSel = 0xFFD6D8DE.toInt(); cCell = 0xFFE4E5EA.toInt()
            cTabSel = 0xFFC3C6CE.toInt(); cPill = 0xFFC4C7CF.toInt()
        }
        pStroke.color = cFg; pFill.color = cFg; pTxt.color = cFg
    }

    fun bgColor(): Int = cBg
    fun isLight(): Boolean = light

    fun loadPrefs() {
        val t = prefs.getInt("theme", 0)
        val sysDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        applyTheme(t == 1 || (t == 2 && !sysDark))
        layouts.clear()
        searchEnabled = prefs.getBoolean("emoji_search", true)
        searchScale = prefs.getInt("search_h", 100) / 100f
        val nhf = prefs.getInt("kb_h", 100) / 100f
        val ndy = prefs.getInt("icon_y", 0).toFloat()
        val nsz = prefs.getInt("icon_sz", 100) / 100f
        oneHand = prefs.getInt("onehand", 0)
        if (nhf != hf || ndy != iconDy || nsz != iconSz) {
            hf = nhf; iconDy = ndy; iconSz = nsz
            for (i in keysCache.indices) keysCache[i] = null
            requestLayout()
        }
        invalidate()
    }

    // ---------- tata letak ----------
    private fun ky(y: Float) = y * hf
    private fun kh() = 120f * hf
    private fun iconCy() = ky(838f) + iconDy

    private fun row(chars: List<String>, x0: Float, pitch: Float, w: Float, y: Float): List<Key> =
        chars.mapIndexed { i, c -> Key(c, c[0].code, x0 + i * pitch, ky(y), w, kh(), K_LETTER) }

    private fun iconKeys(): List<Key> {
        val s = 90f * iconSz
        return listOf(
            Key("globe", GLOBE, 135f - s / 2, iconCy() - s / 2, s, s, K_ICON),
            Key("mic", MIC, 945f - s / 2, iconCy() - s / 2, s, s, K_ICON)
        )
    }

    private fun bottomRow(first: Key, second: Key): List<Key> = listOf(
        first, second,
        Key(" ", SPACE, 298f, ky(628f), 492f, kh(), K_FUNC),
        Key("\n", RET, 808f, ky(628f), 252f, kh(), K_FUNC)
    ) + iconKeys()

    private fun build(p: Int): List<Key> {
        keysCache[p]?.let { return it }
        val k = ArrayList<Key>()
        when (p) {
            0 -> {
                k += row("QWERTYUIOP".map { it.toString() }, 20f, 105.6f, 88f, 182f)
                k += row("ASDFGHJKL".map { it.toString() }, 72f, 105.6f, 88f, 331f)
                k += Key("shift", SHIFT, 20f, ky(479f), 121f, kh(), K_FUNC)
                k += row("ZXCVBNM".map { it.toString() }, 178f, 105.6f, 88f, 479f)
                k += Key("del", DEL, 937f, ky(479f), 123f, kh(), K_FUNC)
                k += bottomRow(Key("123", NUM, 20f, ky(628f), 121f, kh(), K_FUNC), Key("emoji", EMOJI, 160f, ky(628f), 121f, kh(), K_FUNC))
            }
            1, 2 -> {
                val r1 = if (p == 1) "1234567890" else "[]{}#%^*+="
                val r2 = if (p == 1) "-/:;()$&@\"" else "_\\|~<>€£¥•"
                k += row(r1.map { it.toString() }, 20f, 105.6f, 88f, 182f)
                k += row(r2.map { it.toString() }, 20f, 105.6f, 88f, 331f)
                k += Key(if (p == 1) "#+=" else "123", SYM, 20f, ky(479f), 121f, kh(), K_FUNC)
                k += row(listOf(".", ",", "?", "!", "'"), 188f, 144f, 126f, 479f)
                k += Key("del", DEL, 937f, ky(479f), 123f, kh(), K_FUNC)
                k += bottomRow(Key("ABC", ABC, 20f, ky(628f), 121f, kh(), K_FUNC), Key("emoji", EMOJI, 160f, ky(628f), 121f, kh(), K_FUNC))
            }
            else -> {
                k += Key("ABC", ABC, 20f, iconCy() - 45f, 150f, 90f, K_PLAIN)
                if (p == 3) k += Key("del", DEL, 937f, tabCy() - 42f, 123f, 84f, K_PLAIN)
                k += iconKeys().filter { it.code == MIC }
            }
        }
        keysCache[p] = k
        return k
    }

    // geometri panel emoji (mengikuti proporsi emoji iPhone asli)
    private fun tabCy() = ky(687f)
    private fun gridTop() = if (searchEnabled) ky(144f) else ky(80f)
    private fun rowH() = 126f * hf
    private val GX0 = 8f

    private fun keys() = build(page)

    // ---------- isi grid ----------
    private fun isStickerTab() = query.isEmpty() && tab == 0
    private var cachedQ = ""
    private var cachedRes: List<String> = emptyList()
    private fun searchRes(): List<String> {
        if (query != cachedQ) { cachedQ = query; cachedRes = repo.search(query) }
        return cachedRes
    }
    private fun gridItems(): List<String> = when {
        query.isNotEmpty() -> searchRes()
        tab == 0 -> emptyList()
        tab == 1 -> repo.recents()
        else -> repo.byCat[EmojiRepo.CATS[tab - 2]] ?: emptyList()
    }
    private fun gridRows() = if (isStickerTab()) 2 else 4
    private fun cellH() = if (isStickerTab()) rowH() * 2 else rowH()
    private fun cellW() = if (isStickerTab()) cellH() else 145f
    private fun itemCount(): Int = if (isStickerTab()) stickerFiles.size + 1 else gridItems().size
    private fun maxScroll(): Float {
        if (page == 4) {
            val cols = ceil(clipItems.size / clipRows().toFloat())
            return max(0f, cols * (clipCardW() + 14f) * scale - width + 20f * scale)
        }
        val cols = ceil(itemCount() / gridRows().toFloat())
        return max(0f, cols * cellW() * scale - width + 20f * scale)
    }

    fun reloadStickers() {
        stickerFiles = StickerStore.list(context)
        invalidate()
    }

    private fun thumb(f: File): Bitmap? {
        val key = f.path
        thumbs[key]?.let { return it }
        if (loading.add(key)) {
            exec.execute {
                try {
                    val o = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(key, o)?.let { thumbs[key] = it }
                } catch (_: Exception) {}
                postInvalidate()
            }
        }
        return null
    }

    // ---------- ukuran ----------
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        scale = w / DW
        val h = (DH * hf * scale).toInt() + bottomInset
        setMeasuredDimension(w, h)
    }

    override fun onApplyWindowInsets(i: WindowInsets): WindowInsets {
        @Suppress("DEPRECATION")
        val b = i.systemWindowInsetBottom
        if (b != bottomInset) { bottomInset = b; requestLayout() }
        return i
    }

    fun release() { sounds.release(); exec.shutdown() }

    // ---------- API untuk service ----------
    fun setSuggestions(a: String, b: String, c: String, boldMiddle: Boolean) {
        for (i in 0..2) fromSug[i] = curSug[i]
        sug[0] = a; sug[1] = b; sug[2] = c; sugBold = boldMiddle
        for (i in 0..2) curSug[i] = sug[i]
        fadeDur = (tapGap * 0.6f).toLong().coerceIn(70L, 220L)
        fadeStart = SystemClock.uptimeMillis()
        invalidate(0, 0, width, (ky(165f) * scale).toInt())
    }

    private fun fadeT(now: Long): Float = ((now - fadeStart).toFloat() / fadeDur).coerceIn(0f, 1f)

    fun resetPage() { page = 0; searchMode = false; query = ""; menuOpen = false; invalidate() }
    fun setNumberPage() { page = 1; searchMode = false; menuOpen = false; invalidate() }

    // ---------- gambar ----------
    private fun oneOff(): Float = if (oneHand == 1) 0f else width * (1f - ONE_F)

    override fun onDraw(c: Canvas) {
        val s = scale
        pKey.color = cBg
        rf.set(0f, 0f, width.toFloat(), height.toFloat() + 80f)
        c.drawRoundRect(rf, 64f * s, 64f * s, pKey)

        if (oneHand != 0) { c.save(); c.translate(oneOff(), 0f); c.scale(ONE_F, 1f) }
        c.save(); c.scale(s, s)
        if (page == 3) {
            drawEmojiTop(c)
            drawGrid(c, s)
            drawTabs(c)
        } else if (page == 4) {
            drawClipPage(c, s)
        } else if (searchMode) {
            drawSearchTop(c)
        } else drawSuggestions(c)
        for (k in keys()) drawKey(c, k, down.indexOfValue(k) >= 0)
        for (i in 0 until down.size()) {
            val k = down.valueAt(i)
            if (k.kind == K_LETTER && page < 3) drawPopup(c, k)
        }
        if (!capturing) {
            if (menuOpen) drawMenu(c)
            if (prevItem != null) drawPreview(c)
        }
        c.restore()
        if (oneHand != 0) { c.restore(); drawOneHandButton(c) }
    }

    private fun drawOneHandButton(c: Canvas) {
        val stripW = width * (1f - ONE_F)
        val cx = if (oneHand == 1) width - stripW / 2 else stripW / 2
        val cy = height * 0.5f
        pKey.color = cFunc; c.drawCircle(cx, cy, 34f * scale + 8f, pKey)
        pStroke.strokeWidth = 5f * scale + 1f
        val d = if (oneHand == 1) 1 else -1
        c.drawLine(cx - 8f * d * scale, cy - 14f * scale, cx + 8f * d * scale, cy, pStroke)
        c.drawLine(cx + 8f * d * scale, cy, cx - 8f * d * scale, cy + 14f * scale, pStroke)
        pStroke.strokeWidth = 5.5f
    }

    private fun drawMagnifier(c: Canvas, cx: Float, cy: Float) {
        pStroke.color = cHint; pStroke.strokeWidth = 5f
        c.drawCircle(cx - 4f, cy - 4f, 14f, pStroke)
        c.drawLine(cx + 6f, cy + 6f, cx + 17f, cy + 17f, pStroke)
        pStroke.color = cFg; pStroke.strokeWidth = 5.5f
    }

    private fun drawPill(c: Canvas, top: Float, bottom: Float) {
        pKey.color = cPill
        rf.set(20f, top, 1060f, bottom)
        c.drawRoundRect(rf, (bottom - top) / 2, (bottom - top) / 2, pKey)
        val cy = (top + bottom) / 2
        drawMagnifier(c, 64f, cy)
        pTxt.textAlign = Paint.Align.LEFT; pTxt.textSize = 44f
        if (query.isEmpty()) { pTxt.color = cHint; c.drawText("Cari Emoji", 104f, cy + 15f, pTxt) }
        else {
            pTxt.color = cFg
            var t = query; while (pTxt.measureText(t) > 760f && t.length > 1) t = t.drop(1)
            c.drawText(t, 104f, cy + 15f, pTxt)
            if (searchMode) { val x = 104f + pTxt.measureText(t) + 3f; pKey.color = cBlue; c.drawRect(x, cy - 24f, x + 3f, cy + 24f, pKey) }
            pKey.color = cHint; c.drawCircle(1015f, cy, 22f, pKey)
            pStroke.color = cPill; pStroke.strokeWidth = 4f
            c.drawLine(1007f, cy - 8f, 1023f, cy + 8f, pStroke); c.drawLine(1023f, cy - 8f, 1007f, cy + 8f, pStroke)
            pStroke.color = cFg; pStroke.strokeWidth = 5.5f
        }
        pTxt.textAlign = Paint.Align.CENTER; pTxt.color = cFg
    }

    private fun drawEmojiTop(c: Canvas) {
        if (!searchEnabled) return
        val h = 97f * searchScale * hf
        val mid = ky(68.5f)
        drawPill(c, mid - h / 2, mid + h / 2)
    }

    private fun drawSearchTop(c: Canvas) {
        val ph = 74f * searchScale
        drawPill(c, 10f, 10f + ph)
        val res = searchRes()
        pEmoji.textSize = 50f
        val sy = 10f + ph + 10f
        for (i in 0 until min(res.size, 10)) c.drawText(res[i], 20f + i * 104f + 52f, sy + 40f, pEmoji)
    }

    private fun drawGrid(c: Canvas, s: Float) {
        val rows = gridRows(); val cw = cellW(); val ch = cellH(); val top = gridTop()
        c.save(); c.clipRect(0f, top, DW, top + rows * ch)
        val off = scrollPx / s
        val first = max(0, ((off - GX0) / cw).toInt())
        val last = ((off + DW) / cw).toInt() + 1
        if (isStickerTab()) {
            for (col in first..last) for (r in 0 until rows) {
                val idx = col * rows + r
                if (idx > stickerFiles.size) continue
                val x = GX0 + col * cw - off; val y = top + r * ch
                if (idx == 0) {
                    pKey.color = cFunc; rf.set(x + 8f, y + 8f, x + cw - 8f, y + ch - 8f); c.drawRoundRect(rf, 28f, 28f, pKey)
                    val ab = if (light) addLight else addDark
                    pBmp.alpha = 255; pBmp.colorFilter = null
                    rf.set(x + cw / 2 - 70f, y + ch / 2 - 70f, x + cw / 2 + 70f, y + ch / 2 + 70f)
                    c.drawBitmap(ab, null, rf, pBmp)
                } else {
                    pKey.color = cCell; rf.set(x + 8f, y + 8f, x + cw - 8f, y + ch - 8f); c.drawRoundRect(rf, 28f, 28f, pKey)
                    val b = thumb(stickerFiles[idx - 1])
                    if (b != null) { rf.set(x + 18f, y + 18f, x + cw - 18f, y + ch - 18f); c.drawBitmap(b, null, rf, pBmp) }
                }
            }
        } else {
            val items = gridItems()
            pEmoji.textSize = 93f
            for (col in first..last) for (r in 0 until rows) {
                val idx = col * rows + r
                if (idx >= items.size) continue
                c.drawText(items[idx], GX0 + col * cw - off + cw / 2, top + r * ch + ch / 2 + 33f, pEmoji)
            }
            if (items.isEmpty()) {
                pTxt.color = cHint; pTxt.textSize = 40f
                c.drawText(if (query.isNotEmpty()) "Tidak ada hasil" else if (tab == 1) "Belum ada emoji terbaru" else "Memuat emoji…", 540f, top + 200f, pTxt)
                pTxt.color = cFg
            }
        }
        c.restore()
    }

    private fun drawTabs(c: Canvas) {
        val cy = tabCy()
        for (i in 0..9) {
            val cx = 69f + i * 82.5f
            val sel = query.isEmpty() && i == tab
            if (sel) { pKey.color = cTabSel; c.drawCircle(cx, cy, 40f, pKey) }
            // kanvas ikon jam 60px (penuh), ikon lain 120px (berpadding): disamakan ke ukuran iOS asli
            val half = if (i == 1) 22f else 46.8f
            pTint.colorFilter = PorterDuffColorFilter(if (sel) cFg else cHint, PorterDuff.Mode.SRC_IN)
            rf.set(cx - half, cy - half, cx + half, cy + half)
            c.drawBitmap(catBmps[i], null, rf, pTint)
        }
        pTint.colorFilter = null
    }

    private fun drawSuggestions(c: Canvas) {
        val cy = ky(110f)
        pStroke.color = cDiv; pStroke.strokeWidth = 2f
        c.drawLine(363f, cy - 35f, 363f, cy + 35f, pStroke)
        c.drawLine(716f, cy - 35f, 716f, cy + 35f, pStroke)
        pStroke.color = cFg; pStroke.strokeWidth = 5.5f
        pTxt.textSize = 48f
        val xs = floatArrayOf(188f, 540f, 892f)
        val t = fadeT(SystemClock.uptimeMillis())
        for (i in 0..2) {
            if (fromSug[i] == curSug[i] || t >= 1f) drawSugText(c, curSug[i], xs[i], cy, i, 255)
            else {
                drawSugText(c, fromSug[i], xs[i], cy, i, ((1f - t) * 255).toInt())
                drawSugText(c, curSug[i], xs[i], cy, i, (t * 255).toInt())
            }
        }
        if (t < 1f) postInvalidateOnAnimation(0, 0, width, (ky(165f) * scale).toInt())
        pTxt.isFakeBoldText = false
        pTxt.color = cFg; pTxt.alpha = 255
    }

    private fun drawSugText(c: Canvas, t: String, x: Float, cy: Float, i: Int, a: Int) {
        if (t.isEmpty() || a <= 0) return
        pTxt.color = if (i == 1 && sugBold) cFg else cSug
        pTxt.alpha = a.coerceIn(0, 255)
        pTxt.isFakeBoldText = (i == 1 && sugBold)
        var txt = t
        while (pTxt.measureText(txt) > 330f && txt.length > 3) txt = txt.dropLast(1)
        c.drawText(txt, x, cy + 17f, pTxt)
    }

    private fun drawKey(c: Canvas, k: Key, pressed: Boolean) {
        if (k.kind == K_ICON) { drawIcon(c, k); return }
        val cx = k.x + k.w / 2; val cy = k.y + k.h / 2
        if (k.kind == K_PLAIN) {
            pTxt.color = if (pressed) cHint else cFg
            if (k.code == DEL) { pStroke.alpha = if (pressed) 120 else 255; drawDelete(c, cx, cy); pStroke.alpha = 255 }
            else { pTxt.textSize = 46f; c.drawText(k.label, cx, cy + 16f, pTxt) }
            pTxt.color = cFg
            return
        }
        val isRet = k.code == RET
        val letterLook = k.kind == K_LETTER || k.code == SPACE
        val base = when {
            isRet && showReturnArrow -> if (pressed) cBlueDown else cBlue
            letterLook -> if (pressed && k.code == SPACE) cFunc else cKey
            else -> if (pressed) cFuncDown else cFunc
        }
        if (cShadow != 0) {
            pKey.color = cShadow
            rf.set(k.x, k.y + 2.5f, k.x + k.w, k.y + k.h + 2.5f); c.drawRoundRect(rf, 21f, 21f, pKey)
        }
        pKey.color = base
        rf.set(k.x, k.y, k.x + k.w, k.y + k.h)
        c.drawRoundRect(rf, 21f, 21f, pKey)
        pTxt.color = cFg
        when (k.code) {
            SHIFT -> drawShift(c, cx, cy)
            DEL -> drawDelete(c, cx, cy)
            EMOJI -> drawSmiley(c, cx, cy)
            RET -> if (showReturnArrow) drawArrow(c, cx, cy) else { pTxt.textSize = 44f; c.drawText(returnLabel, cx, cy + 15f, pTxt) }
            SPACE -> {}
            NUM, ABC, SYM -> { pTxt.textSize = if (k.label.length > 2) 46f else 50f; c.drawText(k.label, cx, cy + 17f, pTxt) }
            else -> {
                pTxt.textSize = 58f
                val t = if (page == 0 && shift != 0) k.label else if (page == 0) k.label.lowercase() else k.label
                c.drawText(t, cx, cy + 20f, pTxt)
            }
        }
    }

    private fun drawIcon(c: Canvas, k: Key) {
        val cx = k.x + k.w / 2; val cy = k.y + k.h / 2
        pBmp.alpha = if (down.indexOfValue(k) >= 0) 140 else 255
        pBmp.colorFilter = when {
            k.code == MIC && micActive -> PorterDuffColorFilter(cBlueDown, PorterDuff.Mode.SRC_IN)
            light -> PorterDuffColorFilter(cFg, PorterDuff.Mode.SRC_IN)
            else -> null
        }
        if (k.code == GLOBE) {
            val h = 37f * iconSz
            rf.set(cx - h, cy - h, cx + h, cy + h); c.drawBitmap(globe, null, rf, pBmp)
        } else {
            val hw = 24f * iconSz; val hh = 40f * iconSz
            rf.set(cx - hw, cy - hh, cx + hw, cy + hh); c.drawBitmap(mic, null, rf, pBmp)
        }
        pBmp.colorFilter = null
    }

    private fun drawShift(c: Canvas, cx: Float, cy: Float) {
        path.reset()
        path.moveTo(cx, cy - 30f); path.lineTo(cx + 33f, cy + 3f); path.lineTo(cx + 16f, cy + 3f)
        path.lineTo(cx + 16f, cy + 27f); path.lineTo(cx - 16f, cy + 27f); path.lineTo(cx - 16f, cy + 3f)
        path.lineTo(cx - 33f, cy + 3f); path.close()
        if (shift != 0) c.drawPath(path, pFill)
        c.drawPath(path, pStroke)
        if (shift == 2) c.drawLine(cx - 16f, cy + 38f, cx + 16f, cy + 38f, pStroke)
    }

    private fun drawDelete(c: Canvas, cx: Float, cy: Float) {
        path.reset()
        path.moveTo(cx - 40f, cy); path.lineTo(cx - 18f, cy - 28f); path.lineTo(cx + 36f, cy - 28f)
        path.lineTo(cx + 36f, cy + 28f); path.lineTo(cx - 18f, cy + 28f); path.close()
        c.drawPath(path, pStroke)
        c.drawLine(cx - 2f, cy - 11f, cx + 20f, cy + 11f, pStroke)
        c.drawLine(cx + 20f, cy - 11f, cx - 2f, cy + 11f, pStroke)
    }

    private fun drawArrow(c: Canvas, cx: Float, cy: Float) {
        pStroke.color = Color.WHITE
        c.drawLine(cx - 30f, cy, cx + 30f, cy, pStroke)
        c.drawLine(cx + 30f, cy, cx + 10f, cy - 20f, pStroke)
        c.drawLine(cx + 30f, cy, cx + 10f, cy + 20f, pStroke)
        pStroke.color = cFg
    }

    private fun drawSmiley(c: Canvas, cx: Float, cy: Float) {
        rf.set(cx - 36f, cy - 36f, cx + 36f, cy + 36f)
        pBmp.alpha = 255; pBmp.colorFilter = null
        c.drawBitmap(if (light) kbEmojiBlack else kbEmojiWhite, null, rf, pBmp)
    }

    private fun drawPopup(c: Canvas, k: Key) {
        val bw = k.w * 1.5f; val bh = k.h * 1.25f
        var left = k.x + k.w / 2 - bw / 2
        left = left.coerceIn(6f, DW - bw - 6f)
        val top = k.y - bh + k.h * 0.25f
        if (cShadow != 0) { pKey.color = cShadow; rf.set(left, top + 3f, left + bw, top + bh + 3f); c.drawRoundRect(rf, 30f, 30f, pKey) }
        pKey.color = cPopup
        rf.set(left, top, left + bw, top + bh)
        c.drawRoundRect(rf, 30f, 30f, pKey)
        rf.set(k.x, top + bh - 40f, k.x + k.w, k.y + k.h)
        c.drawRoundRect(rf, 21f, 21f, pKey)
        pTxt.color = cFg; pTxt.textSize = 86f
        val t = if (page == 0 && shift == 0) k.label.lowercase() else k.label
        c.drawText(t, left + bw / 2, top + bh * 0.55f + 28f, pTxt)
    }

    // ---------- menu tekan-tahan globe (seperti iOS) ----------
    private fun menuRow(): Float = min(116f, (menuBottom() - 16f - 28f) / 6f)
    private val MENU_L = 16f
    private val MENU_W = 600f
    private fun menuBottom() = iconCy() - 55f * iconSz
    private fun menuTop() = menuBottom() - (6 * menuRow() + 28f)
    private fun menuLangIdx() = prefs.getInt("lang", 0)

    private fun drawMenu(c: Canvas) {
        val top = menuTop(); val mr = menuRow()
        if (cShadow != 0) { pKey.color = cShadow; rf.set(MENU_L, top + 4f, MENU_L + MENU_W, menuBottom() + 4f); c.drawRoundRect(rf, 56f, 56f, pKey) }
        pKey.color = cMenuBg
        rf.set(MENU_L, top, MENU_L + MENU_W, menuBottom()); c.drawRoundRect(rf, 56f, 56f, pKey)
        val labels = arrayOf("Pengaturan Keyboard...", "Indonesia", "English (US)", "Emoji", "Clipboard")
        pTxt.textAlign = Paint.Align.LEFT; pTxt.textSize = 44f
        for (i in 0..4) {
            val y0 = top + 14f + i * mr
            val sel = (i == 1 && menuLangIdx() == 0) || (i == 2 && menuLangIdx() == 1)
            if (sel || menuHover == i) {
                pKey.color = cMenuSel; rf.set(MENU_L + 12f, y0 + 6f, MENU_L + MENU_W - 12f, y0 + mr - 6f)
                c.drawRoundRect(rf, (mr - 12f) / 2, (mr - 12f) / 2, pKey)
            }
            pTxt.color = cFg
            c.drawText(labels[i], MENU_L + 46f, y0 + mr / 2 + 15f, pTxt)
        }
        pTxt.textAlign = Paint.Align.CENTER
        // baris ikon mode satu tangan: kiri, normal, kanan
        val y0 = top + 14f + 5 * mr
        val cw = MENU_W / 3
        for (i in 0..2) {
            val mode = when (i) { 0 -> 1; 1 -> 0; else -> 2 }
            val sel = oneHand == mode
            val cx = MENU_L + cw * i + cw / 2; val cy = y0 + mr / 2
            if (sel || (menuHover == 5 && menuHoverCell == i)) {
                pKey.color = cMenuSel; rf.set(cx - 62f, cy - 46f, cx + 62f, cy + 46f); c.drawRoundRect(rf, 46f, 46f, pKey)
            }
            drawKbIcon(c, cx, cy, i)
        }
    }

    private fun drawKbIcon(c: Canvas, cx: Float, cy: Float, i: Int) {
        pStroke.strokeWidth = 4.5f
        rf.set(cx - 36f, cy - 22f, cx + 36f, cy + 22f); c.drawRoundRect(rf, 8f, 8f, pStroke)
        for (r in 0..1) for (q in 0..4) c.drawPoint(cx - 24f + q * 12f, cy - 9f + r * 11f, pStroke)
        c.drawLine(cx - 18f, cy + 12f, cx + 18f, cy + 12f, pStroke)
        if (i != 1) {
            // segitiga kecil menunjukkan arah keyboard bergeser
            val d = if (i == 0) -1f else 1f
            val ax = cx + d * 50f
            path.reset(); path.moveTo(ax + d * 12f, cy); path.lineTo(ax - d * 6f, cy - 11f); path.lineTo(ax - d * 6f, cy + 11f); path.close()
            c.drawPath(path, pFill)
        }
        pStroke.strokeWidth = 5.5f
    }

    private fun menuHit(x: Float, y: Float): Int {
        if (x < MENU_L || x > MENU_L + MENU_W) return -1
        val r = ((y - menuTop() - 14f) / menuRow()).toInt()
        return if (y >= menuTop() + 14f && r in 0..5) r else -1
    }

    private fun menuAct(row: Int, x: Float) {
        when (row) {
            0 -> l.onOpenSettings()
            1 -> { prefs.edit().putInt("lang", 0).apply(); l.onLanguage(0) }
            2 -> { prefs.edit().putInt("lang", 1).apply(); l.onLanguage(1) }
            3 -> { page = 3; searchMode = false; scrollPx = 0f; if (query.isEmpty() && tab == 0) reloadStickers() }
            4 -> { page = 4; searchMode = false; scrollPx = 0f; scroller.forceFinished(true); reloadClip() }
            5 -> {
                val i = ((x - MENU_L) / (MENU_W / 3)).toInt().coerceIn(0, 2)
                oneHand = when (i) { 0 -> 1; 1 -> 0; else -> 2 }
                prefs.edit().putInt("onehand", oneHand).apply()
            }
        }
        invalidate()
    }

    private fun closeMenu() { menuOpen = false; menuHover = -1; menuHoverCell = -1; invalidate() }

    private fun handleMenuTouch(e: MotionEvent): Boolean {
        val idx = e.actionIndex
        val x = e.getX(idx) / scale; val y = e.getY(idx) / scale
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                menuHover = menuHit(x, y)
                menuHoverCell = if (menuHover == 5) ((x - MENU_L) / (MENU_W / 3)).toInt().coerceIn(0, 2) else -1
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val h = menuHover
                if (h >= 0) { menuAct(h, x); closeMenu() }
                else if (menuFresh) { menuFresh = false; menuHover = -1; invalidate() }
                else closeMenu()
            }
            MotionEvent.ACTION_CANCEL -> closeMenu()
        }
        return true
    }

    // ---------- sentuhan ----------
    private val down = SparseArray<Key>()
    private var lastShiftTap = 0L
    private var repeating: Key? = null
    private var cursorMode = false
    private var cursorLastX = 0f
    private val repeatRunnable = object : Runnable {
        var n = 0
        override fun run() {
            if (repeating?.code == DEL) {
                dispatchDelete(); n++
                handler.postDelayed(this, if (n > 12) 30L else 55L)
            }
        }
    }
    private val spaceLong = Runnable {
        cursorMode = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    private val globeLong = Runnable {
        down.clear()
        menuOpen = true; menuFresh = true; menuHover = -1; menuHoverCell = -1
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    // grid
    private var gridId = -1
    private var gridX0 = 0f
    private var gridY0 = 0f
    private var gridScroll0 = 0f
    private var gridMoved = false
    private val gridLong = Runnable {
        if (gridId >= 0 && !gridMoved && isStickerTab()) {
            val idx = cellAt(gridX0, gridY0)
            if (idx in 1..stickerFiles.size) {
                val f = stickerFiles[idx - 1]
                f.delete(); thumbs.remove(f.path); loading.remove(f.path)
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                Toast.makeText(context, "Stiker dihapus", Toast.LENGTH_SHORT).show()
                gridId = -1; reloadStickers()
            }
        }
    }

    private fun cellAt(px: Float, py: Float): Int {
        val s = scale; val x = px / s; val y = py / s
        val col = ((x - GX0 + scrollPx / s) / cellW()).toInt()
        val r = ((y - gridTop()) / cellH()).toInt()
        if (x < GX0 || y < gridTop() || r !in 0 until gridRows() || col < 0) return -1
        return col * gridRows() + r
    }

    private fun dispatchDelete() {
        if (searchMode) { if (query.isNotEmpty()) { query = query.dropLast(1); invalidate() } } else l.onDelete()
    }

    private fun find(px: Float, py: Float): Key? {
        val x = px / scale; val y = py / scale
        var best: Key? = null; var bd = Float.MAX_VALUE
        val useProb = page == 0 && !searchMode && probs.isNotEmpty()
        for (k in keys()) {
            if (k.kind == K_ICON) {
                if (x >= k.x - 20 && x <= k.x + k.w + 20 && y >= k.y - 10 && y <= k.y + k.h + 30) return k
                continue
            }
            val cx = k.x + k.w / 2; val cy = k.y + k.h / 2
            val gx = max(abs(x - cx) - k.w / 2, 0f); val gy = max(abs(y - cy) - k.h / 2, 0f)
            if (gx > 32f || gy > 32f) continue
            val nx = (x - cx) / k.w; val ny = (y - cy) / k.h
            var d = nx * nx + ny * ny
            if (useProb && k.kind == K_LETTER) {
                val p = probs[k.label[0].lowercaseChar()] ?: 0f
                d /= (1f + 3.5f * p)
            }
            if (d < bd) { bd = d; best = k }
        }
        return best
    }

    private fun tap(k: Int = KeySounds.STD) {
        val now = SystemClock.uptimeMillis()
        if (lastTapAt != 0L) { val g = (now - lastTapAt).coerceAtMost(600L).toFloat(); tapGap = tapGap * 0.7f + g * 0.3f }
        lastTapAt = now
        if (prefs.getBoolean("haptic", true)) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        sounds.play(k)
    }

    private fun soundFor(k: Key) = when (k.code) { DEL -> KeySounds.DEL; SPACE -> KeySounds.SPACE; RET -> KeySounds.RET; else -> KeySounds.STD }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollPx = scroller.currX.toFloat().coerceIn(0f, maxScroll())
            postInvalidateOnAnimation()
        }
    }

    private fun selectTab(i: Int) {
        tab = i; query = ""; scrollPx = 0f; scroller.forceFinished(true)
        if (i == 0) reloadStickers()
        invalidate()
    }

    private fun enterSearch() {
        page = 0; searchMode = true; shift = 0; scroller.forceFinished(true); invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (oneHand == 0) return touch(e)
        val off = oneOff(); val w = width * ONE_F
        val a = e.actionMasked
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            val x = e.getX(e.actionIndex)
            if (x < off || x > off + w) {
                oneHand = 0; prefs.edit().putInt("onehand", 0).apply(); invalidate(); return true
            }
        }
        val ev = MotionEvent.obtain(e)
        ev.offsetLocation(-off, 0f)
        val m = Matrix(); m.setScale(1f / ONE_F, 1f)
        ev.transform(m)
        val r = touch(ev)
        ev.recycle()
        return r
    }

    private fun touch(e: MotionEvent): Boolean {
        val idx = e.actionIndex
        val id = e.getPointerId(idx)
        val s = scale

        if (menuOpen) return handleMenuTouch(e)
        if (prevItem != null) return handlePreviewTouch(e)
        if (page == 4 && touchClip(e)) return true

        // ---- grid emoji/stiker (satu jari) ----
        if (page == 3) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val x = e.getX(idx); val y = e.getY(idx)
                    val gTop = gridTop() * s; val gBot = (gridTop() + gridRows() * cellH()) * s
                    if (y < gTop && y < ky(165f) * s) {
                        if (searchEnabled) { tap(); enterSearch() }
                        return true
                    }
                    if (y >= gTop && y < gBot && gridId < 0) {
                        gridId = id; gridX0 = x; gridY0 = y; gridScroll0 = scrollPx; gridMoved = false
                        scroller.forceFinished(true)
                        vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(e)
                        handler.postDelayed(gridLong, 650)
                        return true
                    }
                    if (abs(y - tabCy() * s) < 46f * s && x < 935f * s) {
                        val i = ((x / s - 28f) / 82.5f).toInt()
                        if (i in TAB_ICONS.indices && x / s >= 28f) { tap(); selectTab(i) }
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> if (gridId >= 0) {
                    val pi = e.findPointerIndex(gridId)
                    if (pi >= 0) {
                        vt?.addMovement(e)
                        val dx = e.getX(pi) - gridX0
                        if (abs(dx) > 14f * s) { gridMoved = true; handler.removeCallbacks(gridLong) }
                        if (gridMoved) { scrollPx = (gridScroll0 - dx).coerceIn(0f, maxScroll()); invalidate() }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if (id == gridId) {
                    handler.removeCallbacks(gridLong)
                    vt?.addMovement(e)
                    if (gridMoved) {
                        vt?.computeCurrentVelocity(1000)
                        val vx = vt?.xVelocity ?: 0f
                        scroller.fling(scrollPx.toInt(), 0, (-vx).toInt(), 0, 0, maxScroll().toInt(), 0, 0)
                        postInvalidateOnAnimation()
                    } else {
                        val i = cellAt(e.getX(idx), e.getY(idx))
                        if (i >= 0) pickCell(i)
                    }
                    vt?.recycle(); vt = null; gridId = -1
                    return true
                }
                MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(gridLong); gridId = -1 }
            }
        }

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = e.getX(idx); val y = e.getY(idx)
                if (page < 3 && y < ky(165f) * s) {
                    if (searchMode) {
                        if (y < (20f + 74f * searchScale) * s) { if (x > 960f * s && query.isNotEmpty()) { query = ""; invalidate() } }
                        else {
                            val res = searchRes()
                            val i = ((x / s - 20f) / 104f).toInt()
                            if (i in 0 until min(res.size, 10)) { tap(); insertEmoji(res[i]) }
                        }
                    } else {
                        val slot = (x / (width / 3f)).toInt().coerceIn(0, 2)
                        if (sug[slot].isNotEmpty()) { tap(); l.onSuggestion(slot) }
                    }
                    return true
                }
                val k = find(x, y) ?: return true
                down.put(id, k)
                tap(soundFor(k))
                when (k.code) {
                    DEL -> { repeating = k; dispatchDelete(); repeatRunnable.n = 0; handler.postDelayed(repeatRunnable, 380) }
                    SPACE -> { cursorMode = false; cursorLastX = x; if (!searchMode) handler.postDelayed(spaceLong, 420) }
                    GLOBE -> handler.postDelayed(globeLong, 450)
                }
                invalidateKey(k)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val pid = e.getPointerId(i)
                    val old = down.get(pid) ?: continue
                    val x = e.getX(i); val y = e.getY(i)
                    if (cursorMode && old.code == SPACE) {
                        val step = 28f * s
                        while (x - cursorLastX > step) { l.onCursorMove(1); cursorLastX += step }
                        while (cursorLastX - x > step) { l.onCursorMove(-1); cursorLastX -= step }
                        continue
                    }
                    if (page >= 3 || old.code == DEL || old.code == SPACE || old.code == GLOBE) continue
                    val nk = find(x, y)
                    if (nk != null && nk !== old) { down.put(pid, nk); invalidateKey(old); invalidateKey(nk) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val k = down.get(id)
                if (k != null) {
                    down.remove(id)
                    handler.removeCallbacks(repeatRunnable); handler.removeCallbacks(spaceLong); handler.removeCallbacks(globeLong)
                    repeating = null
                    if (!cursorMode || k.code != SPACE) commit(k)
                    cursorMode = false
                    invalidateKey(k)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(repeatRunnable); handler.removeCallbacks(spaceLong); handler.removeCallbacks(globeLong)
                repeating = null; down.clear(); cursorMode = false; invalidate()
            }
        }
        return true
    }

    // =====================================================================
    // Clipboard (teks biasa, gambar, teks panjang) + pratinjau tekan-tahan
    // =====================================================================
    private val srcR = Rect()
    private val clipPath = Path()

    fun reloadClip() {
        clipItems = ClipStore.list(context, clipTab)
        layouts.clear()
        invalidate()
    }

    private fun clipTop() = ky(128f)
    private fun clipBottom() = ky(780f)
    private fun clipRows() = if (clipTab == 2) 2 else 3
    private fun clipCardH(): Float { val r = clipRows(); return (clipBottom() - clipTop() - (r - 1) * 12f) / r }
    private fun clipCardW(): Float = when (clipTab) { 0 -> 340f; 1 -> clipCardH(); else -> 520f }

    private fun clipThumb(it: ClipStore.Item): Bitmap? {
        val f = ClipStore.imageFile(context, it); val key = f.path
        clipThumbs[key]?.let { b -> return b }
        if (clipLoading.add(key)) {
            exec.execute {
                try {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(key, o)
                    var ss = 1
                    while (o.outWidth / ss > 700 || o.outHeight / ss > 700) ss *= 2
                    BitmapFactory.decodeFile(key, BitmapFactory.Options().apply { inSampleSize = ss })?.let { b -> clipThumbs[key] = b }
                } catch (_: Exception) {}
                postInvalidate()
            }
        }
        return null
    }

    private fun textLayout(it: ClipStore.Item, w: Float, h: Float): StaticLayout {
        layouts[it.id]?.let { l0 -> return l0 }
        val size = if (it.type == ClipStore.T_TEXT) 38f else 31f
        val p2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; color = cFg }
        val maxLines = ((h - 40f) / (size * 1.28f)).toInt().coerceAtLeast(1)
        val lay = StaticLayout.Builder.obtain(it.value, 0, it.value.length, p2, (w - 44f).toInt())
            .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, 1.05f)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        layouts[it.id] = lay
        return lay
    }

    private fun drawPinBadge(c: Canvas, cx: Float, cy: Float) {
        pKey.color = cBlue; c.drawCircle(cx, cy, 17f, pKey)
        pFill.color = Color.WHITE; pStroke.color = Color.WHITE; pStroke.strokeWidth = 3.4f
        c.drawCircle(cx, cy - 4f, 5f, pFill)
        c.drawLine(cx, cy, cx, cy + 9f, pStroke)
        pFill.color = cFg; pStroke.color = cFg; pStroke.strokeWidth = 5.5f
    }

    private fun drawClipCard(c: Canvas, it: ClipStore.Item, x: Float, y: Float, w: Float, h: Float) {
        if (cShadow != 0) { pKey.color = cShadow; rf.set(x, y + 3f, x + w, y + h + 3f); c.drawRoundRect(rf, 30f, 30f, pKey) }
        pKey.color = cKey; rf.set(x, y, x + w, y + h); c.drawRoundRect(rf, 30f, 30f, pKey)
        if (it.type == ClipStore.T_IMAGE) {
            val b = clipThumb(it)
            if (b != null) {
                val bw = b.width.toFloat(); val bh = b.height.toFloat()
                val k = max(w / bw, h / bh)
                val sw = (w / k).toInt(); val sh = (h / k).toInt()
                val sx = ((bw - sw) / 2).toInt(); val sy = ((bh - sh) / 2).toInt()
                srcR.set(sx, sy, sx + sw, sy + sh)
                clipPath.reset(); clipPath.addRoundRect(x, y, x + w, y + h, 30f, 30f, Path.Direction.CW)
                c.save(); c.clipPath(clipPath)
                rf.set(x, y, x + w, y + h); c.drawBitmap(b, srcR, rf, pBmp)
                c.restore()
            }
        } else {
            val lay = textLayout(it, w, h)
            c.save(); c.translate(x + 22f, y + 20f); lay.draw(c); c.restore()
        }
        if (it.pinned) drawPinBadge(c, x + w - 28f, y + 28f)
    }

    private fun drawClipPage(c: Canvas, s: Float) {
        val top = ky(30f); val h = 70f * hf; val sw = 1040f / 3
        pKey.color = cPill; rf.set(20f, top, 1060f, top + h); c.drawRoundRect(rf, h / 2, h / 2, pKey)
        pKey.color = if (light) 0xFFFFFFFF.toInt() else cMenuSel
        rf.set(20f + clipTab * sw + 5f, top + 5f, 20f + (clipTab + 1) * sw - 5f, top + h - 5f)
        c.drawRoundRect(rf, (h - 10f) / 2, (h - 10f) / 2, pKey)
        val labels = arrayOf("Teks", "Gambar", "Prompt")
        pTxt.textSize = 38f
        for (i in 0..2) { pTxt.color = if (i == clipTab) cFg else cHint; c.drawText(labels[i], 20f + i * sw + sw / 2, top + h / 2 + 13f, pTxt) }
        pTxt.color = cFg

        val rows = clipRows(); val cw = clipCardW(); val ch = clipCardH(); val tp0 = clipTop()
        c.save(); c.clipRect(0f, tp0 - 4f, DW, clipBottom() + 6f)
        val off = scrollPx / s
        if (clipItems.isEmpty()) {
            pTxt.color = cHint; pTxt.textSize = 38f
            val msg = when (clipTab) { 0 -> "Belum ada teks yang disalin"; 1 -> "Belum ada gambar yang disalin"; else -> "Belum ada teks panjang yang disalin" }
            c.drawText(msg, 540f, tp0 + 250f, pTxt); pTxt.color = cFg
        }
        val first = max(0, ((off - 20f) / (cw + 14f)).toInt()); val last = ((off + DW) / (cw + 14f)).toInt() + 1
        for (col in first..last) for (r in 0 until rows) {
            val idx = col * rows + r
            if (idx >= clipItems.size) continue
            drawClipCard(c, clipItems[idx], 20f + col * (cw + 14f) - off, tp0 + r * (ch + 12f), cw, ch)
        }
        c.restore()
    }

    private fun clipCellAt(px: Float, py: Float): Int {
        val s = scale; val x = px / s; val y = py / s
        val cw = clipCardW(); val ch = clipCardH()
        val fx = x - 20f + scrollPx / s; val fy = y - clipTop()
        if (fx < 0f || fy < 0f) return -1
        val col = (fx / (cw + 14f)).toInt(); val row = (fy / (ch + 12f)).toInt()
        if (fx - col * (cw + 14f) > cw || fy - row * (ch + 12f) > ch || row >= clipRows()) return -1
        val idx = col * clipRows() + row
        return if (idx < clipItems.size) idx else -1
    }

    private var clipId = -1
    private var clipX0 = 0f
    private var clipY0 = 0f
    private var clipScroll0 = 0f
    private var clipMoved = false
    private val clipLong = Runnable {
        if (clipId >= 0 && !clipMoved) {
            val i = clipCellAt(clipX0, clipY0)
            if (i >= 0) { clipId = -1; openPreview(clipItems[i]) }
        }
    }

    private fun touchClip(e: MotionEvent): Boolean {
        val idx = e.actionIndex; val id = e.getPointerId(idx); val s = scale
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = e.getX(idx); val y = e.getY(idx)
                if (y < (clipTop() - 6f) * s) {
                    val i = ((x / s - 20f) / (1040f / 3)).toInt().coerceIn(0, 2)
                    tap(); clipTab = i; scrollPx = 0f; scroller.forceFinished(true); reloadClip(); return true
                }
                if (y >= clipTop() * s && y <= clipBottom() * s && clipId < 0) {
                    clipId = id; clipX0 = x; clipY0 = y; clipScroll0 = scrollPx; clipMoved = false
                    scroller.forceFinished(true)
                    vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(e)
                    handler.postDelayed(clipLong, 480)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> if (clipId >= 0) {
                val pi = e.findPointerIndex(clipId)
                if (pi >= 0) {
                    vt?.addMovement(e)
                    val dx = e.getX(pi) - clipX0
                    if (abs(dx) > 14f * s) { clipMoved = true; handler.removeCallbacks(clipLong) }
                    if (clipMoved) { scrollPx = (clipScroll0 - dx).coerceIn(0f, maxScroll()); invalidate() }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if (id == clipId) {
                handler.removeCallbacks(clipLong)
                vt?.addMovement(e)
                if (clipMoved) {
                    vt?.computeCurrentVelocity(1000)
                    scroller.fling(scrollPx.toInt(), 0, (-(vt?.xVelocity ?: 0f)).toInt(), 0, 0, maxScroll().toInt(), 0, 0)
                    postInvalidateOnAnimation()
                } else {
                    val i = clipCellAt(e.getX(idx), e.getY(idx))
                    if (i >= 0) {
                        tap()
                        val it = clipItems[i]
                        if (it.type == ClipStore.T_IMAGE) l.onClipImage(ClipStore.imageFile(context, it)) else l.onClipText(it.value)
                    }
                }
                vt?.recycle(); vt = null; clipId = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(clipLong); clipId = -1 }
        }
        return false
    }

    // ---- pratinjau (popup sedang + latar blur) ----
    private fun captureBlur(): Bitmap? = try {
        val k = 8
        val bmp = Bitmap.createBitmap(max(1, width / k), max(1, height / k), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp); cv.scale(1f / k, 1f / k)
        capturing = true; draw(cv); capturing = false
        bmp
    } catch (_: Throwable) { capturing = false; null }

    private fun openPreview(it: ClipStore.Item) {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        blurBmp = captureBlur()
        pvLayout = null; pvBitmap = null
        val hd = height / scale
        val cardW: Float; val cardH: Float
        if (it.type == ClipStore.T_IMAGE) {
            val f = ClipStore.imageFile(context, it)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            var ss = 1; while (o.outWidth / ss > 1000 || o.outHeight / ss > 1000) ss *= 2
            pvBitmap = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = ss })
            val b = pvBitmap
            val bw = (b?.width ?: 400).toFloat(); val bh = (b?.height ?: 300).toFloat()
            val k = min(600f / bw, 400f / bh)
            cardW = bw * k + 28f; cardH = bh * k + 28f
        } else {
            val size = if (it.type == ClipStore.T_TEXT) 44f else 36f
            val p2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; color = cFg }
            val maxLines = if (it.type == ClipStore.T_TEXT) 6 else 9
            val lay = StaticLayout.Builder.obtain(it.value, 0, it.value.length, p2, 680)
                .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, 1.08f).build()
            pvLayout = lay
            cardW = 740f; cardH = min(lay.height + 64f, 520f)
        }
        val block = cardH + 24f + 208f
        val top = max(24f, (hd - block) / 2 - 10f)
        pvCard.set(540f - cardW / 2, top, 540f + cardW / 2, top + cardH)
        pvPin.set(540f - 230f, pvCard.bottom + 24f, 540f + 230f, pvCard.bottom + 24f + 104f)
        pvDel.set(540f - 230f, pvPin.bottom, 540f + 230f, pvPin.bottom + 104f)
        prevItem = it; prevT0 = SystemClock.uptimeMillis()
        invalidate()
    }

    private fun closePreview() {
        prevItem = null; blurBmp = null; pvLayout = null; pvBitmap = null
        reloadClip()
    }

    private fun drawPreview(c: Canvas) {
        val it = prevItem ?: return
        val hd = height / scale
        val t0 = ((SystemClock.uptimeMillis() - prevT0) / 190f).coerceIn(0f, 1f)
        val e = t0 * t0 * (3f - 2f * t0)
        blurBmp?.let { b -> rf.set(0f, 0f, DW, hd); pBmp.alpha = (255 * e).toInt(); c.drawBitmap(b, null, rf, pBmp); pBmp.alpha = 255 }
        pKey.color = if (light) Color.argb((110 * e).toInt(), 255, 255, 255) else Color.argb((150 * e).toInt(), 0, 0, 0)
        c.drawRect(0f, 0f, DW, hd, pKey)

        val midY = (pvCard.top + pvDel.bottom) / 2
        c.saveLayerAlpha(0f, 0f, DW, hd, (255 * e).toInt())
        val sc = 0.92f + 0.08f * e
        c.scale(sc, sc, 540f, midY)
        // kartu isi
        if (cShadow != 0) { pKey.color = 0x40000000; rf.set(pvCard.left, pvCard.top + 6f, pvCard.right, pvCard.bottom + 6f); c.drawRoundRect(rf, 44f, 44f, pKey) }
        pKey.color = cMenuBg; c.drawRoundRect(pvCard, 44f, 44f, pKey)
        if (it.type == ClipStore.T_IMAGE) {
            pvBitmap?.let { b ->
                rf.set(pvCard.left + 14f, pvCard.top + 14f, pvCard.right - 14f, pvCard.bottom - 14f)
                clipPath.reset(); clipPath.addRoundRect(rf, 32f, 32f, Path.Direction.CW)
                c.save(); c.clipPath(clipPath); c.drawBitmap(b, null, rf, pBmp); c.restore()
            }
        } else {
            pvLayout?.let { lay -> c.save(); c.translate(pvCard.left + 30f, pvCard.top + 32f); lay.draw(c); c.restore() }
        }
        // menu aksi
        rf.set(pvPin.left, pvPin.top, pvPin.right, pvDel.bottom)
        pKey.color = cMenuBg; c.drawRoundRect(rf, 44f, 44f, pKey)
        pKey.color = cDiv; c.drawRect(pvPin.left + 30f, pvPin.bottom - 1f, pvPin.right - 30f, pvPin.bottom + 1f, pKey)
        pTxt.textAlign = Paint.Align.LEFT; pTxt.textSize = 42f
        pTxt.color = cFg
        c.drawText(if (it.pinned) "Lepas sematan" else "Sematkan", pvPin.left + 44f, pvPin.centerY() + 15f, pTxt)
        val red = if (light) 0xFFFF3B30.toInt() else 0xFFFF453A.toInt()
        pTxt.color = red
        c.drawText("Hapus", pvDel.left + 44f, pvDel.centerY() + 15f, pTxt)
        pTxt.textAlign = Paint.Align.CENTER; pTxt.color = cFg
        // ikon pin & tempat sampah
        val px = pvPin.right - 62f; val py = pvPin.centerY()
        pStroke.strokeWidth = 4f; pFill.color = cFg
        c.drawCircle(px, py - 7f, 8f, pFill); c.drawLine(px, py - 1f, px, py + 14f, pStroke)
        val tx = pvDel.right - 62f; val ty = pvDel.centerY()
        pStroke.color = red
        c.drawLine(tx - 13f, ty - 8f, tx + 13f, ty - 8f, pStroke); c.drawLine(tx - 5f, ty - 13f, tx + 5f, ty - 13f, pStroke)
        rf.set(tx - 10f, ty - 5f, tx + 10f, ty + 15f); c.drawRoundRect(rf, 4f, 4f, pStroke)
        pStroke.color = cFg; pStroke.strokeWidth = 5.5f
        c.restore()
        if (t0 < 1f) postInvalidateOnAnimation()
    }

    private fun handlePreviewTouch(e: MotionEvent): Boolean {
        val it = prevItem ?: return true
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            val idx = e.actionIndex
            val x = e.getX(idx) / scale; val y = e.getY(idx) / scale
            when {
                pvPin.contains(x, y) -> { ClipStore.togglePin(context, it.id); tap(); closePreview() }
                pvDel.contains(x, y) -> { ClipStore.delete(context, it.id); tap(); closePreview() }
                pvCard.contains(x, y) -> {}
                else -> closePreview()
            }
        }
        return true
    }

    private fun pickCell(i: Int) {
        if (isStickerTab()) {
            if (i == 0) l.onCreateSticker()
            else if (i - 1 < stickerFiles.size) { tap(); l.onSticker(stickerFiles[i - 1]) }
        } else {
            val items = gridItems()
            if (i < items.size) { tap(); insertEmoji(items[i]) }
        }
    }

    private fun insertEmoji(e: String) {
        repo.addRecent(e)
        l.onText(e)
    }

    private fun invalidateKey(k: Key) {
        val s = scale
        invalidate(((k.x - 140f) * s).toInt().coerceAtLeast(0), ((k.y - 220f) * s).toInt().coerceAtLeast(0),
            ((k.x + k.w + 140f) * s).toInt(), ((k.y + k.h + 20f) * s).toInt())
    }

    private fun commit(k: Key) {
        when (k.code) {
            DEL -> {}
            SHIFT -> {
                val now = System.currentTimeMillis()
                shift = if (shift == 2) 0 else if (shift == 1) (if (now - lastShiftTap < 350) 2 else 0) else (if (now - lastShiftTap < 350) 2 else 1)
                lastShiftTap = now
                l.onShiftChange(shift)
            }
            NUM -> { page = 1; invalidate() }
            ABC -> { page = 0; searchMode = false; invalidate() }
            SYM -> { page = if (page == 1) 2 else 1; invalidate() }
            EMOJI -> {
                if (page == 3) { page = 0 } else {
                    page = 3; searchMode = false; scrollPx = 0f
                    if (query.isEmpty() && tab == 0) reloadStickers()
                }
                invalidate()
            }
            SPACE -> if (searchMode) { query += " "; invalidate() } else l.onSpace()
            RET -> if (searchMode) { searchMode = false; page = 3; scrollPx = 0f; invalidate() } else l.onReturn()
            GLOBE -> l.onGlobe(false)
            MIC -> l.onMic()
            else -> {
                val t = if (page == 0 && shift == 0) k.label.lowercase() else k.label
                if (searchMode) { query += t.lowercase(); invalidate() } else l.onText(t)
                if (page == 0 && shift == 1) { shift = 0; l.onShiftChange(0) }
            }
        }
    }
}

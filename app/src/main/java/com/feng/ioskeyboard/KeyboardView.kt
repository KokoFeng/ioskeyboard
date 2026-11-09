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
        fun onCombine(a: String, b: String)
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
    private var leftEmoji = false
    private var langFlash = ""
    private var langFlashAt = 0L
    private val keysCache = HashMap<Int, List<Key>>()
    private var langIdx = 0
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
    // emoji gabungan, zoom, nama
    private val zoomer = EmojiZoom(ctx)
    private var zoomActive = false
    private var zoomPid = -1
    private var zoomItem: String? = null
    private var accentActive = false
    private var accentKey: Key? = null
    private var accentPid = -1
    private var accentList: List<String> = emptyList()
    private var accentHover = -1
    private val accentRect = RectF()
    private val accentRunnable = Runnable { startAccent() }
    private val ALT_LATIN = mapOf("a" to "à á â ä æ ã å ā", "c" to "ç ć č", "e" to "è é ê ë ē ė ę", "i" to "î ï í ī į ì", "l" to "ł",
        "n" to "ñ ń", "o" to "ô ö ò ó œ ø ō õ", "s" to "ß ś š", "u" to "û ü ù ú ū", "y" to "ÿ ý", "z" to "ž ź ż")
    private val ALT_ES = mapOf("a" to "á à ä â", "e" to "é è ë ê", "i" to "í ì ï î", "o" to "ó ò ö ô", "u" to "ú ü ù û", "n" to "ñ", "c" to "ç", "?" to "¿", "!" to "¡")
    private val ALT_RU = mapOf("е" to "ё", "ь" to "ъ")
    private val ALT_AR = mapOf("ا" to "أ إ آ ء", "و" to "ؤ", "ي" to "ئ ى", "ه" to "ة")
    private var toneBase: String? = null
    private var toneVars: List<String> = emptyList()
    private var toneHover = -1
    private val toneRect = RectF()
    private var toneAbove = true
    private var zoomD0 = 0f
    private var zoomZ0 = 1f
    private var combineBtn: Pair<String, String>? = null
    private var combineMode = false
    private val combineSel = ArrayList<String>()
    private var genFiles: List<File> = emptyList()
    private var userName = ""
    private var nameShow = 0
    private var nameSkew = 0
    private var nameRot = 0
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
        userName = prefs.getString("user_name", "") ?: ""
        langIdx = Langs.cur(context)
        nameShow = prefs.getInt("name_show", 0)
        nameSkew = prefs.getInt("name_skew", 0)
        nameRot = prefs.getInt("name_rot", 0)
        searchScale = prefs.getInt("search_h", 100) / 100f
        val nhf = prefs.getInt("kb_h", 100) / 100f
        val ndy = prefs.getInt("icon_y", 0).toFloat()
        val nsz = prefs.getInt("icon_sz", 100) / 100f
        oneHand = prefs.getInt("onehand", 0)
        val nle = prefs.getInt("left_icon", 0) == 1
        if (nhf != hf || ndy != iconDy || nsz != iconSz || nle != leftEmoji) {
            hf = nhf; iconDy = ndy; iconSz = nsz; leftEmoji = nle
            keysCache.clear()
            requestLayout()
        }
        invalidate()
    }

    // ---------- tata letak ----------
    private fun ky(y: Float) = y * hf
    private fun kh() = 120f * hf
    private fun iconCy() = ky(838f) + iconDy

    /** Baris tombol yang dibagi rata antara left..right. */
    private fun fitRow(chars: List<String>, left: Float, right: Float, y: Float, gap: Float): List<Key> {
        val n = chars.size; val w = (right - left - (n - 1) * gap) / n
        return chars.mapIndexed { i, c -> Key(c, c[0].code, left + i * (w + gap), ky(y), w, kh(), K_LETTER) }
    }

    private fun row(chars: List<String>, x0: Float, pitch: Float, w: Float, y: Float): List<Key> =
        chars.mapIndexed { i, c -> Key(c, c[0].code, x0 + i * pitch, ky(y), w, kh(), K_LETTER) }

    private fun iconKeys(): List<Key> {
        val s = 90f * iconSz
        return listOf(
            if (leftEmoji) Key("emoji", EMOJI, 135f - s / 2, iconCy() - s / 2, s, s, K_ICON)
            else Key("globe", GLOBE, 135f - s / 2, iconCy() - s / 2, s, s, K_ICON),
            Key("mic", MIC, 945f - s / 2, iconCy() - s / 2, s, s, K_ICON)
        )
    }

    private fun bottomRow(first: Key, second: Key): List<Key> =
        if (leftEmoji) listOf(
            first,
            Key(" ", SPACE, 160f, ky(628f), 630f, kh(), K_FUNC),
            Key("\n", RET, 808f, ky(628f), 252f, kh(), K_FUNC)
        ) + iconKeys()
        else listOf(
            first, second,
            Key(" ", SPACE, 298f, ky(628f), 492f, kh(), K_FUNC),
            Key("\n", RET, 808f, ky(628f), 252f, kh(), K_FUNC)
        ) + iconKeys()

    private fun build(p: Int): List<Key> {
        val ck = langIdx * 8 + p
        keysCache[ck]?.let { return it }
        val k = ArrayList<Key>()
        when (p) {
            0 -> {
                val bottom = bottomRow(Key("123", NUM, 20f, ky(628f), 121f, kh(), K_FUNC), Key("emoji", EMOJI, 160f, ky(628f), 121f, kh(), K_FUNC))
                when (langIdx) {
                    Langs.RU -> {
                        k += fitRow("ЙЦУКЕНГШЩЗХ".map { it.toString() }, 20f, 1060f, 182f, 10f)
                        k += fitRow("ФЫВАПРОЛДЖЭ".map { it.toString() }, 20f, 1060f, 331f, 10f)
                        k += Key("shift", SHIFT, 20f, ky(479f), 121f, kh(), K_FUNC)
                        k += fitRow("ЯЧСМИТЬБЮ".map { it.toString() }, 152f, 925f, 479f, 10f)
                        k += Key("del", DEL, 937f, ky(479f), 123f, kh(), K_FUNC)
                    }
                    Langs.AR -> {
                        k += fitRow("ضصثقفغعهخحجد".map { it.toString() }, 20f, 1060f, 182f, 8f)
                        k += fitRow("شسيبلاتنمكطذ".map { it.toString() }, 20f, 1060f, 331f, 8f)
                        k += fitRow(listOf("ئ", "ء", "ؤ", "ر", "لا", "ى", "ة", "و", "ز", "ظ"), 20f, 925f, 479f, 8f)
                        k += Key("del", DEL, 937f, ky(479f), 123f, kh(), K_FUNC)
                    }
                    else -> {
                        k += row("QWERTYUIOP".map { it.toString() }, 20f, 105.6f, 88f, 182f)
                        if (langIdx == Langs.ES) k += row("ASDFGHJKLÑ".map { it.toString() }, 20f, 105.6f, 88f, 331f)
                        else k += row("ASDFGHJKL".map { it.toString() }, 72f, 105.6f, 88f, 331f)
                        k += Key("shift", SHIFT, 20f, ky(479f), 121f, kh(), K_FUNC)
                        k += row("ZXCVBNM".map { it.toString() }, 178f, 105.6f, 88f, 479f)
                        k += Key("del", DEL, 937f, ky(479f), 123f, kh(), K_FUNC)
                    }
                }
                k += bottom
            }
            1, 2 -> {
                val r1 = if (p == 1) "1234567890" else "[]{}#%^*+="
                val r2 = if (p == 1) "-/:;()$&@\"" else "_\\|~<>€£¥•"
                k += row(r1.map { it.toString() }, 20f, 105.6f, 88f, 182f)
                k += row(r2.map { it.toString() }, 20f, 105.6f, 88f, 331f)
                k += Key(if (p == 1) "#+=" else "123", SYM, 20f, ky(479f), 121f, kh(), K_FUNC)
                k += row(if (langIdx == Langs.AR) listOf(".", "،", "؟", "!", "'") else listOf(".", ",", "?", "!", "'"), 188f, 144f, 126f, 479f)
                k += Key("del", DEL, 937f, ky(479f), 123f, kh(), K_FUNC)
                k += bottomRow(Key("ABC", ABC, 20f, ky(628f), 121f, kh(), K_FUNC), Key("emoji", EMOJI, 160f, ky(628f), 121f, kh(), K_FUNC))
            }
            else -> {
                k += Key("ABC", ABC, 20f, iconCy() - 45f, 150f, 90f, K_PLAIN)
                if (p == 3) k += Key("del", DEL, 937f, tabCy() - 42f, 123f, 84f, K_PLAIN)
                k += iconKeys().filter { it.code == MIC }
            }
        }
        keysCache[ck] = k
        return k
    }

    // geometri panel emoji (mengikuti proporsi emoji iPhone asli)
    private fun tabCy() = ky(687f)
    private fun gridTop() = ky(144f)
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
        tab == 1 -> genFiles.map { "@" + it.name } + repo.recents()
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
        genFiles = GenStore.list(context)
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

    fun release() { zoomer.hideNow(); sounds.release(); exec.shutdown() }

    fun setCombine(p: Pair<String, String>?) { combineBtn = p; invalidate(0, 0, width, (ky(165f) * scale).toInt()) }

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
        } else if (!cursorMode) drawSuggestions(c)
        for (k in keys()) drawKey(c, k, down.indexOfValue(k) >= 0)
        for (i in 0 until down.size()) {
            val k = down.valueAt(i)
            if (k.kind == K_LETTER && page < 3) drawPopup(c, k)
        }
        if (!capturing) {
            if (menuOpen) drawMenu(c)
            drawToneStrip(c)
            drawAccentStrip(c)
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

    private fun drawPill(c: Canvas, top: Float, bottom: Float, left: Float = 20f, right: Float = 1060f) {
        pKey.color = cPill
        rf.set(left, top, right, bottom)
        c.drawRoundRect(rf, (bottom - top) / 2, (bottom - top) / 2, pKey)
        val cy = (top + bottom) / 2
        drawMagnifier(c, left + 44f, cy)
        pTxt.textAlign = Paint.Align.LEFT; pTxt.textSize = 44f
        if (query.isEmpty()) { pTxt.color = cHint; c.drawText(Lang.tr("Cari Emoji"), left + 84f, cy + 15f, pTxt) }
        else {
            pTxt.color = cFg
            var t = query; while (pTxt.measureText(t) > (right - left) - 280f && t.length > 1) t = t.drop(1)
            c.drawText(t, left + 84f, cy + 15f, pTxt)
            if (searchMode) { val x = left + 84f + pTxt.measureText(t) + 3f; pKey.color = cBlue; c.drawRect(x, cy - 24f, x + 3f, cy + 24f, pKey) }
            pKey.color = cHint; c.drawCircle(right - 45f, cy, 22f, pKey)
            pStroke.color = cPill; pStroke.strokeWidth = 4f
            c.drawLine(right - 53f, cy - 8f, right - 37f, cy + 8f, pStroke); c.drawLine(right - 37f, cy - 8f, right - 53f, cy + 8f, pStroke)
            pStroke.color = cFg; pStroke.strokeWidth = 5.5f
        }
        pTxt.textAlign = Paint.Align.CENTER; pTxt.color = cFg
    }

    /** Ikon "Genmoji": wajah dengan percikan plus. */
    private fun drawGenIcon(c: Canvas, cx: Float, cy: Float, color: Int) {
        pStroke.color = color; pFill.color = color; pStroke.strokeWidth = 3.4f
        rf.set(cx - 17f, cy - 15f, cx + 17f, cy + 19f); c.drawArc(rf, 55f, 280f, false, pStroke)
        c.drawCircle(cx - 6f, cy - 2f, 2.3f, pFill); c.drawCircle(cx + 6f, cy - 2f, 2.3f, pFill)
        rf.set(cx - 9f, cy + 1f, cx + 9f, cy + 13f); c.drawArc(rf, 20f, 140f, false, pStroke)
        c.drawLine(cx + 14f, cy - 24f, cx + 14f, cy - 12f, pStroke); c.drawLine(cx + 8f, cy - 18f, cx + 20f, cy - 18f, pStroke)
        pStroke.color = cFg; pFill.color = cFg; pStroke.strokeWidth = 5.5f
    }

    private fun drawGenButton(c: Canvas, top: Float, bottom: Float, left: Float, right: Float, label: String) {
        pKey.color = cPill
        rf.set(left, top, right, bottom); c.drawRoundRect(rf, (bottom - top) / 2, (bottom - top) / 2, pKey)
        val cy = (top + bottom) / 2
        val iconW = 46f
        pTxt.textSize = 38f; pTxt.color = cFg
        val tw = pTxt.measureText(label)
        val total = iconW + 12f + tw
        val x0 = (left + right) / 2 - total / 2
        if (label != Lang.tr("Batal")) drawGenIcon(c, x0 + 22f, cy + 2f, cFg)
        pTxt.textAlign = Paint.Align.LEFT
        c.drawText(label, if (label == Lang.tr("Batal")) (left + right) / 2 - tw / 2 else x0 + iconW + 12f, cy + 13f, pTxt)
        pTxt.textAlign = Paint.Align.CENTER
    }

    private fun drawEmojiTop(c: Canvas) {
        val h = 97f * searchScale * hf
        val mid = ky(68.5f)
        val top = mid - h / 2; val bot = mid + h / 2
        if (combineMode) {
            // pill pilihan: menampilkan dua emoji yang sudah dipilih
            pKey.color = cPill; rf.set(20f, top, 790f, bot); c.drawRoundRect(rf, (bot - top) / 2, (bot - top) / 2, pKey)
            val cy = (top + bot) / 2
            pTxt.textAlign = Paint.Align.LEFT; pTxt.textSize = 38f; pTxt.color = cHint
            c.drawText(if (combineSel.isEmpty()) Lang.tr("Pilih dua emoji…") else Lang.tr("Pilih satu lagi…"), 54f, cy + 13f, pTxt)
            pEmoji.textSize = 56f
            for (i in combineSel.indices) c.drawText(combineSel[i], 640f + i * 76f, cy + 21f, pEmoji)
            pTxt.textAlign = Paint.Align.CENTER; pTxt.color = cFg
            drawGenButton(c, top, bot, 810f, 1060f, Lang.tr("Batal"))
        } else if (searchEnabled) {
            drawPill(c, top, bot, 20f, 790f)
            drawGenButton(c, top, bot, 810f, 1060f, Lang.tr("Gabung"))
        } else drawGenButton(c, top, bot, 20f, 1060f, Lang.tr("Gabung emoji"))
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
                val it0 = items[idx]
                val ccx = GX0 + col * cw - off + cw / 2; val ccy = top + r * ch + ch / 2
                if (it0.startsWith("@")) {
                    val gb = thumb(File(GenStore.dir(context), it0.substring(1)))
                    if (gb != null) { rf.set(ccx - 50f, ccy - 50f, ccx + 50f, ccy + 50f); c.drawBitmap(gb, null, rf, pBmp) }
                } else c.drawText(repo.shown(it0), ccx, ccy + 33f, pEmoji)
            }
            if (items.isEmpty()) {
                pTxt.color = cHint; pTxt.textSize = 40f
                c.drawText(if (query.isNotEmpty()) Lang.tr("Tidak ada hasil") else if (tab == 1) Lang.tr("Belum ada emoji terbaru") else Lang.tr("Memuat emoji…"), 540f, top + 200f, pTxt)
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
        if (combineBtn != null) {
            pTxt.textSize = 44f; pTxt.color = cFg
            drawGenIcon(c, 440f, cy, cFg)
            pTxt.textAlign = Paint.Align.LEFT
            c.drawText(Lang.tr("Gabungkan"), 486f, cy + 15f, pTxt)
            pTxt.textAlign = Paint.Align.CENTER
            return
        }
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
                val aOld = (1f - t / 0.4f).coerceIn(0f, 1f)
                val aNew = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f)
                if (aOld > 0f) drawSugText(c, fromSug[i], xs[i], cy, i, (aOld * 255).toInt())
                if (aNew > 0f) drawSugText(c, curSug[i], xs[i], cy, i, (aNew * 255).toInt())
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
        val blank = cursorMode && page < 3
        pKey.color = if (!blank) base else when {
            isRet && showReturnArrow -> cBlue
            k.code == SHIFT -> if (light) 0xFFB4B8C2.toInt() else 0xFF9C9C9C.toInt()
            k.kind == K_LETTER || k.code == SPACE -> if (light) 0xFFE6E8ED.toInt() else 0xFF555555.toInt()
            else -> cFunc
        }
        rf.set(k.x, k.y, k.x + k.w, k.y + k.h)
        c.drawRoundRect(rf, 21f, 21f, pKey)
        if (blank) return
        pTxt.color = cFg
        when (k.code) {
            SHIFT -> drawShift(c, cx, cy)
            DEL -> drawDelete(c, cx, cy)
            EMOJI -> drawSmiley(c, cx, cy)
            RET -> if (showReturnArrow) drawArrow(c, cx, cy) else { pTxt.textSize = 44f; c.drawText(returnLabel, cx, cy + 15f, pTxt) }
            SPACE -> {
                val fl = SystemClock.uptimeMillis() - langFlashAt
                val flashing = langFlashAt != 0L && fl < 1600L
                if (flashing) postInvalidateOnAnimation()
                val fa = if (flashing && fl > 1200L) (1600L - fl) / 400f else 1f
                val n = if (flashing) langFlash else if ((nameShow == 1 || nameShow == 3) && userName.isNotBlank()) userName else ""
                if (n.isNotEmpty()) {
                    pTxt.textSize = 34f; pTxt.color = cHint; pTxt.alpha = (255 * fa).toInt()
                    var t = n; while (pTxt.measureText(t) > 440f && t.length > 3) t = t.dropLast(1)
                    pTxt.textSkewX = -Math.tan(Math.toRadians(nameSkew.toDouble())).toFloat()
                    c.save(); c.rotate(nameRot.toFloat(), cx, cy)
                    c.drawText(t, cx, cy + 12f, pTxt)
                    c.restore()
                    pTxt.textSkewX = 0f; pTxt.color = cFg
                }
            }
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
            k.code == EMOJI -> null
            k.code == MIC && micActive -> PorterDuffColorFilter(cBlueDown, PorterDuff.Mode.SRC_IN)
            light -> PorterDuffColorFilter(cFg, PorterDuff.Mode.SRC_IN)
            else -> null
        }
        if (k.code == EMOJI) {
            val h = 38f * iconSz
            rf.set(cx - h, cy - h, cx + h, cy + h); c.drawBitmap(if (light) kbEmojiBlack else kbEmojiWhite, null, rf, pBmp)
        } else if (k.code == GLOBE) {
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
    /** Susunan baris menu: 0 pengaturan, 10..14 bahasa aktif, 3 emoji, 4 clipboard, 5 ganti keyboard, 6 ikon satu tangan. */
    private fun menuModel(): List<Int> = listOf(0) + Langs.enabled(context).map { 10 + it } + listOf(3, 4, 5, 6)
    private fun menuRow(): Float = min(116f, (menuBottom() - 16f - 28f) / menuModel().size)
    private val MENU_L = 16f
    private val MENU_W = 600f
    private fun menuBottom() = iconCy() - 55f * iconSz
    private fun menuTop() = menuBottom() - (menuModel().size * menuRow() + 28f)

    private fun drawMenu(c: Canvas) {
        val model = menuModel()
        val top = menuTop(); val mr = menuRow()
        if (cShadow != 0) { pKey.color = cShadow; rf.set(MENU_L, top + 4f, MENU_L + MENU_W, menuBottom() + 4f); c.drawRoundRect(rf, 56f, 56f, pKey) }
        pKey.color = cMenuBg
        rf.set(MENU_L, top, MENU_L + MENU_W, menuBottom()); c.drawRoundRect(rf, 56f, 56f, pKey)
        pTxt.textAlign = Paint.Align.LEFT; pTxt.textSize = if (mr < 88f) 36f else 44f
        for ((i, code) in model.withIndex()) {
            val y0 = top + 14f + i * mr
            if (code == 6) {
                pTxt.textAlign = Paint.Align.CENTER
                val cw = MENU_W / 3
                for (j in 0..2) {
                    val mode = when (j) { 0 -> 1; 1 -> 0; else -> 2 }
                    val sel = oneHand == mode
                    val cx = MENU_L + cw * j + cw / 2; val cy = y0 + mr / 2
                    if (sel || (menuHover == i && menuHoverCell == j)) {
                        pKey.color = cMenuSel; rf.set(cx - 62f, cy - min(46f, mr / 2 - 4f), cx + 62f, cy + min(46f, mr / 2 - 4f)); c.drawRoundRect(rf, 40f, 40f, pKey)
                    }
                    drawKbIcon(c, cx, cy, j)
                }
                continue
            }
            val label = when {
                code == 0 -> Lang.tr("Pengaturan Keyboard...")
                code in 10..14 -> Langs.NAMES[code - 10]
                code == 3 -> "Emoji"
                code == 4 -> "Clipboard"
                else -> Lang.tr("Ganti keyboard")
            }
            val sel = code in 10..14 && code - 10 == langIdx
            if (sel || menuHover == i) {
                pKey.color = cMenuSel; rf.set(MENU_L + 12f, y0 + 6f, MENU_L + MENU_W - 12f, y0 + mr - 6f)
                c.drawRoundRect(rf, (mr - 12f) / 2, (mr - 12f) / 2, pKey)
            }
            pTxt.color = cFg
            c.drawText(label, MENU_L + 46f, y0 + mr / 2 + pTxt.textSize * 0.34f, pTxt)
        }
        pTxt.textAlign = Paint.Align.CENTER
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
        return if (y >= menuTop() + 14f && r in 0 until menuModel().size) r else -1
    }

    private fun menuAct(row: Int, x: Float) {
        val model = menuModel()
        if (row !in model.indices) return
        val code = model[row]
        when {
            code == 0 -> l.onOpenSettings()
            code in 10..14 -> setLanguage(code - 10)
            code == 3 -> { page = 3; searchMode = false; scrollPx = 0f; if (query.isEmpty() && tab == 0) reloadStickers() }
            code == 4 -> { page = 4; searchMode = false; scrollPx = 0f; scroller.forceFinished(true); reloadClip() }
            code == 5 -> l.onGlobe(false)
            code == 6 -> {
                val i = ((x - MENU_L) / (MENU_W / 3)).toInt().coerceIn(0, 2)
                oneHand = when (i) { 0 -> 1; 1 -> 0; else -> 2 }
                prefs.edit().putInt("onehand", oneHand).apply()
            }
        }
        invalidate()
    }

    private fun setLanguage(i: Int) {
        if (i == langIdx) { langFlash = Langs.NAMES[i]; langFlashAt = SystemClock.uptimeMillis(); invalidate(); return }
        prefs.edit().putInt("lang", i).apply()
        langIdx = i
        l.onLanguage(i)
        langFlash = Langs.NAMES[i]; langFlashAt = SystemClock.uptimeMillis()
        probs = emptyMap()
        invalidate()
    }

    private fun closeMenu() { menuOpen = false; menuHover = -1; menuHoverCell = -1; invalidate() }

    private fun handleMenuTouch(e: MotionEvent): Boolean {
        val idx = e.actionIndex
        val x = e.getX(idx) / scale; val y = e.getY(idx) / scale
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> {
                menuHover = menuHit(x, y)
                menuHoverCell = if (menuHover >= 0 && menuModel().getOrNull(menuHover) == 6) ((x - MENU_L) / (MENU_W / 3)).toInt().coerceIn(0, 2) else -1
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
        set(v) { field = v; invalidate() }
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
        if (gridId >= 0 && !gridMoved) {
            val idx = cellAt(gridX0, gridY0)
            if (isStickerTab()) {
                if (idx in 1..stickerFiles.size) {
                    val f = stickerFiles[idx - 1]
                    f.delete(); thumbs.remove(f.path); loading.remove(f.path)
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    Toast.makeText(context, Lang.tr("Stiker dihapus"), Toast.LENGTH_SHORT).show()
                    gridId = -1; reloadStickers()
                }
            } else if (idx >= 0) startZoom(idx)
        }
    }

    // ---- emoji besar (tekan-tahan) ----
    private fun toScreen(x: Float, y: Float): Pair<Int, Int> {
        val loc = IntArray(2); getLocationOnScreen(loc)
        val sx = if (oneHand != 0) loc[0] + oneOff() + x * ONE_F else loc[0] + x
        return Pair(sx.toInt(), (loc[1] + y).toInt())
    }

    private fun zoomContent(item: String): Pair<String?, Bitmap?> =
        if (item.startsWith("@")) Pair(null, try { BitmapFactory.decodeFile(File(GenStore.dir(context), item.substring(1)).path) } catch (_: Exception) { null })
        else Pair(repo.shown(item), null)

    private fun startZoom(idx: Int) {
        val items = gridItems()
        if (idx !in items.indices) return
        val item = items[idx]
        zoomItem = item; zoomActive = true; zoomPid = gridId; gridId = -1
        vt?.recycle(); vt = null
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        // pilihan warna kulit (untuk emoji yang mendukung)
        toneBase = null; toneVars = emptyList(); toneHover = -1
        val base = repo.baseOf(item)
        val vars = if (base != null) repo.variants(base) else null
        var shiftY = 0
        if (base != null && vars != null) {
            toneBase = base; toneVars = listOf(base) + vars
            val col = idx / gridRows(); val row = idx % gridRows()
            val cx = GX0 + col * cellW() - scrollPx / scale + cellW() / 2
            val cellTop = gridTop() + row * cellH()
            val sw = 6 * 88f + 24f; val sh = 116f
            val left = (cx - sw / 2).coerceIn(12f, DW - 12f - sw)
            toneAbove = cellTop - sh - 8f >= 4f
            val top = if (toneAbove) cellTop - sh - 8f else cellTop + cellH() + 8f
            toneRect.set(left, top, left + sw, top + sh)
            if (toneAbove) shiftY = ((sh + 16f) * scale).toInt()
        }
        val (sx, sy) = toScreen(gridX0, gridY0)
        val (t, b) = zoomContent(item)
        zoomer.show(this, t, b, repo.face, sx, sy - shiftY)
        invalidate()
    }

    private fun toneShift(): Int = if (toneBase != null && toneAbove) ((toneRect.height() + 16f) * scale).toInt() else 0

    private fun endZoom() {
        zoomActive = false; zoomPid = -1; zoomItem = null; zoomD0 = 0f
        toneBase = null; toneVars = emptyList(); toneHover = -1
        zoomer.hide()
        invalidate()
    }

    /** Lepas jari di atas salah satu warna kulit: sisipkan dan ingat pilihannya. */
    private fun commitTone(): Boolean {
        val base = toneBase ?: return false
        val h = toneHover
        if (h < 0 || h >= toneVars.size) return false
        repo.saveTone(base, h - 1)
        tap()
        insertEmoji(toneVars[h])
        return true
    }

    private fun drawToneStrip(c: Canvas) {
        if (toneBase == null) return
        if (cShadow != 0) { pKey.color = cShadow; rf.set(toneRect.left, toneRect.top + 4f, toneRect.right, toneRect.bottom + 4f); c.drawRoundRect(rf, 44f, 44f, pKey) }
        pKey.color = cMenuBg; c.drawRoundRect(toneRect, 44f, 44f, pKey)
        pEmoji.textSize = 62f
        for (i in toneVars.indices) {
            val cx = toneRect.left + 12f + i * 88f + 44f
            if (i == toneHover) { pKey.color = cMenuSel; rf.set(cx - 40f, toneRect.top + 10f, cx + 40f, toneRect.bottom - 10f); c.drawRoundRect(rf, 36f, 36f, pKey) }
            c.drawText(toneVars[i], cx, toneRect.centerY() + 22f, pEmoji)
        }
    }

    private fun altsFor(label: String): List<String> {
        val m = when (langIdx) { Langs.ES -> ALT_ES; Langs.RU -> ALT_RU; Langs.AR -> ALT_AR; else -> ALT_LATIN }
        val v = m[label.lowercase()] ?: return emptyList()
        return v.split(' ').filter { it.isNotEmpty() }
    }

    private fun startAccent() {
        val k = accentKey ?: return
        if (down.get(accentPid) !== k) return
        val up = page == 0 && shift != 0
        val alts = altsFor(k.label).map { if (up && it != "ß") it.uppercase() else it }
        if (alts.isEmpty()) return
        accentList = alts; accentHover = -1
        val w = alts.size * 86f + 24f
        val left = (k.x + k.w / 2 - w / 2).coerceIn(8f, DW - 8f - w)
        val top = max(4f, k.y - 128f)
        accentRect.set(left, top, left + w, top + 112f)
        down.remove(accentPid)
        accentActive = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    private fun drawAccentStrip(c: Canvas) {
        if (!accentActive) return
        if (cShadow != 0) { pKey.color = cShadow; rf.set(accentRect.left, accentRect.top + 4f, accentRect.right, accentRect.bottom + 4f); c.drawRoundRect(rf, 36f, 36f, pKey) }
        pKey.color = cMenuBg; c.drawRoundRect(accentRect, 36f, 36f, pKey)
        pTxt.textSize = 58f
        for (i in accentList.indices) {
            val cx = accentRect.left + 12f + i * 86f + 43f
            if (i == accentHover) { pKey.color = cBlue; rf.set(cx - 38f, accentRect.top + 10f, cx + 38f, accentRect.bottom - 10f); c.drawRoundRect(rf, 30f, 30f, pKey) }
            pTxt.color = if (i == accentHover) Color.WHITE else cFg
            c.drawText(accentList[i], cx, accentRect.centerY() + 20f, pTxt)
        }
        pTxt.color = cFg
    }

    private fun handleAccentTouch(e: MotionEvent): Boolean {
        val k = accentKey
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val pi = e.findPointerIndex(accentPid)
                if (pi >= 0) {
                    val x = e.getX(pi) / scale; val y = e.getY(pi) / scale
                    val h = if (x >= accentRect.left && x <= accentRect.right && y < accentRect.bottom + 150f)
                        ((x - accentRect.left - 12f) / 86f).toInt().coerceIn(0, accentList.size - 1) else -1
                    if (h != accentHover) { accentHover = h; invalidate() }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (e.actionMasked == MotionEvent.ACTION_POINTER_UP && e.getPointerId(e.actionIndex) != accentPid) return true
                val h = accentHover
                if (e.actionMasked != MotionEvent.ACTION_CANCEL && k != null) {
                    val t = if (h in accentList.indices) accentList[h] else (if (page == 0 && shift == 0) k.label.lowercase() else k.label)
                    l.onText(t)
                    if (page == 0 && shift == 1) { shift = 0; l.onShiftChange(0) }
                }
                accentActive = false; accentKey = null; accentHover = -1
                invalidate()
            }
        }
        return true
    }

    private fun pinchDist(e: MotionEvent): Float {
        val dx = e.getX(0) - e.getX(1); val dy = e.getY(0) - e.getY(1)
        return Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    }

    private fun handleZoomTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount >= 2) { zoomD0 = pinchDist(e); zoomZ0 = zoomer.zoom }
            MotionEvent.ACTION_MOVE -> {
                val pi = e.findPointerIndex(zoomPid)
                if (pi < 0) return true
                val (sx, sy0) = toScreen(e.getX(pi), e.getY(pi))
                val sy = sy0 - toneShift()
                if (e.pointerCount >= 2 && zoomD0 > 0f) {
                    zoomer.setZoom(zoomZ0 * pinchDist(e) / zoomD0, this, sx, sy)
                } else if (toneBase != null) {
                    val dx = e.getX(pi) / scale; val dy = e.getY(pi) / scale
                    val inside = dx >= toneRect.left && dx <= toneRect.right && dy >= toneRect.top - 70f && dy <= toneRect.bottom + 30f
                    val h = if (inside) ((dx - toneRect.left - 12f) / 88f).toInt().coerceIn(0, toneVars.size - 1) else -1
                    if (h != toneHover) {
                        toneHover = h; invalidate()
                        val shown = if (h >= 0) toneVars[h] else repo.shown(toneBase!!)
                        zoomer.update(this, shown, null, repo.face, sx, sy)
                    } else zoomer.update(this, toneVars.getOrNull(toneHover) ?: repo.shown(toneBase!!), null, repo.face, sx, sy)
                } else {
                    val i = cellAt(e.getX(pi), e.getY(pi)); val items = gridItems()
                    if (i in items.indices && items[i] != zoomItem) {
                        zoomItem = items[i]
                        val (t, b) = zoomContent(items[i]); zoomer.update(this, t, b, repo.face, sx, sy)
                    } else zoomItem?.let { cur -> val (t, b) = zoomContent(cur); zoomer.update(this, t, b, repo.face, sx, sy) }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> { if (e.getPointerId(e.actionIndex) == zoomPid) { commitTone(); endZoom() } else zoomD0 = 0f }
            MotionEvent.ACTION_UP -> { commitTone(); endZoom() }
            MotionEvent.ACTION_CANCEL -> endZoom()
        }
        return true
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

        if (zoomActive) return handleZoomTouch(e)
        if (accentActive) return handleAccentTouch(e)
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
                        val xd = x / s
                        val btnL = if (searchEnabled || combineMode) 810f else 20f
                        if (xd >= btnL) { tap(); combineMode = !combineMode; combineSel.clear(); invalidate() }
                        else if (searchEnabled && !combineMode) { tap(); enterSearch() }
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
                    } else if (combineBtn != null) {
                        tap(); combineBtn?.let { l.onCombine(it.first, it.second) }
                    } else {
                        val slot = (x / (width / 3f)).toInt().coerceIn(0, 2)
                        if (sug[slot].isNotEmpty()) { tap(); l.onSuggestion(slot) }
                    }
                    return true
                }
                val k = find(x, y) ?: return true
                down.put(id, k)
                tap(soundFor(k))
                if (k.kind == K_LETTER && !searchMode && (page == 0 || page == 1) && altsFor(k.label).isNotEmpty()) {
                    accentPid = id; accentKey = k
                    handler.postDelayed(accentRunnable, 380)
                }
                when (k.code) {
                    DEL -> { repeating = k; dispatchDelete(); repeatRunnable.n = 0; handler.postDelayed(repeatRunnable, 380) }
                    SPACE -> { cursorMode = false; cursorLastX = x; if (!searchMode) handler.postDelayed(spaceLong, 420) }
                    GLOBE -> handler.postDelayed(globeLong, 450)
                    EMOJI -> if (k.kind == K_ICON) handler.postDelayed(globeLong, 450)
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
                    if (page >= 3 || old.code == DEL || old.code == SPACE || old.code == GLOBE || old.kind == K_ICON) continue
                    val nk = find(x, y)
                    if (nk != null && nk !== old) { handler.removeCallbacks(accentRunnable); down.put(pid, nk); invalidateKey(old); invalidateKey(nk) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val k = down.get(id)
                if (k != null) {
                    down.remove(id)
                    handler.removeCallbacks(repeatRunnable); handler.removeCallbacks(spaceLong); handler.removeCallbacks(globeLong); handler.removeCallbacks(accentRunnable)
                    repeating = null
                    if (!cursorMode || k.code != SPACE) commit(k)
                    cursorMode = false
                    invalidateKey(k)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(repeatRunnable); handler.removeCallbacks(spaceLong); handler.removeCallbacks(globeLong); handler.removeCallbacks(accentRunnable)
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
        val labels = arrayOf(Lang.tr("Teks"), Lang.tr("Gambar"), Lang.tr("Prompt"))
        pTxt.textSize = 38f
        for (i in 0..2) { pTxt.color = if (i == clipTab) cFg else cHint; c.drawText(labels[i], 20f + i * sw + sw / 2, top + h / 2 + 13f, pTxt) }
        pTxt.color = cFg

        val rows = clipRows(); val cw = clipCardW(); val ch = clipCardH(); val tp0 = clipTop()
        c.save(); c.clipRect(0f, tp0 - 4f, DW, clipBottom() + 6f)
        val off = scrollPx / s
        if (clipItems.isEmpty()) {
            pTxt.color = cHint; pTxt.textSize = 38f
            val msg = when (clipTab) { 0 -> Lang.tr("Belum ada teks yang disalin"); 1 -> Lang.tr("Belum ada gambar yang disalin"); else -> Lang.tr("Belum ada teks panjang yang disalin") }
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
        c.drawText(if (it.pinned) Lang.tr("Lepas sematan") else Lang.tr("Sematkan"), pvPin.left + 44f, pvPin.centerY() + 15f, pTxt)
        val red = if (light) 0xFFFF3B30.toInt() else 0xFFFF453A.toInt()
        pTxt.color = red
        c.drawText(Lang.tr("Hapus"), pvDel.left + 44f, pvDel.centerY() + 15f, pTxt)
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
            return
        }
        val items = gridItems()
        if (i >= items.size) return
        val it0 = items[i]
        if (combineMode) {
            if (!it0.startsWith("@")) {
                tap(); combineSel.add(repo.shown(it0))
                if (combineSel.size >= 2) { val a = combineSel[0]; val b = combineSel[1]; combineMode = false; combineSel.clear(); l.onCombine(a, b) }
                invalidate()
            }
            return
        }
        tap()
        if (it0.startsWith("@")) l.onClipImage(File(GenStore.dir(context), it0.substring(1))) else insertEmoji(repo.shown(it0))
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
            GLOBE -> setLanguage(Langs.next(context, langIdx))
            MIC -> l.onMic()
            else -> {
                val t = if (page == 0 && shift == 0) k.label.lowercase() else k.label
                if (searchMode) { query += t.lowercase(); invalidate() } else l.onText(t)
                if (page == 0 && shift == 1) { shift = 0; l.onShiftChange(0) }
            }
        }
    }
}

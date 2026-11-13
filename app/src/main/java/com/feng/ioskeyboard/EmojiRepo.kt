package com.feng.ioskeyboard

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import java.io.File

/** Data emoji (kategori + kata kunci Indonesia/Inggris) dan font Apple Color Emoji. */
class EmojiRepo(private val ctx: Context) {
    companion object {
        val CATS = arrayOf("smile", "animal", "food", "activity", "travel", "object", "symbol", "flag")
        private const val FONT = "AppleColorEmoji.ttf"
    }

    @Volatile var byCat: Map<String, List<String>> = emptyMap()
    @Volatile var face: Typeface? = null
    private var all: List<String> = emptyList()
    private var texts: List<Array<String>> = emptyList()
    var onChange: (() -> Unit)? = null

    // varian warna kulit: dasar -> 5 varian (terang..gelap)
    private val toneMap = HashMap<String, List<String>>()
    private val toneRev = HashMap<String, String>()

    fun variants(base: String): List<String>? = toneMap[base]
    /** Emoji dasar bila [e] punya varian warna kulit (e dasar atau salah satu variannya), selain itu null. */
    fun baseOf(e: String): String? = if (toneMap.containsKey(e)) e else toneRev[e]

    /** Emoji yang ditampilkan: memakai warna kulit yang terakhir dipilih untuk emoji itu. */
    fun shown(e: String): String {
        val v = toneMap[e] ?: return e
        val i = prefs.getInt("tone_$e", -1)
        return if (i in 0..4) v[i] else e
    }

    fun saveTone(base: String, idx: Int) { prefs.edit().putInt("tone_$base", idx).apply() }
    private val ui = Handler(Looper.getMainLooper())
    private val prefs = ctx.getSharedPreferences("kb", Context.MODE_PRIVATE)

    fun load() {
        Thread {
            try {
                val cats = HashMap<String, ArrayList<String>>()
                val a = ArrayList<String>(); val t = ArrayList<Array<String>>()
                ctx.assets.open("emoji.tsv").bufferedReader().useLines { ls ->
                    for (line in ls) {
                        val p = line.split('\t'); if (p.size < 3) continue
                        a.add(p[0]); t.add(p[2].split(' ').toTypedArray())
                        cats.getOrPut(p[1]) { ArrayList() }.add(p[0])
                    }
                }
                all = a; texts = t; byCat = cats
                try {
                    ctx.assets.open("tones.tsv").bufferedReader().useLines { ls ->
                        for (line in ls) {
                            val p = line.split('\t'); if (p.size < 2) continue
                            val vs = p[1].split(' ').filter { it.isNotEmpty() }
                            if (vs.size == 5) { toneMap[p[0]] = vs; for (v in vs) toneRev[v] = p[0] }
                        }
                    }
                } catch (_: Exception) {}
                ui.post { onChange?.invoke() }
            } catch (_: Throwable) {}
            try {
                val f = File(ctx.filesDir, FONT)
                if (!f.exists() || f.length() < 1_000_000) {
                    val tmp = File(ctx.filesDir, "$FONT.tmp")
                    ctx.assets.open(FONT).use { i -> tmp.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
                    tmp.renameTo(f)
                }
                val tf = Typeface.createFromFile(f)
                face = tf
                // buang emoji yang tidak punya glyph sama sekali
                val p = Paint().apply { typeface = tf }
                val fixed = HashMap<String, List<String>>()
                for ((k, v) in byCat) fixed[k] = v.filter { p.hasGlyph(it) }
                byCat = fixed
                ui.post { onChange?.invoke() }
            } catch (_: Throwable) {}
        }.start()
    }

    fun recents(): List<String> = prefs.getString("recent", "")!!.split(' ').filter { it.isNotEmpty() }

    fun addRecent(e: String) {
        val l = ArrayList(recents()); l.remove(e); l.add(0, e)
        prefs.edit().putString("recent", l.take(48).joinToString(" ")).apply()
    }

    fun search(q: String): List<String> {
        val toks = q.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (toks.isEmpty()) return emptyList()
        val hit = ArrayList<Pair<Int, Int>>()
        for (i in all.indices) {
            var score = 0; var ok = true
            for (tk in toks) {
                var best = 99
                for (w in texts[i]) {
                    val s = if (w == tk) 0 else if (w.startsWith(tk)) 1 else if (tk.length >= 3 && w.contains(tk)) 2 else 99
                    if (s < best) best = s
                }
                if (best == 99) { ok = false; break }
                score += best
            }
            if (ok) hit.add(score to i)
        }
        hit.sortWith(compareBy({ it.first }, { it.second }))
        return hit.take(150).map { all[it.second] }
    }
}

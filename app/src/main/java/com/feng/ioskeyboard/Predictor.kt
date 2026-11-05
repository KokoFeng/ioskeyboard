package com.feng.ioskeyboard

/**
 * Prediksi ringan: kamus kata + frekuensi (urutan = peringkat), bigram kecil, dan
 * kata yang dipelajari dari ketikan user. Pencarian lewat daftar terurut + binary search,
 * jadi tidak berat saat mengetik.
 */
class Predictor(words: List<String>) {
    private val sorted: Array<String> = words.map { it.lowercase() }.distinct().sorted().toTypedArray()
    private val rank = HashMap<String, Int>().apply {
        words.forEachIndexed { i, w -> putIfAbsent(w.lowercase(), i) }
    }
    private val learned = HashMap<String, Int>()
    private val bigram = HashMap<String, HashMap<String, Int>>()

    fun learn(prev: String?, word: String) {
        val w = word.lowercase()
        if (w.length < 2) return
        learned[w] = (learned[w] ?: 0) + 1
        if (!prev.isNullOrEmpty()) {
            bigram.getOrPut(prev.lowercase()) { HashMap() }.let { it[w] = (it[w] ?: 0) + 1 }
        }
    }

    private fun lowerBound(p: String): Int {
        var lo = 0; var hi = sorted.size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (sorted[m] < p) lo = m + 1 else hi = m }
        return lo
    }

    private fun score(w: String): Int =
        (rank[w] ?: 50000) - (learned[w] ?: 0) * 4000

    /** Saran kata untuk prefix yang sedang diketik. */
    fun suggest(prefix: String, prev: String?, max: Int = 3): List<String> {
        if (prefix.isEmpty()) return nextWord(prev, max)
        val p = prefix.lowercase()
        val out = ArrayList<String>()
        var i = lowerBound(p)
        val cand = ArrayList<String>()
        while (i < sorted.size && sorted[i].startsWith(p) && cand.size < 400) { cand.add(sorted[i]); i++ }
        for ((w, _) in learned) if (w.startsWith(p) && w !in cand) cand.add(w)
        val bi = prev?.let { bigram[it.lowercase()] }
        cand.sortBy { score(it) - (bi?.get(it) ?: 0) * 8000 }
        for (c in cand) { if (c != p) out.add(c); if (out.size == max) break }
        return out
    }

    /** Saran kata berikutnya setelah spasi. */
    fun nextWord(prev: String?, max: Int = 3): List<String> {
        val bi = prev?.let { bigram[it.lowercase()] }
        val out = ArrayList<String>()
        if (bi != null) bi.entries.sortedByDescending { it.value }.forEach { if (out.size < max) out.add(it.key) }
        for (d in DEFAULT_START) { if (out.size >= max) break; if (d !in out) out.add(d) }
        return out
    }

    /** Peluang huruf berikutnya (untuk zona sentuh adaptif ala iPhone). */
    fun nextLetters(prefix: String): Map<Char, Float> {
        if (prefix.isEmpty()) return emptyMap()
        val p = prefix.lowercase()
        var i = lowerBound(p)
        val m = HashMap<Char, Float>()
        var n = 0
        while (i < sorted.size && sorted[i].startsWith(p) && n < 300) {
            val w = sorted[i]
            if (w.length > p.length) {
                val c = w[p.length]
                val wt = 1f / (1f + (rank[w] ?: 50000) / 800f)
                m[c] = (m[c] ?: 0f) + wt
            }
            i++; n++
        }
        val total = m.values.sum()
        if (total > 0f) for (k in m.keys.toList()) m[k] = m[k]!! / total
        return m
    }

    /** Koreksi sederhana: kata yang hampir sama (1 huruf beda / tertukar). */
    fun autocorrect(word: String): String? {
        val w = word.lowercase()
        if (w.length < 3 || rank.containsKey(w) || learned.containsKey(w)) return null
        var best: String? = null; var bestScore = Int.MAX_VALUE
        val start = lowerBound(w.substring(0, 1))
        var i = start
        while (i < sorted.size && sorted[i][0] == w[0]) {
            val c = sorted[i]
            if (kotlin.math.abs(c.length - w.length) <= 1 && close(w, c)) {
                val s = rank[c] ?: 50000
                if (s < bestScore) { bestScore = s; best = c }
            }
            i++
        }
        return best
    }

    private fun close(a: String, b: String): Boolean {
        if (a.length == b.length) {
            var d = 0; var first = -1
            for (k in a.indices) if (a[k] != b[k]) { d++; if (first < 0) first = k }
            if (d == 1) return true
            if (d == 2 && first + 1 < a.length && a[first] == b[first + 1] && a[first + 1] == b[first]) return true
            return false
        }
        val (s, l) = if (a.length < b.length) a to b else b to a
        var k = 0; var skipped = false; var j = 0
        while (k < s.length && j < l.length) {
            if (s[k] == l[j]) { k++; j++ } else { if (skipped) return false; skipped = true; j++ }
        }
        return true
    }

    companion object {
        val DEFAULT_START = listOf("saya", "aku", "yang", "I", "the", "apa", "kamu")
    }
}

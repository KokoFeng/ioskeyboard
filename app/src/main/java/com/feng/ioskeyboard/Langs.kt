package com.feng.ioskeyboard

import android.content.Context

/** Bahasa ketikan keyboard: Indonesia, English, Español, العربية, Русский. */
object Langs {
    const val ID = 0; const val EN = 1; const val ES = 2; const val AR = 3; const val RU = 4
    val NAMES = arrayOf("Indonesia", "English (US)", "Español", "العربية", "Русский")
    val START = arrayOf(
        listOf("saya", "aku", "yang", "kamu", "apa"),
        listOf("I", "the", "you", "to", "it"),
        listOf("yo", "el", "la", "que", "no"),
        listOf("أنا", "هل", "في", "من", "ما"),
        listOf("я", "не", "что", "в", "это")
    )

    private fun prefs(c: Context) = c.getSharedPreferences("kb", Context.MODE_PRIVATE)

    fun cur(c: Context): Int = prefs(c).getInt("lang", 0).coerceIn(0, 4)

    fun mask(c: Context): Int { val m = prefs(c).getInt("lang_mask", 31) and 31; return if (m == 0) 31 else m }

    fun enabled(c: Context): List<Int> { val m = mask(c); return (0..4).filter { m and (1 shl it) != 0 } }

    /** Bahasa berikutnya yang aktif (dipakai ketuk globe). */
    fun next(c: Context, current: Int): Int {
        val e = enabled(c)
        val i = e.indexOf(current)
        return e[(i + 1) % e.size]
    }
}

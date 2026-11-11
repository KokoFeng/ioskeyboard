package com.feng.ioskeyboard

import android.content.Context
import android.graphics.Typeface

/** Dua gaya font: Reguler (font sistem) dan Bold (Bold_iOS.ttf). Huruf yang tidak ada di font Bold (mis. Arab) memakai font sistem. */
object Fonts {
    private var boldTf: Typeface? = null

    fun isBold(c: Context): Boolean = c.getSharedPreferences("kb", Context.MODE_PRIVATE).getInt("font_style", 0) == 1

    private fun bold(c: Context): Typeface {
        boldTf?.let { return it }
        val t = try { Typeface.createFromAsset(c.assets, "Bold_iOS.ttf") } catch (_: Exception) { Typeface.DEFAULT_BOLD }
        boldTf = t
        return t
    }

    /** Teks biasa. */
    fun main(c: Context): Typeface = if (isBold(c)) bold(c) else Typeface.create("sans-serif", Typeface.NORMAL)

    /** Teks tebal untuk judul. */
    fun heavy(c: Context): Typeface = if (isBold(c)) bold(c) else Typeface.create("sans-serif", Typeface.BOLD)

    /** Teks sedang (tombol). */
    fun medium(c: Context): Typeface = if (isBold(c)) bold(c) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

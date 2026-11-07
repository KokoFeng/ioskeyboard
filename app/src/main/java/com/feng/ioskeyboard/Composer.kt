package com.feng.ioskeyboard

import android.content.Context
import android.graphics.*
import java.io.File

/**
 * Menggabungkan dua emoji menjadi satu gambar dengan beberapa gaya tata letak.
 * (Ini komposisi dua emoji Apple, bukan gambar buatan AI.)
 */
object Composer {
    const val N = 512
    const val S_SPLIT = 0; const val S_HEAD = 1; const val S_SIDE = 2; const val S_CORNER = 3; const val S_BLEND = 4
    val STYLE_NAMES = arrayOf("Belah dua", "Aksesori di kepala", "Berdampingan", "Di sudut", "Campur")

    class Opt(var style: Int, var scaleB: Float = 1f, var dx: Float = 0f, var dy: Float = 0f, var mirror: Boolean = false, var rot: Float = 0f)

    fun face(c: Context): Typeface? = try {
        val f = File(c.filesDir, "AppleColorEmoji.ttf")
        if (f.exists()) Typeface.createFromFile(f) else null
    } catch (_: Exception) { null }

    fun glyph(e: String, tf: Typeface?): Bitmap {
        val b = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888)
        val size = 380f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = tf; textSize = size }
        Canvas(b).drawText(e, N / 2f, N / 2f + size * 0.355f, p)
        return b
    }

    private fun isFace(e: String): Boolean {
        val cp = e.codePointAt(0)
        return cp in 0x1F600..0x1F64F || cp in 0x1F910..0x1F92F || cp in 0x1F970..0x1F97A || cp == 0x1F978 || cp == 0x263A || cp == 0x1F643 || cp == 0x1F644
    }

    /** Pilih gaya dan urutan otomatis: wajah menjadi dasar, emoji lain jadi aksesori. */
    fun auto(a: String, b: String): Triple<String, String, Int> {
        val fa = isFace(a); val fb = isFace(b)
        return when {
            fa && !fb -> Triple(a, b, S_HEAD)
            !fa && fb -> Triple(b, a, S_HEAD)
            fa && fb -> Triple(a, b, S_SPLIT)
            else -> Triple(a, b, S_SIDE)
        }
    }

    private fun put(cv: Canvas, bmp: Bitmap, cx: Float, cy: Float, s: Float, rot: Float, mirror: Boolean, alpha: Int, p: Paint) {
        cv.save(); cv.translate(cx, cy); cv.rotate(rot); cv.scale(if (mirror) -s else s, s)
        p.alpha = alpha; cv.drawBitmap(bmp, -N / 2f, -N / 2f, p); p.alpha = 255
        cv.restore()
    }

    fun compose(a: Bitmap, b: Bitmap, o: Opt): Bitmap {
        val out = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val c = N / 2f
        when (o.style) {
            S_SPLIT -> {
                cv.drawBitmap(a, 0f, 0f, p)
                val layer = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888)
                val lc = Canvas(layer)
                put(lc, b, c + o.dx, c + o.dy, o.scaleB, o.rot, o.mirror, 255, p)
                val mp = Paint().apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    shader = LinearGradient(N * 0.42f, 0f, N * 0.58f, 0f, 0x00FFFFFF, -1, Shader.TileMode.CLAMP)
                }
                lc.drawRect(0f, 0f, N.toFloat(), N.toFloat(), mp)
                cv.drawBitmap(layer, 0f, 0f, p)
            }
            S_HEAD -> {
                put(cv, a, c, c + N * 0.07f, 0.86f, 0f, false, 255, p)
                put(cv, b, c + o.dx, N * 0.17f + o.dy, 0.48f * o.scaleB, -10f + o.rot, o.mirror, 255, p)
            }
            S_SIDE -> {
                put(cv, a, N * 0.31f, c + N * 0.04f, 0.68f, 0f, false, 255, p)
                put(cv, b, N * 0.69f + o.dx, c + N * 0.04f + o.dy, 0.68f * o.scaleB, o.rot, o.mirror, 255, p)
            }
            S_CORNER -> {
                put(cv, a, c, c, 0.92f, 0f, false, 255, p)
                val bx = N * 0.74f + o.dx; val by = N * 0.76f + o.dy
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
                cv.drawCircle(bx, by + 4f, N * 0.2f * o.scaleB, ring)
                put(cv, b, bx, by, 0.5f * o.scaleB, o.rot, o.mirror, 255, p)
            }
            else -> {
                put(cv, a, c, c, 0.9f, 0f, false, 255, p)
                put(cv, b, c + o.dx, c + o.dy, 0.8f * o.scaleB, o.rot, o.mirror, 150, p)
            }
        }
        return out
    }

    /** Terapkan kata kunci sederhana dari kolom "Jelaskan perubahan". */
    fun applyText(o: Opt, text: String): Opt {
        val t = text.lowercase()
        if ("besar" in t) o.scaleB *= 1.35f
        if ("kecil" in t) o.scaleB *= 0.75f
        if ("kiri" in t) o.dx -= 80f
        if ("kanan" in t) o.dx += 80f
        if ("atas" in t) o.dy -= 60f
        if ("bawah" in t) o.dy += 60f
        if ("cermin" in t) o.mirror = !o.mirror
        if ("putar" in t) o.rot += 25f
        if ("belah" in t) o.style = S_SPLIT
        if ("aksesori" in t || "topi" in t || "kepala" in t) o.style = S_HEAD
        if ("samping" in t) o.style = S_SIDE
        if ("sudut" in t) o.style = S_CORNER
        if ("campur" in t) o.style = S_BLEND
        return o
    }
}

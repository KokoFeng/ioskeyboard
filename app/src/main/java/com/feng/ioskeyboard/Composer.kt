package com.feng.ioskeyboard

import android.content.Context
import android.graphics.*
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * MODE DEMO: menggabungkan dua emoji dengan komposisi gambar (bukan AI).
 * Semua emoji dinormalkan (dipotong sesuai tepi gambar) sehingga tidak ada bagian yang terpotong.
 */
object Composer {
    const val N = 512
    private const val W = 1024
    const val S_SPLIT = 0; const val S_HEAD = 1; const val S_SIDE = 2; const val S_CORNER = 3; const val S_BLEND = 4; const val S_FEATURE = 5
    val STYLE_NAMES = arrayOf("Belah dua", "Aksesori di kepala", "Berdampingan", "Di sudut", "Campur", "Ekspresi menyatu")

    class Opt(var style: Int, var scaleB: Float = 1f, var dx: Float = 0f, var dy: Float = 0f, var mirror: Boolean = false, var rot: Float = 0f)

    /** Gambar emoji ternormalisasi N x N beserta kotak batas isinya. */
    class G(val bmp: Bitmap, val box: RectF)

    fun face(c: Context): Typeface? = try {
        val f = File(c.filesDir, "AppleColorEmoji.ttf")
        if (f.exists()) Typeface.createFromFile(f) else null
    } catch (_: Exception) { null }

    private fun alphaBounds(b: Bitmap): Rect? {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        var x0 = w; var x1 = -1; var y0 = h; var y1 = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) if ((px[row + x] ushr 24) > 10) {
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
            }
        }
        return if (x1 < 0) null else Rect(x0, y0, x1 + 1, y1 + 1)
    }

    fun glyph(e: String, tf: Typeface?): G {
        val s = 800
        val big = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val size = 420f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = tf; textSize = size }
        Canvas(big).drawText(e, s / 2f, s / 2f + size * 0.355f, p)
        val bb = alphaBounds(big) ?: Rect(0, 0, 1, 1)
        val k = min(1f, 0.96f * N / max(bb.width(), bb.height()))
        val out = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888)
        val w = bb.width() * k; val h = bb.height() * k
        val dst = RectF((N - w) / 2, (N - h) / 2, (N + w) / 2, (N + h) / 2)
        Canvas(out).drawBitmap(big, bb, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        big.recycle()
        return G(out, dst)
    }

    private fun isFace(e: String): Boolean {
        val cp = e.codePointAt(0)
        return cp in 0x1F600..0x1F64F || cp in 0x1F910..0x1F92F || cp in 0x1F970..0x1F97A || cp == 0x1F978 || cp == 0x263A || cp == 0x1F643 || cp == 0x1F644 ||
            cp in 0x1FAE0..0x1FAE8
    }

    /** Gaya otomatis: dua wajah -> ekspresi menyatu; wajah + benda -> aksesori di kepala. */
    fun auto(a: String, b: String): Triple<String, String, Int> {
        val fa = isFace(a); val fb = isFace(b)
        return when {
            fa && !fb -> Triple(a, b, S_HEAD)
            !fa && fb -> Triple(b, a, S_HEAD)
            fa && fb -> Triple(a, b, S_FEATURE)
            else -> Triple(a, b, S_SIDE)
        }
    }

    private val pf = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** Gambar emoji dengan pusat kotak batas di (tx, ty), skala s. */
    private fun put(cv: Canvas, g: G, tx: Float, ty: Float, s: Float, rot: Float = 0f, mirror: Boolean = false, alpha: Int = 255) {
        cv.save()
        cv.translate(tx, ty); cv.rotate(rot); cv.scale(if (mirror) -s else s, s)
        pf.alpha = alpha
        cv.drawBitmap(g.bmp, -g.box.centerX(), -g.box.centerY(), pf)
        pf.alpha = 255
        cv.restore()
    }

    private fun softShadow(cv: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, alpha: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(alpha, 0, 0, 0); maskFilter = BlurMaskFilter(max(rx, ry) * 0.35f, BlurMaskFilter.Blur.NORMAL) }
        cv.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
    }

    /** Potong ke tepi gambar lalu masukkan ke kanvas N x N dengan margin. */
    private fun trimFit(src: Bitmap): Bitmap {
        val bb = alphaBounds(src) ?: return Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888)
        val k = 0.94f * N / max(bb.width(), bb.height())
        val out = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888)
        val w = bb.width() * k; val h = bb.height() * k
        Canvas(out).drawBitmap(src, bb, RectF((N - w) / 2, (N - h) / 2, (N + w) / 2, (N + h) / 2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Skala untuk membuat lebar kotak batas B sama dengan A (agar proporsi terlihat serasi). */
    private fun match(a: G, b: G): Float = a.box.width() / max(1f, b.box.width())

    fun compose(a: G, b: G, o: Opt): Bitmap {
        val work = Bitmap.createBitmap(W, W, Bitmap.Config.ARGB_8888)
        val cv = Canvas(work)
        val c = W / 2f
        val aw = a.box.width(); val ah = a.box.height()
        when (o.style) {
            S_HEAD -> {
                val sA = 0.95f
                val acy = c + 70f
                put(cv, a, c, acy, sA)
                val atop = acy - ah * sA / 2
                val sB = min(1.3f, aw * sA * 0.52f / max(1f, b.box.width())) * o.scaleB
                val bh = b.box.height() * sB
                val bx = c + o.dx
                val by = atop - bh * 0.5f + bh * 0.30f + o.dy
                softShadow(cv, bx, atop + bh * 0.05f, b.box.width() * sB * 0.38f, bh * 0.10f, 70)
                put(cv, b, bx, by, sB, -8f + o.rot, o.mirror)
            }
            S_FEATURE -> {
                put(cv, a, c, c, 1f)
                val sB = match(a, b) * o.scaleB.coerceIn(0.8f, 1.2f)
                val layer = Bitmap.createBitmap(W, W, Bitmap.Config.ARGB_8888)
                val lc = Canvas(layer)
                put(lc, b, c + o.dx, c + o.dy, sB, o.rot, o.mirror)
                val fx = c + o.dx
                val fy = c + o.dy + b.box.height() * sB * 0.08f
                val rad = b.box.width() * sB * 0.38f
                val m = Paint().apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    shader = RadialGradient(fx, fy, rad, intArrayOf(-1, -1, 0), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                }
                lc.drawRect(0f, 0f, W.toFloat(), W.toFloat(), m)
                cv.drawBitmap(layer, 0f, 0f, null)
            }
            S_SPLIT -> {
                put(cv, a, c, c, 1f)
                val sB = match(a, b)
                val layer = Bitmap.createBitmap(W, W, Bitmap.Config.ARGB_8888)
                val lc = Canvas(layer)
                put(lc, b, c + o.dx, c + o.dy, sB * o.scaleB, o.rot, o.mirror)
                val m = Paint().apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    shader = LinearGradient(c - aw * 0.10f, c - ah * 0.12f, c + aw * 0.10f, c + ah * 0.12f, 0x00FFFFFF, -1, Shader.TileMode.CLAMP)
                }
                lc.drawRect(0f, 0f, W.toFloat(), W.toFloat(), m)
                cv.drawBitmap(layer, 0f, 0f, null)
            }
            S_SIDE -> {
                val s = 0.8f
                put(cv, a, c - aw * s * 0.46f, c, s)
                put(cv, b, c + b.box.width() * s * 0.46f + o.dx, c + o.dy, s * o.scaleB, o.rot, o.mirror)
            }
            S_CORNER -> {
                put(cv, a, c, c, 1f)
                val sB = 0.5f * o.scaleB
                val bx = c + aw * 0.34f + o.dx; val by = c + ah * 0.34f + o.dy
                softShadow(cv, bx, by + b.box.height() * sB * 0.45f, b.box.width() * sB * 0.45f, b.box.height() * sB * 0.12f, 80)
                put(cv, b, bx, by, sB, o.rot, o.mirror)
            }
            else -> {
                put(cv, a, c, c, 1f)
                put(cv, b, c + o.dx, c + o.dy, match(a, b) * 0.95f * o.scaleB, o.rot, o.mirror, 150)
            }
        }
        val res = trimFit(work)
        work.recycle()
        return res
    }

    /** Masukkan gambar apa pun (mis. hasil server AI) ke kanvas N x N. */
    fun fitSquare(src: Bitmap): Bitmap {
        val k = min(W.toFloat() / src.width, W.toFloat() / src.height)
        val w = src.width * k; val h = src.height * k
        val tmp = Bitmap.createBitmap(W, W, Bitmap.Config.ARGB_8888)
        Canvas(tmp).drawBitmap(src, null, RectF((W - w) / 2, (W - h) / 2, (W + w) / 2, (W + h) / 2), pf)
        val r = trimFit(tmp); tmp.recycle(); return r
    }

    /** Terapkan kata kunci sederhana dari kolom "Jelaskan perubahan" (hanya mode demo). */
    fun applyText(o: Opt, text: String): Opt {
        val t = text.lowercase()
        if ("besar" in t) o.scaleB *= 1.3f
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
        if ("ekspresi" in t || "wajah" in t) o.style = S_FEATURE
        return o
    }
}

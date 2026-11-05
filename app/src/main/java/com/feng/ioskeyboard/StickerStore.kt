package com.feng.ioskeyboard

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object StickerStore {
    fun dir(c: Context): File = File(c.filesDir, "stickers").apply { mkdirs() }

    fun list(c: Context): List<File> =
        dir(c).listFiles { f -> f.extension == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun save(c: Context, bmp: Bitmap): File {
        val f = File(dir(c), "st_${System.nanoTime()}.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    fun uri(c: Context, f: File): Uri = FileProvider.getUriForFile(c, "${c.packageName}.files", f)

    /** Ubah gambar apa saja jadi stiker 512x512 PNG transparan (opsional potong membulat + teks). */
    fun make(c: Context, src: Uri, caption: String, round: Boolean): Bitmap? {
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, opt) }
        if (opt.outWidth <= 0) return null
        var ss = 1
        while (opt.outWidth / ss > 1400 || opt.outHeight / ss > 1400) ss *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = ss }
        val b = c.contentResolver.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
        val out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val k = minOf(512f / b.width, 512f / b.height)
        val w = b.width * k; val h = b.height * k
        val l = (512 - w) / 2; val t = (512 - h) / 2
        val dst = RectF(l, t, l + w, t + h)
        if (round) {
            val p = Path(); p.addRoundRect(dst, minOf(w, h) * 0.18f, minOf(w, h) * 0.18f, Path.Direction.CW)
            cv.save(); cv.clipPath(p)
        }
        cv.drawBitmap(b, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        if (round) cv.restore()
        if (caption.isNotBlank()) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 64f
            }
            val stroke = Paint(fill).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 10f; strokeJoin = Paint.Join.ROUND }
            while (fill.measureText(caption) > 480f && fill.textSize > 20f) { fill.textSize -= 2f; stroke.textSize = fill.textSize }
            cv.drawText(caption, 256f, 490f, stroke); cv.drawText(caption, 256f, 490f, fill)
        }
        return out
    }
}

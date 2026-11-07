package com.feng.ioskeyboard

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/** Penyimpanan emoji buatan (hasil gabungan). */
object GenStore {
    fun dir(c: Context): File = File(c.filesDir, "genmoji").apply { mkdirs() }
    fun shareDir(c: Context): File = File(c.filesDir, "gshare").apply { mkdirs() }

    fun list(c: Context): List<File> =
        dir(c).listFiles { f -> f.extension == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun save(c: Context, bmp: Bitmap): File {
        val f = File(dir(c), "g_${System.nanoTime()}.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    fun clear(c: Context) { dir(c).listFiles()?.forEach { it.delete() } }
}

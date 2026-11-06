package com.feng.ioskeyboard

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Riwayat clipboard lokal: 0 = teks biasa, 1 = gambar, 2 = teks panjang (prompt). */
object ClipStore {
    const val T_TEXT = 0; const val T_IMAGE = 1; const val T_LONG = 2
    private const val LONG_LEN = 90

    class Item(val id: Long, val type: Int, val value: String, var pinned: Boolean, val ts: Long)

    private var items = ArrayList<Item>()
    private var loaded = false
    private var lastSig = ""

    private fun file(c: Context) = File(c.filesDir, "clip.json")
    fun dir(c: Context): File = File(c.filesDir, "clip").apply { mkdirs() }

    @Synchronized private fun ensure(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(c); if (!f.exists()) return
            val a = JSONArray(f.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                items.add(Item(o.getLong("id"), o.getInt("t"), o.getString("v"), o.optBoolean("p"), o.optLong("ts")))
            }
        } catch (_: Exception) {}
    }

    @Synchronized private fun save(c: Context) {
        try {
            val a = JSONArray()
            for (it in items) a.put(JSONObject().put("id", it.id).put("t", it.type).put("v", it.value).put("p", it.pinned).put("ts", it.ts))
            file(c).writeText(a.toString())
        } catch (_: Exception) {}
    }

    @Synchronized fun list(c: Context, type: Int): List<Item> {
        ensure(c)
        return items.filter { it.type == type }.sortedWith(compareByDescending<Item> { it.pinned }.thenByDescending { it.ts })
    }

    fun imageFile(c: Context, it: Item) = File(dir(c), it.value)

    private fun prune(c: Context) {
        for (t in 0..2) {
            val un = items.filter { it.type == t && !it.pinned }.sortedByDescending { it.ts }
            for (x in un.drop(40)) remove(c, x, false)
        }
    }

    private fun remove(c: Context, x: Item, persist: Boolean = true) {
        items.remove(x)
        if (x.type == T_IMAGE) try { imageFile(c, x).delete() } catch (_: Exception) {}
        if (persist) save(c)
    }

    @Synchronized fun addText(c: Context, text: String): Boolean {
        ensure(c)
        val t = text.trim()
        if (t.isEmpty()) return false
        val sig = "T" + t.hashCode()
        if (sig == lastSig) return false
        lastSig = sig
        val type = if (t.length > LONG_LEN || t.count { it == '\n' } >= 2) T_LONG else T_TEXT
        val old = items.firstOrNull { it.type == type && it.value == t }
        val now = System.currentTimeMillis()
        if (old != null) { items.remove(old); items.add(Item(now, type, t, old.pinned, now)) }
        else items.add(Item(now, type, t, false, now))
        prune(c); save(c); return true
    }

    @Synchronized fun addImage(c: Context, uri: Uri): Boolean {
        ensure(c)
        val sig = "I$uri"
        if (sig == lastSig) return false
        try {
            val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opt) }
            if (opt.outWidth <= 0) return false
            var ss = 1
            while (opt.outWidth / ss > 1600 || opt.outHeight / ss > 1600) ss *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = ss }
            val b = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return false
            val now = System.currentTimeMillis()
            val name = "c_$now.png"
            FileOutputStream(File(dir(c), name)).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            lastSig = sig
            items.add(Item(now, T_IMAGE, name, false, now))
            prune(c); save(c); return true
        } catch (_: Exception) { return false }
    }

    /** Dipanggil saat clipboard berubah. true bila ada item baru. */
    fun capture(c: Context, clip: ClipData?): Boolean {
        if (clip == null || clip.itemCount == 0) return false
        val item = clip.getItemAt(0)
        val d = clip.description
        return try {
            if (d.hasMimeType("image/*") && item.uri != null) addImage(c, item.uri)
            else addText(c, item.coerceToText(c)?.toString() ?: "")
        } catch (_: Exception) { false }
    }

    @Synchronized fun togglePin(c: Context, id: Long) {
        ensure(c); items.firstOrNull { it.id == id }?.let { it.pinned = !it.pinned; save(c) }
    }

    @Synchronized fun delete(c: Context, id: Long) {
        ensure(c); items.firstOrNull { it.id == id }?.let { remove(c, it) }
    }

    @Synchronized fun clearUnpinned(c: Context) {
        ensure(c)
        for (x in items.filter { !it.pinned }) remove(c, x, false)
        save(c)
    }
}

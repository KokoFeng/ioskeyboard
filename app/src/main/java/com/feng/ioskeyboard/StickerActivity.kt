package com.feng.ioskeyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

/** Buat stiker dari galeri, atau terima gambar yang dibagikan (Share) dari WhatsApp/Telegram/dll. */
class StickerActivity : Activity() {
    private var src: Uri? = null
    private var preview: Bitmap? = null
    private var caption = ""
    private var round = false
    private lateinit var img: ImageView
    private lateinit var info: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (handleShare(intent)) return
        val pal = palette()
        val ui = Screen(this, pal)
        ui.title("Stiker", "Buat stiker dari gambar apa saja")

        ui.section("Gambar")
        img = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(dp(12f).toInt(), dp(12f).toInt(), dp(12f).toInt(), dp(12f).toInt()) }
        val frame = FrameLayout(this)
        frame.addView(img, FrameLayout.LayoutParams(-1, dp(260f).toInt()))
        ui.rawCard(frame)
        ui.addRaw(android.widget.Space(this).apply { minimumHeight = dp(14f).toInt() }, 0f)
        ui.card(ui.navRow("Pilih dari galeri") { pick() })

        ui.section("Pengaturan")
        ui.card(
            ui.inputRow("Teks pada stiker (opsional)") { caption = it; rebuild() },
            ui.toggleRow("Potong membulat", false) { round = it; rebuild() }
        )
        ui.primaryButton("Simpan stiker") { save() }
        info = ui.footer("Tersimpan: ${StickerStore.list(this).size} stiker. Dari WhatsApp atau Telegram: tekan lama gambar stiker, pilih Bagikan, lalu pilih aplikasi ini.")
        ui.show()
    }

    @Suppress("DEPRECATION")
    private fun handleShare(i: Intent?): Boolean {
        if (i == null) return false
        val uris = ArrayList<Uri>()
        if (i.action == Intent.ACTION_SEND) i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
        if (i.action == Intent.ACTION_SEND_MULTIPLE) i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
        if (uris.isEmpty()) return false
        var n = 0
        for (u in uris) {
            try { StickerStore.make(this, u, "", false)?.let { StickerStore.save(this, it); n++ } } catch (_: Exception) {}
        }
        Toast.makeText(this, "$n stiker disimpan", Toast.LENGTH_SHORT).show()
        finish(); return true
    }

    private fun pick() {
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), 7)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(r: Int, res: Int, d: Intent?) {
        if (r == 7 && res == RESULT_OK) { src = d?.data; rebuild() }
    }

    private fun rebuild() {
        val s = src ?: return
        preview = try { StickerStore.make(this, s, caption, round) } catch (_: Exception) { null }
        img.setImageBitmap(preview)
    }

    private fun save() {
        val p = preview ?: run { Toast.makeText(this, "Pilih gambar dulu", Toast.LENGTH_SHORT).show(); return }
        StickerStore.save(this, p)
        info.text = "Tersimpan: ${StickerStore.list(this).size} stiker."
        Toast.makeText(this, "Stiker disimpan", Toast.LENGTH_SHORT).show()
    }
}

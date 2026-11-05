package com.feng.ioskeyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.*

/** Buat stiker dari galeri, atau terima gambar yang dibagikan (Share) dari WhatsApp/Telegram/dll. */
class StickerActivity : Activity() {
    private var src: Uri? = null
    private var preview: Bitmap? = null
    private lateinit var img: ImageView
    private lateinit var cap: EditText
    private lateinit var round: CheckBox
    private lateinit var info: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (handleShare(intent)) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#191919")); setPadding(40, 60, 40, 40)
        }
        fun lp(h: Int = -2) = LinearLayout.LayoutParams(-1, h).apply { topMargin = 24 }
        root.addView(TextView(this).apply { text = "Buat / simpan stiker"; textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        root.addView(Button(this).apply { text = "Pilih gambar dari galeri"; setOnClickListener { pick() } }, lp())
        img = ImageView(this).apply { setBackgroundColor(Color.parseColor("#2A2A2A")); scaleType = ImageView.ScaleType.FIT_CENTER }
        root.addView(img, lp(700))
        cap = EditText(this).apply {
            hint = "Teks stiker (opsional)"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { rebuild() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            })
        }
        root.addView(cap, lp())
        round = CheckBox(this).apply { text = "Potong membulat"; setTextColor(Color.WHITE); setOnCheckedChangeListener { _, _ -> rebuild() } }
        root.addView(round, lp())
        root.addView(Button(this).apply { text = "Simpan stiker"; setOnClickListener { save() } }, lp())
        info = TextView(this).apply { setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; text = "Tersimpan: ${StickerStore.list(this@StickerActivity).size} stiker.\nTips: dari WhatsApp/Telegram, tekan lama gambar stiker > Bagikan > pilih aplikasi ini." }
        root.addView(info, lp())
        setContentView(ScrollView(this).apply { addView(root); setBackgroundColor(Color.parseColor("#191919")) })
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

    override fun onActivityResult(r: Int, res: Int, d: Intent?) {
        if (r == 7 && res == RESULT_OK) { src = d?.data; rebuild() }
    }

    private fun rebuild() {
        val s = src ?: return
        preview = try { StickerStore.make(this, s, cap.text.toString(), round.isChecked) } catch (_: Exception) { null }
        img.setImageBitmap(preview)
    }

    private fun save() {
        val p = preview ?: run { Toast.makeText(this, "Pilih gambar dulu", Toast.LENGTH_SHORT).show(); return }
        StickerStore.save(this, p)
        info.text = "Tersimpan: ${StickerStore.list(this).size} stiker."
        Toast.makeText(this, "Stiker disimpan", Toast.LENGTH_SHORT).show()
    }
}

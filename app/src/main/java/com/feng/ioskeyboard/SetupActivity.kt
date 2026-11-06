package com.feng.ioskeyboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class SetupActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pf = getSharedPreferences("kb", Context.MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#191919")); setPadding(60, 60, 60, 80)
        }
        fun lp() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 28 }
        fun label(t: String) = root.addView(TextView(this).apply { text = t; setTextColor(Color.WHITE) }, lp())
        fun btn(t: String, a: () -> Unit) = root.addView(Button(this).apply { text = t; setOnClickListener { a() } }, lp())
        fun sw(t: String, key: String, def: Boolean = true) = root.addView(Switch(this).apply {
            text = t; setTextColor(Color.WHITE); isChecked = pf.getBoolean(key, def)
            setOnCheckedChangeListener { _, v -> pf.edit().putBoolean(key, v).apply() }
        }, lp())
        // seekbar dengan rentang min..max, nilai disimpan sebagai int
        fun bar(t: String, key: String, min: Int, max: Int, def: Int) {
            val tv = TextView(this).apply { setTextColor(Color.WHITE) }
            fun show(v: Int) { tv.text = "$t: $v" }
            val cur = pf.getInt(key, def); show(cur)
            root.addView(tv, lp())
            root.addView(SeekBar(this).apply {
                this.max = max - min; progress = cur - min
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { pf.edit().putInt(key, min + p).apply(); show(min + p) }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }, LinearLayout.LayoutParams(-1, -2))
        }

        root.addView(TextView(this).apply { text = "iOS Keyboard"; textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        btn("1. Aktifkan keyboard") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        btn("2. Pilih keyboard") { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        btn("3. Izinkan mikrofon") { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        btn("4. Buat / kelola stiker") { startActivity(Intent(this, StickerActivity::class.java)) }

        label("Tema keyboard")
        root.addView(RadioGroup(this).apply {
            val names = arrayOf("Gelap", "Terang (iOS 27)", "Ikut sistem")
            for (i in names.indices) addView(RadioButton(context).apply { id = 100 + i; text = names[i]; setTextColor(Color.WHITE) })
            check(100 + pf.getInt("theme", 0))
            setOnCheckedChangeListener { _, id -> pf.edit().putInt("theme", id - 100).apply() }
        }, lp())

        sw("Suara ketik", "sound"); sw("Getar saat mengetik", "haptic")
        sw("Koreksi otomatis", "autocorrect")
        sw("Pencarian emoji", "emoji_search")
        sw("Bar bawah ikut warna keyboard", "nav_follow")
        bar("Tinggi kolom pencarian emoji (%)", "search_h", 70, 120, 100)
        bar("Tinggi keyboard (%)", "kb_h", 85, 110, 100)
        bar("Posisi ikon globe & mic (negatif = naik, positif = turun)", "icon_y", -60, 60, 0)
        bar("Ukuran ikon globe & mic (%)", "icon_sz", 60, 140, 100)
        label("Perubahan berlaku saat keyboard dibuka lagi.")
        setContentView(ScrollView(this).apply { addView(root); setBackgroundColor(Color.parseColor("#191919")) })
    }
}

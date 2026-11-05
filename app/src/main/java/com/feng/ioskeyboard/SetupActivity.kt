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
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class SetupActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#191919")); setPadding(60, 60, 60, 60)
        }
        fun btn(t: String, a: () -> Unit) = Button(this).apply {
            text = t; setOnClickListener { a() }
            root.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 30 })
        }
        root.addView(TextView(this).apply { text = "iOS Keyboard"; textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        btn("1. Aktifkan keyboard") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        btn("2. Pilih keyboard") { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        btn("3. Izinkan mikrofon") { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        btn("4. Buat / kelola stiker") { startActivity(Intent(this, StickerActivity::class.java)) }
        val pf = getSharedPreferences("kb", Context.MODE_PRIVATE)
        fun sw(t: String, key: String) = Switch(this).apply {
            text = t; setTextColor(Color.WHITE); isChecked = pf.getBoolean(key, true)
            setOnCheckedChangeListener { _, v -> pf.edit().putBoolean(key, v).apply() }
            root.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 30 })
        }
        sw("Suara ketik", "sound"); sw("Getar saat mengetik", "haptic")
        sw("Koreksi otomatis", "autocorrect")
        sw("Pencarian emoji", "emoji_search")
        sw("Bar bawah ikut warna keyboard", "nav_follow")
        root.addView(TextView(this).apply { text = "Tinggi kolom pencarian emoji"; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 30 })
        root.addView(SeekBar(this).apply {
            max = 50; progress = pf.getInt("search_h", 100) - 70
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { pf.edit().putInt("search_h", 70 + p).apply() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(-1, -2))
        setContentView(ScrollView(this).apply { addView(root); setBackgroundColor(Color.parseColor("#191919")) })
    }
}

package com.feng.ioskeyboard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.TextView

class SetupActivity : Activity() {
    private lateinit var vEnable: TextView
    private lateinit var vPick: TextView
    private lateinit var vMic: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pal = palette()
        val ui = Screen(this, pal)
        val pf = getSharedPreferences("kb", Context.MODE_PRIVATE)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        ui.title("Keyboard", "iOS Keyboard untuk Android")

        ui.section("Mulai")
        vEnable = ui.valueText(""); vPick = ui.valueText(""); vMic = ui.valueText("")
        ui.card(
            ui.navRow("Aktifkan keyboard", vEnable) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
            ui.navRow("Pilih keyboard", vPick) { imm.showInputMethodPicker() },
            ui.navRow("Izinkan mikrofon", vMic) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) },
            ui.navRow("Stiker") { startActivity(Intent(this, StickerActivity::class.java)) }
        )

        ui.section("Tampilan")
        ui.card(
            ui.segmentRow("Tema", arrayOf("Gelap", "Terang", "Sistem"), pf.getInt("theme", 0)) { pf.edit().putInt("theme", it).apply() },
            ui.sliderRow("Tinggi keyboard", pf, "kb_h", 85, 110, 100, "%"),
            ui.sliderRow("Tinggi kolom pencarian emoji", pf, "search_h", 70, 120, 100, "%"),
            ui.sliderRow("Posisi ikon globe & mic", pf, "icon_y", -60, 60, 0),
            ui.sliderRow("Ukuran ikon globe & mic", pf, "icon_sz", 60, 140, 100, "%")
        )
        ui.footer("Posisi ikon: nilai negatif menaikkan, positif menurunkan. Perubahan berlaku saat keyboard dibuka lagi.")

        ui.section("Mengetik")
        ui.card(
            ui.switchRow("Suara ketik", pf, "sound"),
            ui.switchRow("Getar saat mengetik", pf, "haptic"),
            ui.switchRow("Koreksi otomatis", pf, "autocorrect"),
            ui.switchRow("Pencarian emoji", pf, "emoji_search"),
            ui.switchRow("Bar bawah ikut warna keyboard", pf, "nav_follow")
        )

        ui.section("Clipboard")
        ui.card(
            ui.switchRow("Simpan riwayat clipboard", pf, "clip_on"),
            ui.actionRow("Hapus riwayat", pal.red) {
                AlertDialog.Builder(this)
                    .setTitle("Hapus riwayat clipboard?")
                    .setMessage("Item yang disematkan tidak ikut dihapus.")
                    .setPositiveButton("Hapus") { _, _ -> ClipStore.clearUnpinned(this) }
                    .setNegativeButton("Batal", null).show()
            }
        )
        ui.footer("Riwayat clipboard dan kata yang kamu ketik hanya disimpan di perangkat ini.")
        ui.show()
    }

    override fun onResume() {
        super.onResume()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS) ?: ""
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        vEnable.text = if (enabled.contains(packageName)) "Aktif" else "Belum"
        vPick.text = if (current.contains(packageName)) "Dipakai" else ""
        vMic.text = if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "Diizinkan" else "Belum"
    }
}

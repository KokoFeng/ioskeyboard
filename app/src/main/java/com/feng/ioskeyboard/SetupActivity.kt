package com.feng.ioskeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle

class SetupActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pf = getSharedPreferences("kb", Context.MODE_PRIVATE)
        // pertama kali dibuka: tampilkan setup animasi
        if (!pf.getBoolean("onboarded", false)) {
            startActivity(Intent(this, OnboardActivity::class.java)); finish(); return
        }
        val pal = palette()
        val ui = Screen(this, pal)

        val name = pf.getString("user_name", "") ?: ""
        val showHeader = pf.getInt("name_show", 0).let { it == 2 || it == 3 }
        ui.title("Keyboard", if (showHeader && name.isNotBlank()) "Halo, $name" else "iOS Keyboard untuk Android")

        ui.section("Profil")
        ui.card(
            ui.inputRow("Nama kamu", name) { pf.edit().putString("user_name", it.trim()).apply() },
            ui.segmentRow("Tampilkan nama di", arrayOf("Tidak", "Spasi", "Header", "Keduanya"), pf.getInt("name_show", 0)) { pf.edit().putInt("name_show", it).apply() }
        )
        ui.footer("Nama muncul di tombol spasi keyboard dan/atau di bagian atas halaman ini.")

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

        ui.section("Alat")
        ui.card(
            ui.navRow("Stiker") { startActivity(Intent(this, StickerActivity::class.java)) },
            ui.navRow("Ulangi setup awal") { pf.edit().putBoolean("onboarded", false).apply(); startActivity(Intent(this, OnboardActivity::class.java)); finish() }
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

        ui.section("Emoji buatan")
        ui.card(
            ui.actionRow("Hapus semua emoji buatan", pal.red) {
                AlertDialog.Builder(this)
                    .setTitle("Hapus semua emoji buatan?")
                    .setMessage("Emoji gabungan yang disimpan di tab Terbaru akan dihapus. Stiker tidak ikut dihapus.")
                    .setPositiveButton("Hapus") { _, _ -> GenStore.clear(this) }
                    .setNegativeButton("Batal", null).show()
            }
        )
        ui.footer("Buat emoji gabungan: ketik dua emoji lalu ketuk Gabungkan, atau pakai tombol Gabung di panel emoji.")
        ui.show()
    }
}

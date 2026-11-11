package com.feng.ioskeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

class SetupActivity : Activity() {
    private var builtEn = false
    private var blocked = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Lang.load(this)
        if (Guard.blockIfTampered(this)) { blocked = true; return }
        builtEn = Lang.en
        val pf = getSharedPreferences("kb", Context.MODE_PRIVATE)
        // pertama kali dibuka: tampilkan setup animasi
        if (!pf.getBoolean("onboarded", false)) {
            startActivity(Intent(this, OnboardActivity::class.java)); finish(); return
        }
        val pal = palette()
        val ui = Screen(this, pal)

        val name = pf.getString("user_name", "") ?: ""
        val showHeader = pf.getInt("name_show", 0).let { it == 2 || it == 3 }
        ui.title("Keyboard", if (showHeader && name.isNotBlank()) Lang.tr("Halo, %s").format(name) else Lang.tr("KeyOS untuk Android"))

        ui.section(Lang.tr("Profil"))
        ui.card(
            ui.inputRow(Lang.tr("Nama kamu"), name) { pf.edit().putString("user_name", it.trim()).apply() },
            ui.segmentRow(Lang.tr("Tampilkan nama di"), arrayOf(Lang.tr("Tidak"), Lang.tr("Spasi"), "Header", Lang.tr("Keduanya")), pf.getInt("name_show", 0)) { pf.edit().putInt("name_show", it).apply() }
        )
        ui.card(
            ui.sliderRow(Lang.tr("Kemiringan huruf nama"), pf, "name_skew", -30, 30, 0, "°"),
            ui.sliderRow(Lang.tr("Putar nama di spasi"), pf, "name_rot", -30, 30, 0, "°")
        )
        ui.footer(Lang.tr("Nama muncul di tombol spasi dan/atau di bagian atas halaman ini. Kemiringan huruf: negatif miring ke kiri, positif miring ke kanan (seperti huruf miring). Putar: mengangkat sisi kanan atau kiri."))

        ui.section(Lang.tr("Tampilan"))
        ui.card(
            ui.segmentRow(Lang.tr("Tema"), arrayOf(Lang.tr("Gelap"), Lang.tr("Terang"), Lang.tr("Sistem")), pf.getInt("theme", 0)) { pf.edit().putInt("theme", it).apply() },
            ui.segmentRow(Lang.tr("Gaya font"), arrayOf(Lang.tr("Reguler"), "Bold"), pf.getInt("font_style", 0)) { pf.edit().putInt("font_style", it).apply(); window.decorView.post { recreate() } },
            ui.segmentRow(Lang.tr("Ikon kiri bawah"), arrayOf("Globe", "Emoji"), pf.getInt("left_icon", 0)) { pf.edit().putInt("left_icon", it).apply() },
            ui.sliderRow(Lang.tr("Tinggi keyboard"), pf, "kb_h", 85, 110, 100, "%"),
            ui.sliderRow(Lang.tr("Tinggi saran teks"), pf, "sug_h", 70, 140, 100, "%"),
            ui.sliderRow(Lang.tr("Tinggi kolom pencarian emoji"), pf, "search_h", 70, 120, 100, "%"),
            ui.sliderRow(Lang.tr("Posisi ikon globe & mic"), pf, "icon_y", -60, 60, 0),
            ui.sliderRow(Lang.tr("Ukuran ikon globe & mic"), pf, "icon_sz", 60, 140, 100, "%")
        )
        ui.footer(Lang.tr("Posisi ikon: nilai negatif menaikkan, positif menurunkan. Perubahan berlaku saat keyboard dibuka lagi."))

        ui.section(Lang.tr("Mengetik"))
        ui.card(
            ui.switchRow(Lang.tr("Suara ketik"), pf, "sound"),
            ui.switchRow(Lang.tr("Getar saat mengetik"), pf, "haptic"),
            ui.switchRow(Lang.tr("Koreksi otomatis"), pf, "autocorrect"),
            ui.switchRow(Lang.tr("Pencarian emoji"), pf, "emoji_search"),
            ui.switchRow(Lang.tr("Bar bawah ikut warna keyboard"), pf, "nav_follow")
        )

        ui.section(Lang.tr("Bahasa keyboard"))
        ui.card(*Array(Langs.NAMES.size) { i ->
            ui.toggleRow(Langs.NAMES[i], (Langs.mask(this) and (1 shl i)) != 0) { on ->
                var m = Langs.mask(this)
                m = if (on) m or (1 shl i) else m and (1 shl i).inv()
                pf.edit().putInt("lang_mask", if (m and Langs.ALL == 0) Langs.ALL else m and Langs.ALL).apply()
            }
        })
        ui.footer(Lang.tr("Ketuk globe untuk berganti bahasa ketikan. Tekan lama globe untuk memilih langsung."))

        ui.section(Lang.tr("Bahasa"))
        ui.card(
            ui.navRow(Lang.tr("Bahasa"), ui.valueText(if (Lang.en) "English" else "Indonesia")) { startActivity(Intent(this, LanguageActivity::class.java)) }
        )

        ui.section(Lang.tr("Alat"))
        ui.card(
            ui.navRow(Lang.tr("Stiker")) { startActivity(Intent(this, StickerActivity::class.java)) },
            ui.navRow(Lang.tr("Ulangi setup awal")) { pf.edit().putBoolean("onboarded", false).apply(); startActivity(Intent(this, OnboardActivity::class.java)); finish() }
        )

        ui.section("Clipboard")
        ui.card(
            ui.switchRow(Lang.tr("Simpan riwayat clipboard"), pf, "clip_on"),
            ui.actionRow(Lang.tr("Hapus riwayat"), pal.red) {
                AlertDialog.Builder(this)
                    .setTitle(Lang.tr("Hapus riwayat clipboard?"))
                    .setMessage(Lang.tr("Item yang disematkan tidak ikut dihapus."))
                    .setPositiveButton(Lang.tr("Hapus")) { _, _ -> ClipStore.clearUnpinned(this) }
                    .setNegativeButton(Lang.tr("Batal"), null).show()
            }
        )
        ui.footer(Lang.tr("Riwayat clipboard dan kata yang kamu ketik hanya disimpan di perangkat ini."))

        ui.section(Lang.tr("Emoji buatan"))
        ui.card(
            ui.actionRow(Lang.tr("Hapus semua emoji buatan"), pal.red) {
                AlertDialog.Builder(this)
                    .setTitle(Lang.tr("Hapus semua emoji buatan?"))
                    .setMessage(Lang.tr("Emoji gabungan yang disimpan di tab Terbaru akan dihapus. Stiker tidak ikut dihapus."))
                    .setPositiveButton(Lang.tr("Hapus")) { _, _ -> GenStore.clear(this) }
                    .setNegativeButton(Lang.tr("Batal"), null).show()
            }
        )
        ui.footer(Lang.tr("Buat emoji gabungan: ketik dua emoji lalu ketuk Gabungkan, atau pakai tombol Gabung di panel emoji."))

        ui.section(Lang.tr("Dukungan"))
        ui.card(ui.navRow(SupportConfig.LABEL) { openSupport() })
        ui.show()
    }

    private fun openSupport() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SupportConfig.URL))) }
        catch (_: Exception) { android.widget.Toast.makeText(this, Lang.tr("Tidak bisa membuka tautan"), android.widget.Toast.LENGTH_SHORT).show() }
    }

    override fun onResume() {
        super.onResume()
        // bahasa diganti dari layar Bahasa -> muat ulang tampilan
        if (!blocked && builtEn != Lang.en) recreate()
    }
}

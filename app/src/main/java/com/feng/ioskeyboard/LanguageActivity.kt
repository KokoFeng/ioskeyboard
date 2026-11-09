package com.feng.ioskeyboard

import android.app.Activity
import android.os.Bundle

/** Pilihan bahasa aplikasi ala Pengaturan iOS (English / Indonesia). */
class LanguageActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Lang.load(this)
        if (Guard.blockIfTampered(this)) return
        build()
    }

    private fun build() {
        val ui = Screen(this, palette())
        ui.title(Lang.tr("Bahasa"))
        ui.section(Lang.tr("Pilih bahasa"))
        ui.card(
            ui.checkRow("English", "English", Lang.en) { pick(1) },
            ui.checkRow("Indonesia", "Bahasa Indonesia", !Lang.en) { pick(0) }
        )
        ui.footer(Lang.tr("Bahasa berlaku untuk aplikasi dan tampilan keyboard."))
        ui.show()
    }

    private fun pick(i: Int) {
        if ((i == 1) == Lang.en) return
        Lang.set(this, i)
        build()
        window.decorView.alpha = 0.35f
        window.decorView.animate().alpha(1f).setDuration(260).start()
    }
}

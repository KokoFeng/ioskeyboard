package com.feng.ioskeyboard

import android.app.Activity
import android.graphics.Color
import android.os.Bundle

/** Popup error ala iOS yang berjalan di proses terpisah (tetap tampil walau proses utama berhenti). */
class CrashActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Lang.load(this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.argb(120, 0, 0, 0)))
        val code = intent.getStringExtra("code") ?: "KO-000000"
        val detail = intent.getStringExtra("detail") ?: ""
        CrashReporter.markShown(this)
        setContentView(CrashReporter.alertView(this, code, detail) { finish() })
    }
}

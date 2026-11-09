package com.feng.ioskeyboard

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast

/** Activity transparan hanya untuk meminta izin mikrofon; langsung menutup diri. */
class MicActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Lang.load(this)
        if (Guard.tampered(this)) { finish(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) finish()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        if (r.isNotEmpty() && r[0] == PackageManager.PERMISSION_GRANTED)
            Toast.makeText(this, Lang.tr("Izin diberikan. Ketuk mikrofon lagi."), Toast.LENGTH_SHORT).show()
        finish()
    }
}

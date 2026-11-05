package com.feng.ioskeyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

/**
 * Suara ketik. File dicari di assets/sounds/: standard, delete, spacebar, ret
 * (urutan ekstensi: .ogg, .wav, .mp3). Ganti file di folder itu dengan suara asli iOS milik lu.
 */
class KeySounds(private val ctx: Context) {
    companion object { const val STD = 0; const val DEL = 1; const val SPACE = 2; const val RET = 3 }
    private val names = arrayOf("standard", "delete", "spacebar", "ret")
    private val ids = IntArray(4)
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = ctx.getSharedPreferences("kb", Context.MODE_PRIVATE)
    private val sp = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()

    init { for (i in names.indices) ids[i] = load(names[i]) }

    private fun load(n: String): Int {
        for (ext in arrayOf("ogg", "wav", "mp3")) {
            try {
                val fd = ctx.assets.openFd("sounds/$n.$ext")
                val id = sp.load(fd, 1); fd.close(); return id
            } catch (_: Exception) {}
        }
        return 0
    }

    fun play(type: Int) {
        if (!prefs.getBoolean("sound", true) || am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = ids[type]; if (id == 0) return
        val v = 0.8f
        sp.play(id, v, v, 1, 0, 1f)
    }

    fun release() { sp.release() }
}

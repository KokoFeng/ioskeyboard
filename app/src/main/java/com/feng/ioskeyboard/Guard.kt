package com.feng.ioskeyboard

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.security.MessageDigest

/**
 * Pemeriksa keaslian: membandingkan sidik jari (SHA-256) sertifikat penanda tangan APK
 * dengan nilai yang ditanam saat build. APK hasil modifikasi/tanda tangan ulang akan ditolak.
 * Catatan: ini penghalang, bukan perlindungan mutlak (orang yang ahli bisa menghapus pemeriksaan ini).
 */
object Guard {
    private var cached: Boolean? = null

    private fun sha256(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    fun tampered(ctx: Context): Boolean {
        cached?.let { return it }
        val r = try {
            val pm = ctx.packageManager
            val sigs: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
                val si = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                if (si == null) null else if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val expected = BuildConfig.SIG_SHA256.lowercase()
            if (sigs == null || sigs.isEmpty()) true else sigs.none { sha256(it.toByteArray()) == expected }
        } catch (_: Exception) { false } // gagal membaca = jangan memblokir pengguna asli
        cached = r
        return r
    }

    /** true = aplikasi diblokir (popup sudah ditampilkan), hentikan onCreate. */
    fun blockIfTampered(act: Activity): Boolean {
        if (!tampered(act)) return false
        act.window.statusBarColor = Color.BLACK; act.window.navigationBarColor = Color.BLACK
        act.setContentView(blockedView(act, true) { act.finishAffinity() })
        return true
    }

    /** Popup ala iOS (bahasa Inggris). */
    fun blockedView(ctx: Context, full: Boolean, onOk: (() -> Unit)?): View {
        val pal = ctx.palette()
        val root = FrameLayout(ctx).apply { setBackgroundColor(if (full) Color.BLACK else (if (pal.dark) 0xFF111113.toInt() else 0xFF8E8E93.toInt())) }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(if (pal.dark) 0xFF2C2C2E.toInt() else 0xFFF2F2F7.toInt()); cornerRadius = ctx.dp(14f) }
            clipToOutline = true
        }
        val sep = if (pal.dark) 0xFF48484A.toInt() else 0xFFC6C6C8.toInt()
        card.addView(TextView(ctx).apply {
            text = "App Modified"; textSize = 17f; setTextColor(pal.text); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(ctx.dp(20f).toInt(), ctx.dp(20f).toInt(), ctx.dp(20f).toInt(), ctx.dp(4f).toInt())
        })
        card.addView(TextView(ctx).apply {
            text = "This app has been modified and can no longer be opened. Please install the original version."
            textSize = 13f; setTextColor(pal.text); gravity = Gravity.CENTER; setLineSpacing(0f, 1.1f)
            setPadding(ctx.dp(20f).toInt(), 0, ctx.dp(20f).toInt(), ctx.dp(20f).toInt())
        })
        card.addView(View(ctx).apply { setBackgroundColor(sep) }, LinearLayout.LayoutParams(-1, 1))
        card.addView(TextView(ctx).apply {
            text = "OK"; textSize = 17f; setTextColor(pal.accent); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, ctx.dp(13f).toInt(), 0, ctx.dp(13f).toInt())
            setOnClickListener { onOk?.invoke() }
        })
        root.addView(card, FrameLayout.LayoutParams(ctx.dp(270f).toInt(), -2, Gravity.CENTER))
        card.alpha = 0f; card.scaleX = 1.12f; card.scaleY = 1.12f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(DecelerateInterpolator()).start()
        return root
    }
}

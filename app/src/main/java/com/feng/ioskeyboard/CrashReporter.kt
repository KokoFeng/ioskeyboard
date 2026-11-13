package com.feng.ioskeyboard

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.ref.WeakReference
import kotlin.system.exitProcess

/**
 * Pencatat error otomatis. Setiap error disimpan ke file lokal (crash_log.txt / last_crash.txt)
 * dan ditampilkan sebagai popup ala iOS berisi kode kesalahan, yang bisa ditutup pengguna.
 * Error di thread utama ditangkap agar aplikasi/keyboard tidak langsung tertutup.
 */
object CrashReporter {
    private var top: WeakReference<Activity>? = null
    private var recent = ArrayList<Long>()
    private var lastCode = ""
    private var lastShownAt = 0L

    class Info(val code: String, val detail: String, val full: String)

    private fun dir(c: Context) = c.filesDir
    private fun lastFile(c: Context) = File(dir(c), "last_crash.txt")
    private fun logFile(c: Context) = File(dir(c), "crash_log.txt")
    private fun summaryFile(c: Context) = File(dir(c), "last_summary.txt")
    private fun unseenFile(c: Context) = File(dir(c), "crash_unseen")

    private fun root(t: Throwable): Throwable { var r = t; var n = 0; while (r.cause != null && n++ < 10) r = r.cause!!; return r }

    fun build(t: Throwable, thread: String): Info {
        val r = root(t)
        val frame = r.stackTrace.firstOrNull { it.className.startsWith("com.feng.ioskeyboard") } ?: r.stackTrace.firstOrNull()
        val where = if (frame != null) "${frame.fileName}:${frame.lineNumber}" else "?"
        val code = "KO-" + String.format("%06X", (r.javaClass.name + where).hashCode() and 0xFFFFFF)
        val msg = (r.message ?: "").replace('\n', ' ').take(140)
        val detail = r.javaClass.simpleName + (if (msg.isNotEmpty()) ": $msg" else "") + "\n" + where
        val sw = StringWriter(); t.printStackTrace(PrintWriter(sw))
        val full = "KeyOS error $code\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "${Build.MANUFACTURER} ${Build.MODEL}\nABI: ${Build.SUPPORTED_ABIS.joinToString()}\nThread: $thread\n\n$sw"
        return Info(code, detail, full)
    }

    /** Simpan otomatis. */
    fun save(c: Context, i: Info) {
        try {
            lastFile(c).writeText(i.full)
            summaryFile(c).writeText(i.code + "\n" + i.detail)
            unseenFile(c).writeText("1")
            val log = logFile(c)
            val old = if (log.exists()) log.readText() else ""
            log.writeText((i.full + "\n\n----------------------------------------\n\n" + old).take(40000))
        } catch (_: Throwable) {}
    }

    private fun markSeen(c: Context) { try { unseenFile(c).delete() } catch (_: Throwable) {} }

    private fun isCrashProcess(): Boolean =
        try { File("/proc/self/cmdline").readText().trim('\u0000').endsWith(":crash") } catch (_: Throwable) { false }

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { top = WeakReference(a) }
            override fun onActivityPaused(a: Activity) { if (top?.get() === a) top = null }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        // error fatal (thread lain / berulang): simpan, tampilkan popup di proses terpisah, lalu keluar
        Thread.setDefaultUncaughtExceptionHandler { t, e -> fatal(app, t, e) }
        // error di thread utama: tangkap supaya aplikasi dan keyboard tetap hidup
        if (!isCrashProcess()) {
            Handler(Looper.getMainLooper()).post {
                while (true) {
                    try { Looper.loop(); return@post } catch (e: Throwable) { onMainError(app, e) }
                }
            }
        }
    }

    private fun fatal(app: Application, t: Thread, e: Throwable) {
        val info = try { build(e, t.name) } catch (_: Throwable) { Info("KO-000000", e.toString(), e.toString()) }
        save(app, info)
        if (!isCrashProcess()) {
            try {
                app.startActivity(Intent(app, CrashActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("code", info.code).putExtra("detail", info.detail))
                Thread.sleep(400)
            } catch (_: Throwable) {}
        }
        Process.killProcess(Process.myPid())
        exitProcess(10)
    }

    private fun onMainError(app: Application, e: Throwable) {
        val info = build(e, "main")
        save(app, info)
        val now = System.currentTimeMillis()
        recent.add(now); recent.removeAll { now - it > 10000L }
        if (recent.size > 6) { fatal(app, Thread.currentThread(), e); return }   // error terus berulang
        if (info.code == lastCode && now - lastShownAt < 15000L) return
        lastCode = info.code; lastShownAt = now
        val a = top?.get()
        if (a != null && !a.isFinishing) a.runOnUiThread { showPopup(a, info.code, info.detail) }
        else try {
            app.startActivity(Intent(app, CrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("code", info.code).putExtra("detail", info.detail))
        } catch (_: Throwable) {}
    }

    /** Popup ala iOS: judul, kode kesalahan, tombol Tutup. */
    fun alertView(ctx: Context, code: String, detail: String, onClose: () -> Unit): View {
        val pal = ctx.palette()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(if (pal.dark) 0xFF2C2C2E.toInt() else 0xFFF2F2F7.toInt()); cornerRadius = ctx.dp(14f) }
            clipToOutline = true
        }
        val pad = ctx.dp(18f).toInt()
        card.addView(TextView(ctx).apply {
            text = Lang.tr("Terjadi kesalahan"); textSize = 17f; setTextColor(pal.text); gravity = Gravity.CENTER
            typeface = Fonts.medium(ctx); setPadding(pad, ctx.dp(20f).toInt(), pad, ctx.dp(4f).toInt())
        })
        card.addView(TextView(ctx).apply {
            text = Lang.tr("KeyOS mengalami error. Laporan sudah tersimpan otomatis."); textSize = 13f; setTextColor(pal.text); gravity = Gravity.CENTER
            setLineSpacing(0f, 1.1f); setPadding(pad, 0, pad, ctx.dp(12f).toInt())
        })
        card.addView(TextView(ctx).apply {
            text = "$code\n$detail"; textSize = 11f; typeface = Typeface.MONOSPACE; setTextColor(pal.sub); gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(if (pal.dark) 0xFF1C1C1E.toInt() else 0xFFE5E5EA.toInt()); cornerRadius = ctx.dp(8f) }
            setPadding(ctx.dp(10f).toInt(), ctx.dp(8f).toInt(), ctx.dp(10f).toInt(), ctx.dp(8f).toInt()); maxLines = 6
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(pad, 0, pad, ctx.dp(16f).toInt()) })
        card.addView(View(ctx).apply { setBackgroundColor(if (pal.dark) 0xFF48484A.toInt() else 0xFFC6C6C8.toInt()) }, LinearLayout.LayoutParams(-1, 1))
        card.addView(TextView(ctx).apply {
            text = Lang.tr("Tutup"); textSize = 17f; setTextColor(pal.accent); gravity = Gravity.CENTER; typeface = Fonts.medium(ctx)
            setPadding(0, ctx.dp(13f).toInt(), 0, ctx.dp(13f).toInt()); setOnClickListener { onClose() }
        })
        card.alpha = 0f; card.scaleX = 1.12f; card.scaleY = 1.12f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(DecelerateInterpolator()).start()
        val wrap = FrameLayout(ctx)
        wrap.addView(card, FrameLayout.LayoutParams(ctx.dp(290f).toInt(), -2, Gravity.CENTER))
        return wrap
    }

    fun showPopup(act: Activity, code: String, detail: String) {
        try {
            val d = Dialog(act)
            d.requestWindowFeature(Window.FEATURE_NO_TITLE)
            d.setContentView(alertView(act, code, detail) { d.dismiss() })
            d.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); setDimAmount(0.45f)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            d.setCancelable(true)
            d.show()
            markSeen(act)
        } catch (_: Throwable) {}
    }

    /** Error sebelumnya yang popup-nya belum sempat tampil: tampilkan saat aplikasi dibuka lagi. */
    fun showIfAny(act: Activity) {
        try {
            if (!unseenFile(act).exists()) return
            val s = summaryFile(act).takeIf { it.exists() }?.readText()?.split("\n", limit = 2) ?: return
            showPopup(act, s[0], s.getOrElse(1) { "" })
        } catch (_: Throwable) {}
    }

    fun markShown(c: Context) = markSeen(c)
}

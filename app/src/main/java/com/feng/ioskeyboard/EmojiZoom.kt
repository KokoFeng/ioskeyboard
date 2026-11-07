package com.feng.ioskeyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.PopupWindow
import kotlin.math.max
import kotlin.math.min

/**
 * Pratinjau emoji besar saat ditekan-tahan. Memakai jendela popup terpisah, jadi bisa keluar dari batas keyboard.
 * Bisa di-zoom (dikendalikan dari luar) dan menghilang dengan fade out.
 */
class EmojiZoom(private val ctx: Context) {
    private class ZoomView(c: Context) : View(c) {
        var text: String? = null
        var bmp: Bitmap? = null
        var face: Typeface? = null
        private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { textAlign = Paint.Align.CENTER }
        override fun onDraw(c: Canvas) {
            val s = min(width, height).toFloat()
            bmp?.let { c.drawBitmap(it, null, android.graphics.RectF(s * 0.06f, s * 0.06f, s * 0.94f, s * 0.94f), p); return }
            val t = text ?: return
            p.typeface = face; p.textSize = s * 0.74f
            c.drawText(t, width / 2f, height / 2f + p.textSize * 0.355f, p)
        }
    }

    private var pw: PopupWindow? = null
    private var zv: ZoomView? = null
    private var fade: ValueAnimator? = null
    private val base = (ctx.resources.displayMetrics.widthPixels * 0.34f).toInt()
    var zoom = 1f
        private set

    val isShowing get() = pw?.isShowing == true

    private fun sizePx() = (base * zoom).toInt()

    fun show(anchor: View, text: String?, bmp: Bitmap?, face: Typeface?, screenX: Int, screenY: Int) {
        hideNow()
        zoom = 1f
        try {
            val v = ZoomView(ctx).apply { this.text = text; this.bmp = bmp; this.face = face }
            val w = PopupWindow(v, sizePx(), sizePx(), false)
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.isTouchable = false; w.isFocusable = false; w.isClippingEnabled = false
            zv = v; pw = w
            val (x, y) = place(anchor, screenX, screenY)
            w.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
            v.alpha = 0f; v.scaleX = 0.7f; v.scaleY = 0.7f
            v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(DecelerateInterpolator()).start()
        } catch (_: Exception) { pw = null; zv = null }
    }

    /** Posisi popup relatif terhadap jendela keyboard, di atas jari. */
    private fun place(anchor: View, sx: Int, sy: Int): Pair<Int, Int> {
        val loc = IntArray(2); anchor.rootView.getLocationOnScreen(loc)
        val dm = ctx.resources.displayMetrics
        val s = sizePx()
        var left = sx - s / 2
        left = max(-s / 4, min(dm.widthPixels - s * 3 / 4, left))
        var top = sy - s - (30 * dm.density).toInt()
        top = max(0, top)
        return Pair(left - loc[0], top - loc[1])
    }

    fun update(anchor: View, text: String?, bmp: Bitmap?, face: Typeface?, screenX: Int, screenY: Int) {
        val w = pw ?: return; val v = zv ?: return
        if (v.text != text || v.bmp != bmp) { v.text = text; v.bmp = bmp; v.face = face; v.invalidate() }
        try { val (x, y) = place(anchor, screenX, screenY); w.update(x, y, sizePx(), sizePx()) } catch (_: Exception) {}
    }

    fun setZoom(z: Float, anchor: View, screenX: Int, screenY: Int) {
        zoom = max(0.6f, min(3.4f, z))
        val w = pw ?: return
        try { val (x, y) = place(anchor, screenX, screenY); w.update(x, y, sizePx(), sizePx()) } catch (_: Exception) {}
    }

    /** Lepas jari: fade out lalu tutup. */
    fun hide() {
        val v = zv ?: return
        fade?.cancel()
        fade = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 240
            addUpdateListener { v.alpha = it.animatedValue as Float; val s = 1f + 0.08f * (1f - it.animatedValue as Float); v.scaleX = s; v.scaleY = s }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { hideNow() }
            })
            start()
        }
    }

    fun hideNow() {
        fade?.cancel(); fade = null
        try { pw?.dismiss() } catch (_: Exception) {}
        pw = null; zv = null
    }
}

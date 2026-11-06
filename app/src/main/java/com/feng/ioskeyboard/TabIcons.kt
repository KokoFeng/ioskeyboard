package com.feng.ioskeyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ikon kategori emoji ala iOS, digambar sebagai vektor (tetap tajam di resolusi berapa pun, termasuk 4K).
 * Urutan: 0 stiker, 1 terbaru, 2 wajah, 3 hewan, 4 makanan, 5 olahraga, 6 perjalanan, 7 benda, 8 simbol, 9 bendera.
 */
object TabIcons {
    private val p = Path()
    private val r = RectF()

    private fun circle(c: Canvas, x: Float, y: Float, rad: Float, paint: Paint) = c.drawCircle(x, y, rad, paint)

    fun draw(c: Canvas, idx: Int, cx: Float, cy: Float, stroke: Paint, fill: Paint) {
        val oldW = stroke.strokeWidth
        stroke.strokeWidth = 3.2f
        c.save(); c.translate(cx, cy); c.scale(1.1f, 1.1f)
        when (idx) {
            0 -> { // stiker: lingkaran dengan sudut terkelupas
                p.reset(); r.set(-17f, -17f, 17f, 17f)
                p.arcTo(r, 75f, 295f, true)
                p.quadTo(5f, 5f, 4.4f, 16.4f)
                c.drawPath(p, stroke)
            }
            1 -> { // terbaru: jam
                circle(c, 0f, 0f, 17f, stroke)
                p.reset(); p.moveTo(-4f, -11f); p.lineTo(-4f, 3f); p.lineTo(8f, 3f)
                c.drawPath(p, stroke)
            }
            2 -> { // wajah tersenyum
                circle(c, 0f, 0f, 17f, stroke)
                r.set(-8f, -9f, -4f, -2f); c.drawOval(r, fill)
                r.set(4f, -9f, 8f, -2f); c.drawOval(r, fill)
                p.reset(); p.moveTo(-9f, 2f); p.lineTo(9f, 2f); p.quadTo(9f, 12f, 0f, 12f); p.quadTo(-9f, 12f, -9f, 2f); p.close()
                c.drawPath(p, fill)
            }
            3 -> { // anjing
                c.save(); c.rotate(18f, -13f, -3f); r.set(-18f, -12f, -8f, 6f); c.drawOval(r, fill); c.restore()
                c.save(); c.rotate(-18f, 13f, -3f); r.set(8f, -12f, 18f, 6f); c.drawOval(r, fill); c.restore()
                circle(c, 0f, 3f, 12.5f, stroke)
                circle(c, -5f, 0f, 1.9f, fill)
                circle(c, 5.5f, 0f, 1.9f, fill)
                circle(c, 5.5f, 0f, 5.2f, stroke)
                r.set(-3.5f, 5f, 3.5f, 9f); c.drawOval(r, fill)
                r.set(-2.4f, 10f, 2.4f, 15f); c.drawRoundRect(r, 2f, 2f, fill)
            }
            4 -> { // apel
                p.reset()
                p.moveTo(0f, -7f)
                p.cubicTo(-5f, -11f, -16f, -8f, -15f, 4f)
                p.cubicTo(-14f, 13f, -7f, 20f, -3f, 18f)
                p.cubicTo(-1f, 17f, 1f, 17f, 3f, 18f)
                p.cubicTo(7f, 20f, 14f, 13f, 15f, 4f)
                p.cubicTo(16f, -8f, 5f, -11f, 0f, -7f)
                p.close(); c.drawPath(p, stroke)
                c.drawLine(0f, -7f, 2f, -15f, stroke)
                p.reset(); p.moveTo(3f, -12f); p.quadTo(8f, -19f, 13f, -14f); p.quadTo(8f, -8f, 3f, -12f); c.drawPath(p, stroke)
            }
            5 -> { // bola sepak
                circle(c, 0f, 0f, 17f, stroke)
                p.reset()
                for (i in 0 until 5) {
                    val a = Math.toRadians((-90 + 72 * i).toDouble())
                    val x = (cos(a) * 6.5).toFloat(); val y = (sin(a) * 6.5).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close(); c.drawPath(p, fill)
                for (i in 0 until 5) {
                    val a = Math.toRadians((-90 + 72 * i).toDouble())
                    c.drawLine((cos(a) * 6.5).toFloat(), (sin(a) * 6.5).toFloat(), (cos(a) * 17.0).toFloat(), (sin(a) * 17.0).toFloat(), stroke)
                }
            }
            6 -> { // mobil
                p.reset()
                p.moveTo(-19f, 8f); p.lineTo(-19f, 0f); p.quadTo(-19f, -3f, -15f, -4f); p.lineTo(-10f, -5f)
                p.lineTo(-6f, -11f); p.lineTo(6f, -11f); p.lineTo(11f, -5f); p.lineTo(16f, -4f)
                p.quadTo(19f, -3f, 19f, 0f); p.lineTo(19f, 8f); p.close()
                c.drawPath(p, stroke)
                p.reset(); p.moveTo(-6f, -5f); p.lineTo(-3.5f, -8.5f); p.lineTo(4f, -8.5f); p.lineTo(7f, -5f); p.close()
                c.drawPath(p, stroke)
                circle(c, -11f, 9f, 4.4f, fill); circle(c, 11f, 9f, 4.4f, fill)
            }
            7 -> { // bohlam
                r.set(-12f, -16f, 12f, 8f)
                p.reset(); p.arcTo(r, 125f, 290f, true); c.drawPath(p, stroke)
                c.drawLine(-6.9f, 5.8f, -5f, 12f, stroke); c.drawLine(6.9f, 5.8f, 5f, 12f, stroke)
                c.drawLine(-5f, 12f, 5f, 12f, stroke)
                c.drawLine(-4f, 16f, 4f, 16f, stroke)
                c.drawLine(-2f, 19.5f, 2f, 19.5f, stroke)
            }
            8 -> { // hati
                p.reset()
                p.moveTo(0f, 15f)
                p.cubicTo(-18f, 3f, -19f, -5f, -14f, -11f)
                p.cubicTo(-9f, -16f, -2f, -14f, 0f, -8f)
                p.cubicTo(2f, -14f, 9f, -16f, 14f, -11f)
                p.cubicTo(19f, -5f, 18f, 3f, 0f, 15f)
                p.close(); c.drawPath(p, stroke)
            }
            9 -> { // bendera
                c.drawLine(-13f, -18f, -13f, 19f, stroke)
                p.reset()
                p.moveTo(-13f, -16f); p.cubicTo(-4f, -20f, 4f, -12f, 15f, -16f)
                p.lineTo(15f, 2f); p.cubicTo(4f, 6f, -4f, -2f, -13f, 2f)
                c.drawPath(p, stroke)
            }
        }
        c.restore()
        stroke.strokeWidth = oldW
    }
}

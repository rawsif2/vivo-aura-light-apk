package com.tawsif.aura

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * Top of the iQOO Z10x / vivo T4x back panel, drawn from the product photo:
 * a tall rounded camera island in the top-left with two large lenses stacked on the left,
 * the aura ring (a softly rounded square) below them, and the small sensor and the flash
 * in the right column. The ring lights up in [ringColor]; animate [glow] (0..1) to pulse it.
 * All sizes are fractions of the body width.
 */
class PhonePreview(context: Context) : View(context) {
    var ringColor: Int = Color.RED
        set(v) { field = v; invalidate() }
    var glow: Float = 1f
        set(v) { field = v; invalidate() }
    var fadeColor: Int = Color.BLACK

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    private fun alpha(c: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    override fun onDraw(c: Canvas) {
        val vw = width.toFloat(); val vh = height.toFloat()
        val bw = minOf(vw * 0.62f, 240f * resources.displayMetrics.density)
        val left = (vw - bw) / 2f
        val top = 0.04f * bw
        val bh = bw * 2.17f                                // 165.7 x 76.3 mm body
        fun x(f: Float) = left + f * bw
        fun y(f: Float) = top + f * bw
        fun circle(cx: Float, cy: Float, rad: Float) = c.drawCircle(x(cx), y(cy), rad * bw, p)

        // ---- body (matte black) ----
        r.set(left, top, left + bw, top + bh)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, top, 0f, top + bh, 0xFF2E2F33.toInt(), 0xFF242527.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 0.11f * bw, 0.11f * bw, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.006f * bw; p.color = 0xFF404146.toInt()
        c.drawRoundRect(r, 0.11f * bw, 0.11f * bw, p)

        // ---- camera island ----
        val il = x(0.067f); val it = y(0.079f)
        val iw = 0.504f * bw; val ih = 0.697f * bw; val ic = 0.09f * bw
        val o = 0.014f * bw
        r.set(il - o, it - o, il + iw + o, it + ih + o)                       // stitched outline
        p.style = Paint.Style.STROKE; p.strokeWidth = maxOf(1f, 0.004f * bw); p.color = 0xC8787980.toInt()
        p.pathEffect = DashPathEffect(floatArrayOf(0.012f * bw, 0.012f * bw), 0f)
        c.drawRoundRect(r, ic + o, ic + o, p)
        p.pathEffect = null
        p.style = Paint.Style.FILL; p.color = 0x5A000000                       // shadow
        r.set(il, it + 0.01f * bw, il + iw, it + ih + 0.01f * bw); c.drawRoundRect(r, ic, ic, p)
        r.set(il, it, il + iw, it + ih)                                        // glass
        p.shader = LinearGradient(0f, it, 0f, it + ih, 0xFF3A3B3F.toInt(), 0xFF2B2C30.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, ic, ic, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.006f * bw; p.color = 0xFF9698A4.toInt()   // silver rim
        c.drawRoundRect(r, ic, ic, p)

        // ---- two main lenses ----
        for (cy in floatArrayOf(0.2087f, 0.431f)) {
            val rad = 0.093f; val ring = 0.011f
            p.style = Paint.Style.FILL; p.color = 0xFF050507.toInt(); circle(0.2087f, cy, rad)
            p.style = Paint.Style.STROKE; p.strokeWidth = ring * bw; p.color = 0xFFAEB0BE.toInt()
            circle(0.2087f, cy, rad - ring / 2f)
            p.style = Paint.Style.FILL; p.color = 0xFF0E121A.toInt(); circle(0.2087f, cy, 0.047f)
            p.style = Paint.Style.STROKE; p.strokeWidth = 0.004f * bw; p.color = 0xFF1E222C.toInt()
            circle(0.2087f, cy, 0.045f)
            p.style = Paint.Style.FILL; p.color = 0xFF3A86D8.toInt(); circle(0.2087f, cy, 0.012f)   // glint
        }

        // ---- sensor and flash (right column) ----
        p.style = Paint.Style.FILL; p.color = 0xFF0D0D10.toInt(); circle(0.445f, 0.2087f, 0.034f)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.004f * bw; p.color = 0xFF232428.toInt()
        circle(0.445f, 0.2087f, 0.032f)
        p.style = Paint.Style.FILL; p.color = 0xFFD6D7DA.toInt(); circle(0.445f, 0.433f, 0.0394f)
        p.style = Paint.Style.STROKE; p.color = 0xFF9698A0.toInt(); circle(0.445f, 0.433f, 0.0374f)
        p.style = Paint.Style.FILL; p.color = 0xFFECECEE.toInt(); circle(0.445f, 0.433f, 0.0217f)
        p.color = 0xFFF3D36B.toInt(); circle(0.445f, 0.433f, 0.011f)

        // ---- aura ring: rounded square below the lenses, with a soft bloom ----
        val g = glow.coerceIn(0f, 1f)
        val rcx = 0.2087f; val rcy = 0.650f; val half = 0.0866f
        val cr = 0.066f * bw; val sw = 0.014f * bw
        r.set(x(rcx - half), y(rcy - half), x(rcx + half), y(rcy + half))
        p.style = Paint.Style.STROKE
        for (i in 14 downTo 1) {
            p.strokeWidth = sw + 2f * i * 0.0075f * bw
            p.color = alpha(ringColor, (16 * g).toInt())
            c.drawRoundRect(r, cr, cr, p)
        }
        p.style = Paint.Style.FILL; p.color = 0xFF18181D.toInt()
        r.inset(-sw / 2f, -sw / 2f); c.drawRoundRect(r, cr + sw / 2f, cr + sw / 2f, p); r.inset(sw / 2f, sw / 2f)
        p.style = Paint.Style.STROKE; p.strokeWidth = sw
        p.color = 0xFFC9CADF.toInt(); c.drawRoundRect(r, cr, cr, p)                        // silver (off)
        p.color = alpha(ringColor, (255 * g).toInt()); c.drawRoundRect(r, cr, cr, p)       // lit

        // ---- fade the cropped bottom into the screen background ----
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, vh * 0.6f, 0f, vh, alpha(fadeColor, 0), fadeColor, Shader.TileMode.CLAMP)
        c.drawRect(0f, vh * 0.6f, vw, vh, p)
        p.shader = null
    }
}

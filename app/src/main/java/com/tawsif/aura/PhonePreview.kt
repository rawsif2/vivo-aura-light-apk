package com.tawsif.aura

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * Top of the iQOO Z10x / vivo T4x back panel: raised rectangular camera island in the top-left
 * with the main lens inside the aura ring, the 2 MP depth lens and the flash below it.
 * The ring glows in [ringColor]; animate [glow] (0..1) to pulse it.
 */
class PhonePreview(context: Context) : View(context) {
    var ringColor: Int = Color.RED
        set(v) { field = v; invalidate() }
    var glow: Float = 1f
        set(v) { field = v; invalidate() }
    var fadeColor: Int = Color.BLACK

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val d = context.resources.displayMetrics.density

    private fun alpha(c: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    override fun onDraw(c: Canvas) {
        val vw = width.toFloat(); val vh = height.toFloat()
        val bw = minOf(vw * 0.62f, 240f * d)
        val left = (vw - bw) / 2f
        val top = 10f * d
        val bh = bw * 2.17f                       // 165.7 x 76.3 mm body
        val corner = bw * 0.10f

        // body
        r.set(left, top, left + bw, top + bh)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, top, 0f, top + bh, 0xFF30303C.toInt(), 0xFF22222B.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, corner, corner, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = 0xFF454554.toInt()
        c.drawRoundRect(r, corner, corner, p)

        // camera island (top-left, slightly different hue)
        val iw = bw * 0.40f; val ih = iw * 1.25f
        val il = left + bw * 0.075f; val it = top + bw * 0.075f
        val ic = bw * 0.09f
        p.style = Paint.Style.FILL; p.color = 0x55000000
        r.set(il, it + 3f * d, il + iw, it + ih + 3f * d); c.drawRoundRect(r, ic, ic, p)       // shadow
        p.color = 0xFF3C3C4A.toInt()
        r.set(il, it, il + iw, it + ih); c.drawRoundRect(r, ic, ic, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = 0xFF55556A.toInt()
        c.drawRoundRect(r, ic, ic, p)

        // main lens inside the aura ring
        val cx = il + iw * 0.5f; val cy = it + iw * 0.45f
        val rr = iw * 0.36f
        val g = glow.coerceIn(0f, 1f)
        val outer = rr * 2.3f
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            cx, cy, outer,
            intArrayOf(alpha(ringColor, 0), alpha(ringColor, (170 * g).toInt()), alpha(ringColor, 0)),
            floatArrayOf(rr * 0.8f / outer, rr / outer, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, outer, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = rr * 0.24f
        p.color = 0xFF14141A.toInt(); c.drawCircle(cx, cy, rr, p)                               // ring track
        p.color = alpha(ringColor, (70 + 185 * g).toInt()); c.drawCircle(cx, cy, rr, p)         // lit ring
        p.style = Paint.Style.FILL; p.color = 0xFF08080C.toInt(); c.drawCircle(cx, cy, rr * 0.68f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = 0xFF2C2C3A.toInt()
        c.drawCircle(cx, cy, rr * 0.5f, p)
        p.style = Paint.Style.FILL; p.color = 0x33FFFFFF
        c.drawCircle(cx - rr * 0.22f, cy - rr * 0.22f, rr * 0.12f, p)                           // glass glint

        // depth lens + flash
        val ly = it + ih * 0.82f
        val dx = il + iw * 0.30f; val fx = il + iw * 0.70f
        val dr = iw * 0.11f
        p.style = Paint.Style.FILL; p.color = 0xFF08080C.toInt(); c.drawCircle(dx, ly, dr, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = 0xFF2C2C3A.toInt(); c.drawCircle(dx, ly, dr, p)
        p.style = Paint.Style.FILL; p.color = 0xFFEFE3C2.toInt(); c.drawCircle(fx, ly, iw * 0.065f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = 0xFF6A6A7A.toInt()
        c.drawCircle(fx, ly, iw * 0.065f, p)

        // fade the cropped bottom into the screen background
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, vh * 0.55f, 0f, vh, alpha(fadeColor, 0), fadeColor, Shader.TileMode.CLAMP)
        c.drawRect(0f, vh * 0.55f, vw, vh, p)
        p.shader = null
    }
}

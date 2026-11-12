package com.tawsif.aura

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

/** Rainbow (left to right) fading to white (top). Drag to pick a color. */
class ColorPlane(context: Context) : View(context) {
    var onPick: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.WHITE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f; color = Color.argb(120, 0, 0, 0) }
    private val clip = Path()
    private var hueShader: Shader? = null
    private var whiteShader: Shader? = null
    private val hsv = floatArrayOf(0f, 1f, 1f)
    private var hasMarker = false

    fun setColor(color: Int) {
        Color.colorToHSV(color, hsv)
        hsv[2] = 1f
        hasMarker = true
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val hues = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
        hueShader = LinearGradient(0f, 0f, w.toFloat(), 0f, hues, null, Shader.TileMode.CLAMP)
        whiteShader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        clip.reset()
        val r = 20f * resources.displayMetrics.density
        clip.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.save()
        canvas.clipPath(clip)
        paint.shader = hueShader; canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = whiteShader; canvas.drawRect(0f, 0f, w, h, paint)
        canvas.restore()
        paint.shader = null
        if (hasMarker) {
            val x = hsv[0] / 360f * w
            val y = hsv[1] * h
            paint.color = Color.HSVToColor(hsv)
            canvas.drawCircle(x, y, 20f, paint)
            canvas.drawCircle(x, y, 22f, shadowPaint)
            canvas.drawCircle(x, y, 20f, ringPaint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(true)
            val x = e.x.coerceIn(0f, width.toFloat())
            val y = e.y.coerceIn(0f, height.toFloat())
            hsv[0] = (x / width * 360f).coerceAtMost(359.9f)
            hsv[1] = (y / height).coerceIn(0f, 1f)
            hsv[2] = 1f
            hasMarker = true
            invalidate()
            onPick?.invoke(Color.HSVToColor(hsv))
        }
        return true
    }
}

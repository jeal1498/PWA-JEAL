package com.jeal.switchdriver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/** Burbuja circular con el ícono de la app. Dibuja estados: normal, trabajando, éxito, error, minimizada. */
class BubbleView(
    ctx: Context,
    private val icon: Bitmap?,
    private val fallback: String,
    val sizePx: Int,
) : View(ctx) {

    enum class Mode { IDLE, WORKING, OK, FAIL }

    var mode = Mode.IDLE; set(v) { field = v; invalidate() }
    private var pressedNow = false

    var onTap: () -> Unit = {}
    var onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> }
    var onDragEnd: () -> Unit = {}
    var onTouchStart: () -> Unit = {}

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f; private var downY = 0f
    private var lastX = 0f; private var lastY = 0f
    private var dragging = false

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141922.toInt(); setShadowLayer(sizePx * 0.06f, 0f, sizePx * 0.03f, 0x66000000) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sizePx * 0.07f }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = sizePx * 0.4f; isFakeBoldText = true }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sizePx * 0.09f; strokeCap = Paint.Cap.ROUND; color = Color.WHITE }
    private val clip = Path()

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(sizePx, sizePx)

    override fun onDraw(c: Canvas) {
        val s = sizePx.toFloat()
        val pad = s * 0.09f
        val scale = if (pressedNow) 0.9f else 1f
        c.save(); c.scale(scale, scale, s / 2, s / 2)
        c.drawCircle(s / 2, s / 2, s / 2 - pad, bg)
        val inset = pad + s * 0.08f
        val r = RectF(inset, inset, s - inset, s - inset)
        if (icon != null) {
            clip.reset(); clip.addCircle(s / 2, s / 2, r.width() / 2, Path.Direction.CW)
            c.save(); c.clipPath(clip); c.drawBitmap(icon, null, r, null); c.restore()
        } else c.drawText(fallback, s / 2, s / 2 + text.textSize * 0.35f, text)
        ring.color = when (mode) { Mode.IDLE -> 0xFF2EE6A6.toInt(); Mode.WORKING -> 0xFFFFC857.toInt(); Mode.OK -> 0xFF2EE6A6.toInt(); Mode.FAIL -> 0xFFFF5C5C.toInt() }
        c.drawCircle(s / 2, s / 2, s / 2 - pad, ring)
        if (mode == Mode.OK || mode == Mode.FAIL) {
            // Cinta con ✓ o ! sobre la esquina
            val cx = s * 0.78f; val cy = s * 0.24f; val rr = s * 0.17f
            val b = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (mode == Mode.OK) 0xFF2EE6A6.toInt() else 0xFFFF5C5C.toInt() }
            c.drawCircle(cx, cy, rr, b)
            if (mode == Mode.OK) { c.drawLine(cx - rr * .5f, cy, cx - rr * .1f, cy + rr * .45f, mark.apply { strokeWidth = rr * .3f; color = 0xFF0E1116.toInt() }); c.drawLine(cx - rr * .1f, cy + rr * .45f, cx + rr * .55f, cy - rr * .4f, mark) }
            else { c.drawLine(cx, cy - rr * .5f, cx, cy + rr * .1f, mark.apply { strokeWidth = rr * .3f; color = 0xFF0E1116.toInt() }); c.drawPoint(cx, cy + rr * .5f, mark) }
        }
        c.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; lastX = e.rawX; lastY = e.rawY
                dragging = false; pressedNow = true; invalidate(); onTouchStart()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (Math.abs(e.rawX - downX) > slop || Math.abs(e.rawY - downY) > slop)) dragging = true
                if (dragging) { onDrag(e.rawX - lastX, e.rawY - lastY); lastX = e.rawX; lastY = e.rawY }
            }
            MotionEvent.ACTION_UP -> {
                pressedNow = false; invalidate()
                if (dragging) onDragEnd() else onTap()
            }
            MotionEvent.ACTION_CANCEL -> { pressedNow = false; invalidate(); if (dragging) onDragEnd() }
        }
        return true
    }
}

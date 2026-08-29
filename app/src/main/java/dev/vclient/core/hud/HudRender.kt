package dev.vclient.core.hud

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint

/**
 * Shared, allocation-free painting helpers for HUD elements. All HUD modules
 * draw through these so the overlay keeps a consistent look and a tiny
 * per-frame allocation budget (the overlay redraws at display refresh rate).
 */
object HudRender {

    private val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = false }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arrowPath = Path()

    fun textWidth(text: String, sizePx: Float): Float {
        textPaint.textSize = sizePx
        return textPaint.measureText(text)
    }

    fun textHeight(sizePx: Float): Float {
        textPaint.textSize = sizePx
        return -textPaint.ascent() + textPaint.descent()
    }

    fun ascent(sizePx: Float): Float {
        textPaint.textSize = sizePx
        return textPaint.ascent()
    }

    fun descentPx(sizePx: Float): Float {
        textPaint.textSize = sizePx
        return textPaint.descent()
    }

    fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        sizePx: Float,
        color: Int,
        shadow: Boolean = false,
        bold: Boolean = false,
    ) {
        textPaint.textSize = sizePx
        textPaint.color = color
        textPaint.isFakeBoldText = bold
        if (shadow) {
            textPaint.setShadowLayer(sizePx * 0.1f, 0f, sizePx * 0.1f, 0xB2000000.toInt())
        } else {
            textPaint.clearShadowLayer()
        }
        canvas.drawText(text, x, y, textPaint)
        textPaint.clearShadowLayer()
    }

    /** Draw a line of text vertically centered inside [rect] starting at [x]. */
    fun drawTextCentered(
        canvas: Canvas,
        text: String,
        x: Float,
        rect: RectF,
        sizePx: Float,
        color: Int,
        shadow: Boolean = false,
        bold: Boolean = false,
    ) {
        textPaint.textSize = sizePx
        val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        drawText(canvas, text, x, baseline, sizePx, color, shadow, bold)
    }

    fun drawPanel(canvas: Canvas, rect: RectF, color: Int, radiusPx: Float, borderColor: Int = 0, borderWidthPx: Float = 0f) {
        fillPaint.color = color
        canvas.drawRoundRect(rect, radiusPx, radiusPx, fillPaint)
        if (borderColor != 0 && borderWidthPx > 0f) {
            strokePaint.color = borderColor
            strokePaint.strokeWidth = borderWidthPx
            canvas.drawRoundRect(rect, radiusPx, radiusPx, strokePaint)
        }
    }

    fun drawRoundedBar(canvas: Canvas, rect: RectF, color: Int, radiusPx: Float) {
        fillPaint.color = color
        canvas.drawRoundRect(rect, radiusPx, radiusPx, fillPaint)
    }

    /** Direction arrow (points up at rotation 0) used by the Waypoints module. */
    fun drawArrow(canvas: Canvas, cx: Float, cy: Float, radius: Float, rotationDeg: Float, color: Int) {
        canvas.save()
        canvas.rotate(rotationDeg, cx, cy)
        arrowPath.reset()
        arrowPath.moveTo(cx, cy - radius)
        arrowPath.lineTo(cx + radius * 0.8f, cy + radius)
        arrowPath.lineTo(cx - radius * 0.8f, cy + radius)
        arrowPath.close()
        fillPaint.color = color
        canvas.drawPath(arrowPath, fillPaint)
        canvas.restore()
    }

    fun drawCircle(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, stroke: Boolean = false, strokeWidth: Float = 2f) {
        if (stroke) {
            strokePaint.color = color
            strokePaint.strokeWidth = strokeWidth
            canvas.drawCircle(cx, cy, radius, strokePaint)
        } else {
            fillPaint.color = color
            canvas.drawCircle(cx, cy, radius, fillPaint)
        }
    }

    fun drawCrossLine(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = width
        strokePaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x1, y1, x2, y2, strokePaint)
    }

    /** Applies alpha (0..255) to an ARGB color. */
    fun withAlpha(argb: Int, alpha: Int): Int =
        (argb and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)

    /** Blends [argb] toward the screen by transparency factor [opacity] (0..1). */
    fun opacity(argb: Int, opacity: Float): Int = withAlpha(argb, (Color.alpha(argb) * opacity).toInt())
}

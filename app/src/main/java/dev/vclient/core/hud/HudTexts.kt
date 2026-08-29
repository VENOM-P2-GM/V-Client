package dev.vclient.core.hud

import android.graphics.Canvas
import android.graphics.RectF
import android.text.TextPaint
import android.util.SizeF

/**
 * Shared layout for simple "text panel" HUD modules: measures a list of lines
 * at a given text size and draws them inside the module panel. Uses one shared
 * TextPaint — the overlay redraws at refresh rate, so per-frame allocations
 * are kept at zero.
 */
object HudTexts {

    const val PAD_DP = 5f
    const val LINE_SPACING_DP = 2f

    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)

    fun measureSized(frame: HudFrame, lines: List<String>, textSizePx: Float): SizeF {
        if (lines.isEmpty()) return SizeF(0f, 0f)
        val pad = PAD_DP * frame.density
        val spacing = LINE_SPACING_DP * frame.density
        val lineH = HudRender.textHeight(textSizePx)
        val w = lines.maxOf { HudRender.textWidth(it, textSizePx) } + pad * 2
        val h = lines.size * lineH + (lines.size - 1) * spacing + pad * 2
        return SizeF(w, h)
    }

    fun draw(
        canvas: Canvas,
        frame: HudFrame,
        rect: RectF,
        lines: List<String>,
        textSizePx: Float,
        textColor: Int,
        shadow: Boolean,
    ) = drawColored(canvas, frame, rect, lines.map { it to textColor }, textSizePx, shadow)

    fun drawColored(
        canvas: Canvas,
        frame: HudFrame,
        rect: RectF,
        lines: List<Pair<String, Int>>,
        textSizePx: Float,
        shadow: Boolean,
    ) {
        if (lines.isEmpty()) return
        val pad = PAD_DP * frame.density
        val spacing = LINE_SPACING_DP * frame.density
        val lineH = HudRender.textHeight(textSizePx)
        var baseline = rect.top + pad - HudRender.ascent(textSizePx)
        for ((line, color) in lines) {
            paint.textSize = textSizePx
            paint.color = color
            if (shadow) paint.setShadowLayer(textSizePx * 0.1f, 0f, textSizePx * 0.1f, 0xB2000000.toInt())
            else paint.clearShadowLayer()
            canvas.drawText(line, rect.left + pad, baseline, paint)
            baseline += lineH + spacing
        }
        paint.clearShadowLayer()
    }
}

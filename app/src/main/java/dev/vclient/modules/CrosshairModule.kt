package dev.vclient.modules

import android.graphics.Canvas
import android.graphics.RectF
import android.util.SizeF
import dev.vclient.core.hud.HudAnchor
import dev.vclient.core.hud.HudElement
import dev.vclient.core.hud.HudFrame
import dev.vclient.core.hud.HudRender
import dev.vclient.core.module.ModuleCategory
import dev.vclient.core.module.HudModule
import dev.vclient.core.settings.BoolSetting
import dev.vclient.core.settings.ColorSetting
import dev.vclient.core.settings.FloatSetting
import dev.vclient.core.settings.IntSetting
import dev.vclient.core.settings.ModeSetting
import dev.vclient.core.settings.settingOf

/** Crosshair — custom crosshair rendered over the game's default position. */
class CrosshairModule : HudModule(
    ID, NAME,
    "Custom crosshair with several styles, drawn over the game.",
    ModuleCategory.VISUAL,
) {

    private val style by settingOf(
        ModeSetting("style", "Style", listOf("Cross", "Cross + Dot", "Dot", "Circle", "Circle + Dot"), "Cross + Dot")
    )
    private val size by settingOf(IntSetting("size", "Arm length", 12, 4, 40, 1, "dp"))
    private val thickness by settingOf(IntSetting("thickness", "Thickness", 2, 1, 8, 1, "dp"))
    private val gap by settingOf(IntSetting("gap", "Center gap", 4, 0, 24, 1, "dp"))
    private val dotSize by settingOf(IntSetting("dot_size", "Dot size", 3, 1, 12, 1, "dp"))
    private val color by settingOf(ColorSetting("color", "Color", 0xFF22D3EE.toInt()))
    private val outline by settingOf(BoolSetting("outline", "Outline", true, "Dark outline for visibility."))
    private val opacity by settingOf(FloatSetting("opacity", "Opacity", 1.0f, 0.2f, 1.0f, 0.05f, "", 2))

    override val hud = Element()

    private inner class Element : HudElement(ID) {
        init {
            anchor = HudAnchor.CENTER
            offsetX = 0f
            offsetY = 0f
        }

        override fun measure(frame: HudFrame): SizeF {
            val d = frame.density
            val extent = (size + gap) * d * 2f + thickness * d
            return SizeF(extent, extent)
        }

        override fun draw(canvas: Canvas, frame: HudFrame, rect: RectF) {
            val d = frame.density
            val cx = rect.centerX()
            val cy = rect.centerY()
            val armPx = size * d
            val gapPx = gap * d
            val th = thickness * d
            val main = HudRender.opacity(color, opacity)
            val dark = HudRender.opacity(0xFF000000.toInt(), opacity * 0.65f)

            val hasCross = style == "Cross" || style == "Cross + Dot"
            val hasDot = style == "Dot" || style == "Cross + Dot" || style == "Circle + Dot"
            val hasCircle = style == "Circle" || style == "Circle + Dot"

            fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
                if (outline) HudRender.drawCrossLine(canvas, x1, y1, x2, y2, dark, th + 2f * d)
                HudRender.drawCrossLine(canvas, x1, y1, x2, y2, main, th)
            }

            if (hasCross) {
                line(cx - gapPx - armPx, cy, cx - gapPx, cy) // left
                line(cx + gapPx, cy, cx + gapPx + armPx, cy) // right
                line(cx, cy - gapPx - armPx, cx, cy - gapPx) // top
                line(cx, cy + gapPx, cx, cy + gapPx + armPx) // bottom
            }
            if (hasCircle) {
                val radius = armPx + gapPx
                if (outline) HudRender.drawCircle(canvas, cx, cy, radius, dark, stroke = true, strokeWidth = th + 2f * d)
                HudRender.drawCircle(canvas, cx, cy, radius, main, stroke = true, strokeWidth = th)
            }
            if (hasDot) {
                val r = dotSize * d / 2f
                if (outline) HudRender.drawCircle(canvas, cx, cy, r + d, dark)
                HudRender.drawCircle(canvas, cx, cy, r, main)
            }
        }
    }

    companion object {
        const val ID = "crosshair"
        const val NAME = "Crosshair"
    }
}

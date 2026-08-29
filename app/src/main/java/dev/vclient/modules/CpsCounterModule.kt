package dev.vclient.modules

import android.graphics.Canvas
import android.graphics.RectF
import android.util.SizeF
import dev.vclient.core.hud.HudElement
import dev.vclient.core.hud.HudFrame
import dev.vclient.core.hud.HudTexts
import dev.vclient.core.module.HudModule
import dev.vclient.core.settings.BoolSetting
import dev.vclient.core.settings.ModeSetting
import java.util.Locale

/**
 * CPS Counter — clicks per second. Real data comes from touchscreen taps
 * observed through the optional accessibility service (Android 14+); without
 * it, taps on V Client's own surfaces (bubble, V Menu, pad) are counted.
 */
class CpsCounterModule : HudModule(ID, NAME, "Counts your clicks per second (left/right/both).") {

    private val mode by settingOf(
        ModeSetting("mode", "Buttons", listOf("Left", "Right", "Both"), "Both")
    )
    private val showPeak by settingOf(BoolSetting("show_peak", "Show peak", false, "Session peak CPS."))

    override val hud: HudElement = Element()

    private inner class Element : HudElement(ID) {

        override fun measure(frame: HudFrame): SizeF =
            HudTexts.measureSized(frame, lines(frame), textSizePx(frame.density))

        override fun draw(canvas: Canvas, frame: HudFrame, rect: RectF) {
            drawPanel(canvas, frame, rect)
            val size = textSizePx(frame.density)
            HudTexts.drawColored(
                canvas, frame, rect,
                lines(frame).map { it to if (it == "CPS") accentValue() else textColorValue() },
                size, textShadowEnabled(),
            )
        }

        private fun lines(frame: HudFrame): List<String> {
            val input = frame.input
            val out = mutableListOf<String>()
            when (mode) {
                "Left" -> out.add(line("L", input.cpsLeft, input.peakCpsLeft))
                "Right" -> out.add(line("R", input.cpsRight, input.peakCpsRight))
                else -> {
                    val both = input.cpsLeft + input.cpsRight
                    out.add(String.format(Locale.US, "CPS %.0f", both))
                    if (showPeak) {
                        out.add(String.format(Locale.US, "Peak %.0f", maxOf(input.peakCpsLeft, input.peakCpsRight)))
                    }
                    out.add(String.format(Locale.US, "L %.0f  R %.0f", input.cpsLeft, input.cpsRight))
                }
            }
            return out
        }

        private fun line(label: String, cps: Double, peak: Double): String = buildString {
            append(label).append(' ').append(String.format(Locale.US, "%.0f", cps))
            if (showPeak) append("  peak ").append(String.format(Locale.US, "%.0f", peak))
        }
    }

    companion object {
        const val ID = "cps_counter"
        const val NAME = "CPS Counter"
    }
}

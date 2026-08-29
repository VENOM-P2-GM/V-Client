package dev.vclient.modules

import android.graphics.Canvas
import android.graphics.RectF
import android.util.SizeF
import dev.vclient.core.hud.HudElement
import dev.vclient.core.hud.HudFrame
import dev.vclient.core.hud.HudTexts
import dev.vclient.core.module.HudModule
import dev.vclient.core.settings.BoolSetting
import dev.vclient.core.settings.settingOf
import java.util.Locale

/**
 * FPS Counter — real statistics from the overlay render loop (native FpsStats
 * when the runtime is available, Kotlin fallback otherwise).
 */
class FpsCounterModule : HudModule(ID, NAME, "Live FPS and frame-time statistics of the render loop.") {

    private val showFrameTime by settingOf(
        BoolSetting("show_frame_time", "Frame time", true, "Average frame duration in ms.")
    )
    private val showOnePctLow by settingOf(
        BoolSetting("show_1pct_low", "1% low frame time", false, "Worst 1% frame times — stutter indicator.")
    )
    private val colorByFps by settingOf(
        BoolSetting("color_by_fps", "Color by FPS", true, "Green ≥ 55, amber ≥ 30, red below.")
    )

    override val hud = Element()

    private inner class Element : HudElement(ID) {

        override fun measure(frame: HudFrame): SizeF =
            HudTexts.measureSized(frame, lines(frame), textSizePx(frame.density))

        override fun draw(canvas: Canvas, frame: HudFrame, rect: RectF) {
            drawPanel(canvas, frame, rect)
            val size = textSizePx(frame.density)
            val ls = lines(frame)
            val color = when {
                !colorByFps -> textColorValue()
                (frame.stats?.fps ?: 0.0) >= 55 -> 0xFF34D399.toInt()
                (frame.stats?.fps ?: 0.0) >= 30 -> 0xFFFBBF24.toInt()
                else -> 0xFFF87171.toInt()
            }
            HudTexts.drawColored(
                canvas, frame, rect,
                ls.map { it to if (it.endsWith("FPS")) color else textColorValue() },
                size, textShadowEnabled(),
            )
        }

        private fun lines(frame: HudFrame): List<String> {
            val fps = frame.stats?.fps?.toInt() ?: 0
            val out = mutableListOf<String>()
            out.add("$fps FPS")
            if (showFrameTime) out.add(String.format(Locale.US, "%.1f ms", frame.stats?.avgFrameMs ?: 0.0))
            if (showOnePctLow) out.add(String.format(Locale.US, "1%% low  %.1f ms", frame.stats?.p99FrameMs ?: 0.0))
            return out
        }
    }

    companion object {
        const val ID = "fps_counter"
        const val NAME = "FPS Counter"
    }
}

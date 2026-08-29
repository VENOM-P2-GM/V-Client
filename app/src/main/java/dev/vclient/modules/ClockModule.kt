package dev.vclient.modules

import android.graphics.Canvas
import android.graphics.RectF
import android.util.SizeF
import dev.vclient.core.hud.HudElement
import dev.vclient.core.hud.HudFrame
import dev.vclient.core.hud.HudTexts
import dev.vclient.core.module.HudModule
import dev.vclient.core.settings.BoolSetting
import dev.vclient.core.settings.TextSetting
import dev.vclient.core.settings.settingOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Clock — configurable device clock rendered on the HUD. */
class ClockModule : HudModule(ID, NAME, "Device clock with optional seconds and date.") {

    private val use24h by settingOf(BoolSetting("use_24h", "24-hour clock", true))
    private val showSeconds by settingOf(BoolSetting("show_seconds", "Seconds", false))
    private val showDate by settingOf(BoolSetting("show_date", "Date", false))
    private val prefix by settingOf(TextSetting("prefix", "Prefix", "", "Optional text before the time."))

    override val hud = Element()

    private inner class Element : HudElement(ID) {

        private val timeFmt12 = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        private val timeFmt24 = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

        override fun measure(frame: HudFrame): SizeF =
            HudTexts.measureSized(frame, lines(frame), textSizePx(frame.density))

        override fun draw(canvas: Canvas, frame: HudFrame, rect: RectF) {
            drawPanel(canvas, frame, rect)
            HudTexts.drawColored(
                canvas, frame, rect,
                lines(frame).map { it to textColorValue() },
                textSizePx(frame.density), textShadowEnabled(),
            )
        }

        private fun lines(frame: HudFrame): List<String> {
            val time = Instant.ofEpochMilli(frame.timeMs).atZone(ZoneId.systemDefault())
            val out = mutableListOf<String>()
            val prefixText = prefix.trim()
            val base = if (use24h) time.format(timeFmt24) else time.format(timeFmt12).uppercase(Locale.ROOT)
            val clock = buildString {
                if (prefixText.isNotEmpty()) append(prefixText).append(' ')
                append(base)
                if (showSeconds) append(String.format(Locale.US, ":%02d", time.second))
            }
            out.add(clock)
            if (showDate) out.add(time.format(dateFmt))
            return out
        }
    }

    companion object {
        const val ID = "clock"
        const val NAME = "Clock"
    }
}

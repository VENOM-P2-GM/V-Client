package dev.vclient.modules

import android.graphics.Canvas
import android.graphics.RectF
import android.util.SizeF
import dev.vclient.core.hud.HudElement
import dev.vclient.core.hud.HudFrame
import dev.vclient.core.hud.HudRender
import dev.vclient.core.hud.HudTexts
import dev.vclient.core.module.HudModule
import dev.vclient.core.settings.BoolSetting
import dev.vclient.core.settings.settingOf

/** Potion HUD — active effects with remaining duration. */
class PotionHudModule : HudModule(ID, NAME, "Active potion effects and their remaining time.") {

    private val showAmplifier by settingOf(BoolSetting("show_amplifier", "Amplifier level", true, "Roman numerals (II, III...)."))
    private val showDuration by settingOf(BoolSetting("show_duration", "Remaining time", true))
    private val sortByTime by settingOf(BoolSetting("sort_by_time", "Sort by time left", true))

    override val hud = Element()

    override fun onTick(ctx: dev.vclient.core.module.TickContext) {
        // Effects come straight from the state snapshot each frame.
    }

    private inner class Element : HudElement(ID) {

        override fun measure(frame: HudFrame): SizeF =
            HudTexts.measureSized(frame, textLines(frame), textSizePx(frame.density))

        override fun draw(canvas: Canvas, frame: HudFrame, rect: RectF) {
            drawPanel(canvas, frame, rect)
            val d = frame.density
            val text = textSizePx(d)
            val pad = 5f * d
            val dot = 8f * d
            val dotGap = 4f * d
            val lineH = HudRender.textHeight(text) + 2f * d
            var top = rect.top + pad - HudRender.ascent(text)
            for ((index, effect) in effects(frame).withIndex()) {
                val x = rect.left + pad + dot + dotGap
                HudRender.drawCircle(canvas, rect.left + pad + dot / 2f, top - HudRender.textHeight(text) / 2f + HudRender.descentPx(text), dot / 2f, effect.colorArgb)
                val label = label(effect)
                val color = when {
                    effect.remainingTicks in 1..200 -> 0xFFFBBF24.toInt() // expiring soon
                    else -> textColorValue()
                }
                HudRender.drawText(canvas, label, x, top, text, color, textShadowEnabled())
                top += lineH
            }
        }

        private fun textLines(frame: HudFrame): List<String> = effects(frame).map { label(it) }

        private fun label(effect: dev.vclient.core.game.EffectEntry): String = buildString {
            append(effect.name)
            if (showAmplifier && effect.amplifier > 0) append(' ').append(roman(effect.amplifier + 1))
            if (showDuration) {
                val seconds = (effect.remainingTicks / 20).coerceAtLeast(0)
                append("  ").append("%d:%02d".format(seconds / 60, seconds % 60))
            }
        }

        private fun effects(frame: HudFrame): List<dev.vclient.core.game.EffectEntry> {
            val list = frame.state.effects
            return if (sortByTime) list.sortedBy { it.remainingTicks } else list
        }

        private fun roman(n: Int): String = when (n) {
            1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"; 5 -> "V"; 6 -> "VI"; 7 -> "VII"; 8 -> "VIII"
            else -> n.toString()
        }
    }

    companion object {
        const val ID = "potion_hud"
        const val NAME = "Potion HUD"
    }
}

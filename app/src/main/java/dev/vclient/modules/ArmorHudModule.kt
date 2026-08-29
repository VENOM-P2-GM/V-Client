package dev.vclient.modules

import android.graphics.Canvas
import android.graphics.RectF
import android.util.SizeF
import dev.vclient.core.game.ItemSlot
import dev.vclient.core.hud.HudElement
import dev.vclient.core.hud.HudFrame
import dev.vclient.core.hud.HudRender
import dev.vclient.core.module.HudModule
import dev.vclient.core.settings.BoolSetting
import java.util.Locale
import kotlin.math.roundToInt

/** Armor HUD — helmet/chest/legs/boots with durability bars. */
class ArmorHudModule : HudModule(ID, NAME, "Shows your equipped armor and its durability.") {

    private val showDurability by settingOf(BoolSetting("show_durability", "Durability", true, "Bar + percentage."))
    private val showEmpty by settingOf(BoolSetting("show_empty", "Empty slots", false, "Render placeholders for missing pieces."))

    override val hud: HudElement = Element()

    private inner class Element : HudElement(ID) {

        override fun measure(frame: HudFrame): SizeF {
            val rows = rows(frame)
            val d = frame.density
            val text = textSizePx(d)
            val pad = 5f * d
            val icon = 10f * d
            val gap = 4f * d
            val rowH = HudRender.textHeight(text) + (if (showDurability) 4f * d else 0f) + 3f * d
            val textW = rows.maxOf { HudRender.textWidth(it.name, text) }
            val durW = if (showDurability) HudRender.textWidth(" 100%", text) else 0f
            return SizeF(pad * 2 + icon + gap + textW + durW, pad * 2 + rows.size * rowH)
        }

        override fun draw(canvas: Canvas, frame: HudFrame, rect: RectF) {
            drawPanel(canvas, frame, rect)
            val d = frame.density
            val text = textSizePx(d)
            val pad = 5f * d
            val icon = 10f * d
            val gap = 4f * d
            val rowH = HudRender.textHeight(text) + (if (showDurability) 4f * d else 0f) + 3f * d
            var top = rect.top + pad
            for (row in rows(frame)) {
                // Icon tile
                HudRender.drawPanel(canvas, RectF(rect.left + pad, top + 1f * d, rect.left + pad + icon, top + 1f * d + icon), row.colorArgb, 2f * d)
                val textLeft = rect.left + pad + icon + gap
                // Name (+ % if durability)
                var pctText = ""
                if (showDurability && row.hasDurability) {
                    pctText = " " + (row.durability * 100 / row.maxDurability).toString() + "%"
                }
                HudRender.drawText(canvas, row.name + pctText, textLeft, top + HudRender.textHeight(text) - HudRender.descentPx(text), text, textColorValue(), textShadowEnabled())
                if (showDurability) {
                    val barTop = top + HudRender.textHeight(text) + 1.5f * d
                    val barFull = RectF(textLeft, barTop, rect.right - pad, barTop + 2f * d)
                    HudRender.drawRoundedBar(canvas, barFull, 0x33FFFFFF, 1f * d)
                    if (row.hasDurability) {
                        val ratio = (row.durability.toFloat() / row.maxDurability).coerceIn(0f, 1f)
                        val bar = RectF(textLeft, barTop, textLeft + (barFull.width() * ratio), barTop + 2f * d)
                        val color = when {
                            ratio > 0.5f -> 0xFF34D399.toInt()
                            ratio > 0.2f -> 0xFFFBBF24.toInt()
                            else -> 0xFFF87171.toInt()
                        }
                        HudRender.drawRoundedBar(canvas, bar, color, 1f * d)
                    }
                }
                top += rowH
            }
        }

        private fun rows(frame: HudFrame): List<ItemSlot> {
            val armor = frame.state.armor
            val defaults = listOf(
                ItemSlot(0, "Helmet", 0, 0, 0, 0xFF4B5563.toInt()),
                ItemSlot(1, "Chestplate", 0, 0, 0, 0xFF4B5563.toInt()),
                ItemSlot(2, "Leggings", 0, 0, 0, 0xFF4B5563.toInt()),
                ItemSlot(3, "Boots", 0, 0, 0, 0xFF4B5563.toInt()),
            )
            return defaults.map { placeholder ->
                armor.firstOrNull { it.index == placeholder.index } ?: placeholder.copy(name = "Empty ${placeholder.name}")
            }.filter { showEmpty || it.count > 0 || it.durability > 0 || it.maxDurability > 0 }
        }
    }

    companion object {
        const val ID = "armor_hud"
        const val NAME = "Armor HUD"
    }
}

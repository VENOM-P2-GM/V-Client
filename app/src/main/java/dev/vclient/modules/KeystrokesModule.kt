package dev.vclient.modules

import android.graphics.Canvas
import android.graphics.RectF
import android.util.SizeF
import dev.vclient.core.game.VKey
import dev.vclient.core.hud.HudElement
import dev.vclient.core.hud.HudFrame
import dev.vclient.core.hud.HudRender
import dev.vclient.core.module.HudModule
import dev.vclient.core.settings.BoolSetting
import dev.vclient.core.settings.ColorSetting
import dev.vclient.core.settings.IntSetting
import dev.vclient.core.settings.settingOf
import java.util.Locale

/**
 * Keystrokes — W/A/S/D + Jump/Sneak/Sprint key display.
 *
 * Key states come from the input tracker: real on-screen controls via
 * user-calibrated touch regions (accessibility motion events, Android 14+),
 * or an animated demo pattern while editing/previewing the HUD.
 */
class KeystrokesModule : HudModule(ID, NAME, "On-screen keystroke display (WASD, jump, sneak, sprint).") {

    private val keySize by settingOf(IntSetting("key_size", "Key size", 34, 24, 64, 1, "dp"))
    private val showJump by settingOf(BoolSetting("show_jump", "Jump row", true))
    private val showSneak by settingOf(BoolSetting("show_sneak", "Sneak key", true))
    private val showSprint by settingOf(BoolSetting("show_sprint", "Sprint key", true))
    private val showMouse by settingOf(BoolSetting("show_mouse", "LMB/RMB with CPS", true))
    private val pressedColor by settingOf(ColorSetting("pressed_color", "Pressed color", 0xFF8B5CF6.toInt()))

    override val hud = Element()

    private inner class Element : HudElement(ID) {

        override fun measure(frame: HudFrame): SizeF {
            val d = frame.density
            val key = keySize * d
            val gap = 3f * d
            val pad = 5f * d
            val rows = rowLayouts(frame)
            val width = rows.maxOf { it.size } * key + (rows.maxOf { it.size } - 1) * gap
            val height = rows.size * key + (rows.size - 1) * gap
            return SizeF(pad * 2 + width, pad * 2 + height)
        }

        override fun draw(canvas: Canvas, frame: HudFrame, rect: RectF) {
            drawPanel(canvas, frame, rect)
            val d = frame.density
            val key = keySize * d
            val gap = 3f * d
            val pad = 5f * d
            val rows = rowLayouts(frame)
            val widest = rows.maxOf { it.size }
            val gridWidth = widest * key + (widest - 1) * gap
            val text = key * 0.34f
            var top = rect.top + pad
            for (row in rows) {
                val rowWidth = row.size * key + (row.size - 1) * gap
                var left = rect.left + pad + (gridWidth - rowWidth) / 2f
                for (entry in row) {
                    val r = RectF(left, top, left + key, top + key)
                    val pressed = entry.pressed(frame)
                    val bg = if (pressed) pressedColor else 0x2EFFFFFF
                    HudRender.drawPanel(canvas, r, bg, 5f * d)
                    val labelColor = if (pressed) 0xFFFFFFFF.toInt() else textColorValue()
                    val label = entry.label(frame)
                    HudRender.drawText(
                        canvas, label,
                        r.centerX() - HudRender.textWidth(label, text) / 2f,
                        r.centerY() - (HudRender.ascent(text) + HudRender.descentPx(text)) / 2f,
                        text, labelColor, textShadowEnabled(), bold = pressed,
                    )
                    left += key + gap
                }
                top += key + gap
            }
        }

        private fun rowLayouts(frame: HudFrame): List<List<RowEntry>> {
            val rows = mutableListOf<List<RowEntry>>()
            rows.add(listOf(keyEntry(VKey.W)))
            rows.add(listOf(keyEntry(VKey.A), keyEntry(VKey.S), keyEntry(VKey.D)))
            if (showMouse) {
                rows.add(listOf(cpsEntry(false), cpsEntry(true)))
            }
            val bottomRow = buildList {
                if (showJump) add(keyEntry(VKey.JUMP))
                if (showSneak) add(keyEntry(VKey.SNEAK))
                if (showSprint) add(keyEntry(VKey.SPRINT))
            }
            if (bottomRow.isNotEmpty()) rows.add(bottomRow)
            return rows
        }

        private fun keyEntry(key: VKey) = RowEntry(key.display) { frame -> isPressed(frame, key) }
        private fun cpsEntry(right: Boolean) =
            RowEntry(if (right) "R" else "L") { frame ->
                (if (right) frame.input.cpsRight else frame.input.cpsLeft) > 0.0
            }

        private fun isPressed(frame: HudFrame, key: VKey): Boolean {
            if (frame.input.keys.contains(key)) return true
            if (frame.demo) return demoPattern(frame.timeMs, key)
            return false
        }

        /** Slow walking pattern used for HUD editor previews. */
        private fun demoPattern(timeMs: Long, key: VKey): Boolean {
            val step = (timeMs / 600) % 6
            return when (key) {
                VKey.W -> step == 0L || step == 5L
                VKey.A -> step == 1L
                VKey.S -> step == 3L
                VKey.D -> step == 2L
                VKey.JUMP -> step == 4L
                else -> false
            }
        }
    }

    private class RowEntry(val name: String, val pressedCheck: (HudFrame) -> Boolean) {
        fun pressed(frame: HudFrame): Boolean = pressedCheck(frame)
        fun label(frame: HudFrame): String = when (name) {
            "L" -> String.format(Locale.US, "L %.0f", frame.input.cpsLeft)
            "R" -> String.format(Locale.US, "R %.0f", frame.input.cpsRight)
            else -> name
        }
    }

    companion object {
        const val ID = "keystrokes"
        const val NAME = "Keystrokes"
    }
}

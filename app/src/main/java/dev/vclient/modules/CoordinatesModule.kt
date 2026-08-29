package dev.vclient.modules

import android.graphics.Canvas
import android.graphics.RectF
import android.util.SizeF
import dev.vclient.core.hud.HudElement
import dev.vclient.core.hud.HudFrame
import dev.vclient.core.hud.HudTexts
import dev.vclient.core.module.HudModule
import dev.vclient.core.settings.BoolSetting
import dev.vclient.core.settings.IntSetting
import dev.vclient.core.settings.ModeSetting
import dev.vclient.core.settings.settingOf
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Coordinates — displays the player position from the active game data
 * source (Manual, Demo, or a future bridge), with dimension and heading.
 */
class CoordinatesModule : HudModule(ID, NAME, "Shows your X/Y/Z position, dimension and facing.") {

    private val decimals by settingOf(IntSetting("decimals", "Decimals", 1, 0, 3, 1, ""))
    private val showDimension by settingOf(BoolSetting("show_dimension", "Dimension", true))
    private val showFacing by settingOf(BoolSetting("show_facing", "Facing", true))
    private val singleLine by settingOf(BoolSetting("single_line", "Single line", false, "Compact one-line layout."))
    private val positionMode by settingOf(
        ModeSetting("position_mode", "Position format", listOf("X Y Z", "XYZ"), "X Y Z")
    )

    override val hud = Element()

    private inner class Element : HudElement(ID) {

        override fun measure(frame: HudFrame): SizeF =
            HudTexts.measureSized(frame, lines(frame), textSizePx(frame.density))

        override fun draw(canvas: Canvas, frame: HudFrame, rect: RectF) {
            drawPanel(canvas, frame, rect)
            val ls = lines(frame)
            HudTexts.drawColored(
                canvas, frame, rect,
                ls.map { it to textColorValue() },
                textSizePx(frame.density), textShadowEnabled(),
            )
        }

        private fun lines(frame: HudFrame): List<String> {
            val pos = frame.state.position
                ?: return listOf("Position: set in V Menu → Settings")
            val fmt = "%%.%df".format(decimals)
            val xyz = when (positionMode) {
                "XYZ" -> String.format(Locale.US, "X$fmt Y$fmt Z$fmt", pos.x, pos.y, pos.z)
                else -> String.format(Locale.US, "$fmt / $fmt / $fmt", pos.x, pos.y, pos.z)
            }
            if (singleLine) {
                var line = xyz
                if (showFacing) line += " · ${facing(frame)}"
                if (showDimension) line += " · ${frame.state.dimension.display}"
                return listOf(line)
            }
            val out = mutableListOf(xyz)
            if (showFacing) out.add("Facing ${facing(frame)}")
            if (showDimension) out.add(frame.state.dimension.display)
            return out
        }

        private fun facing(frame: HudFrame): String {
            // Minecraft yaw: 0 = south, 90 = west, 180 = north, 270 = east.
            val yaw = frame.state.yawDeg ?: return "—"
            val normalized = ((yaw % 360f) + 360f) % 360f
            val dirs = listOf("S", "SW", "W", "NW", "N", "NE", "E", "SE")
            return dirs[(normalized / 45f).roundToInt() % 8]
        }
    }

    companion object {
        const val ID = "coordinates"
        const val NAME = "Coordinates"
    }
}

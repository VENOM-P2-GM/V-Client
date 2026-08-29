package dev.vclient.modules

import android.graphics.Canvas
import android.graphics.RectF
import android.util.SizeF
import dev.vclient.core.VClientCore
import dev.vclient.core.config.WaypointDto
import dev.vclient.core.game.GameDimension
import dev.vclient.core.hud.HudElement
import dev.vclient.core.hud.HudFrame
import dev.vclient.core.hud.HudRender
import dev.vclient.core.module.ModuleCategory
import dev.vclient.core.module.HudModule
import dev.vclient.core.settings.BoolSetting
import dev.vclient.core.settings.IntSetting
import dev.vclient.core.settings.settingOf
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Waypoints — nearest waypoints with live distance and a bearing arrow
 * (rotates relative to your heading). Waypoints are stored per profile and
 * managed from V Menu → HUD → Waypoints.
 */
class WaypointsModule : HudModule(
    ID, NAME,
    "Distance and direction to your waypoints.",
    ModuleCategory.UTILITY,
) {

    private val maxShown by settingOf(IntSetting("max_shown", "Max waypoints", 3, 1, 8, 1, ""))
    private val showDistance by settingOf(BoolSetting("show_distance", "Distance", true))
    private val showArrow by settingOf(BoolSetting("show_arrow", "Direction arrow", true, "Points toward the waypoint."))
    private val showCoords by settingOf(BoolSetting("show_coords", "Coordinates", false))
    private val horizontalOnly by settingOf(BoolSetting("horizontal_only", "Ignore height", true, "Horizontal distance only."))

    override val hud = Element()

    private data class Row(val waypoint: WaypointDto, val distance: Double, val bearing: Float?)

    private inner class Element : HudElement(ID) {

        override fun measure(frame: HudFrame): SizeF {
            val d = frame.density
            val text = textSizePx(d)
            val pad = 5f * d
            val arrow = 12f * d
            val arrowGap = 5f * d
            val rows = rows(frame)
            if (rows.isEmpty()) {
                val hint = if (frame.state.position == null) "Waypoints: set your position" else "No waypoints"
                return SizeF(pad * 2 + HudRender.textWidth(hint, text), pad * 2 + HudRender.textHeight(text))
            }
            var w = 0f
            for (row in rows) {
                val line = lineText(row)
                w = maxOf(w, HudRender.textWidth(line, text) + (if (showArrow) arrow + arrowGap else 0f))
                if (showCoords) w = maxOf(w, HudRender.textWidth(coordsText(row.waypoint), text * 0.85f))
            }
            val lineH = HudRender.textHeight(text) + (if (showCoords) HudRender.textHeight(text * 0.85f) + 1f * d else 0f) + 2f * d
            return SizeF(pad * 2 + w, pad * 2 + rows.size * lineH)
        }

        override fun draw(canvas: Canvas, frame: HudFrame, rect: RectF) {
            drawPanel(canvas, frame, rect)
            val d = frame.density
            val text = textSizePx(d)
            val pad = 5f * d
            val arrow = 12f * d
            val arrowGap = 5f * d
            val rows = rows(frame)
            if (rows.isEmpty()) {
                val hint = if (frame.state.position == null) "Waypoints: set your position" else "No waypoints"
                HudRender.drawText(canvas, hint, rect.left + pad, rect.top + pad - HudRender.ascent(text), text, textColorValue(), textShadowEnabled())
                return
            }
            val lineH = HudRender.textHeight(text) + (if (showCoords) HudRender.textHeight(text * 0.85f) + 1f * d else 0f) + 2f * d
            var top = rect.top + pad
            for (row in rows) {
                var textLeft = rect.left + pad
                if (showArrow && row.bearing != null) {
                    val cyArrow = top + HudRender.textHeight(text) / 2f
                    HudRender.drawArrow(canvas, textLeft + arrow / 2f, cyArrow, arrow / 2f, row.bearing, row.waypoint.colorArgb)
                    textLeft += arrow + arrowGap
                }
                val baseline = top - HudRender.ascent(text)
                HudRender.drawText(canvas, lineText(row), textLeft, baseline, text, row.waypoint.colorArgb, textShadowEnabled(), bold = true)
                if (showCoords) {
                    val small = text * 0.85f
                    HudRender.drawText(canvas, coordsText(row.waypoint), textLeft, baseline + HudRender.textHeight(small) + 1f * d, small, HudRender.withAlpha(textColorValue(), 170), textShadowEnabled())
                }
                top += lineH
            }
        }

        private fun rows(frame: HudFrame): List<Row> {
            val core = VClientCore.core
            val waypoints = core.profiles.activeData.value.waypoints.filter { it.visible }
            if (waypoints.isEmpty()) return emptyList()
            val pos = frame.state.position ?: return emptyList()
            val playerDim = frame.state.dimension
            val out = waypoints
                .filter { GameDimension.byId(it.dimension) == playerDim }
                .map { wp ->
                    val dx = wp.x - pos.x
                    val dy = wp.y - pos.y
                    val dz = wp.z - pos.z
                    val dist = if (horizontalOnly) sqrt(dx * dx + dz * dz) else sqrt(dx * dx + dy * dy + dz * dz)
                    Row(wp, dist, bearing(frame, dx, dz))
                }
                .sortedBy { it.distance }
                .take(maxShown)
            return out
        }

        /** Relative bearing in degrees; 0 = straight ahead, 90 = right. Null without heading. */
        private fun bearing(frame: HudFrame, dx: Double, dz: Double): Float? {
            val yaw = frame.state.yawDeg ?: return null
            val targetYaw = Math.toDegrees(atan2(-dx, dz).toDouble())
            var rel = targetYaw - yaw
            rel = ((rel + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
            return rel.toFloat()
        }

        private fun lineText(row: Row): String = buildString {
            append(row.waypoint.name)
            if (showDistance) {
                val m = row.distance
                if (m < 1000) append("  ").append(String.format(Locale.US, "%.0fm", m))
                else append("  ").append(String.format(Locale.US, "%.2fkm", m / 1000.0))
            }
        }

        private fun coordsText(wp: WaypointDto): String =
            String.format(Locale.US, "%d, %d, %d", wp.x.toInt(), wp.y.toInt(), wp.z.toInt())
    }

    companion object {
        const val ID = "waypoints"
        const val NAME = "Waypoints"
    }
}

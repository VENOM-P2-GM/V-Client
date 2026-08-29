package dev.vclient.core.hud

import android.graphics.Canvas
import android.graphics.RectF
import android.util.SizeF
import dev.vclient.core.game.GameState
import dev.vclient.core.game.InputSnapshot
import dev.vclient.core.runtime.FrameStats

/**
 * 9-point anchor grid. Elements position themselves relative to an anchor and
 * a dp offset; the HUD editor manipulates the offset/scale so layouts stay
 * resolution-independent.
 */
enum class HudAnchor(val id: String) {
    TOP_LEFT("top_left"), TOP_CENTER("top_center"), TOP_RIGHT("top_right"),
    CENTER_LEFT("center_left"), CENTER("center"), CENTER_RIGHT("center_right"),
    BOTTOM_LEFT("bottom_left"), BOTTOM_CENTER("bottom_center"), BOTTOM_RIGHT("bottom_right");

    /** Top-left corner of a w*h element (already scaled) on a canvasW*canvasH surface. */
    fun resolve(canvasW: Float, canvasH: Float, w: Float, h: Float): HudPoint = when (this) {
        TOP_LEFT -> HudPoint(0f, 0f)
        TOP_CENTER -> HudPoint((canvasW - w) / 2f, 0f)
        TOP_RIGHT -> HudPoint(canvasW - w, 0f)
        CENTER_LEFT -> HudPoint(0f, (canvasH - h) / 2f)
        CENTER -> HudPoint((canvasW - w) / 2f, (canvasH - h) / 2f)
        CENTER_RIGHT -> HudPoint(canvasW - w, (canvasH - h) / 2f)
        BOTTOM_LEFT -> HudPoint(0f, canvasH - h)
        BOTTOM_CENTER -> HudPoint((canvasW - w) / 2f, canvasH - h)
        BOTTOM_RIGHT -> HudPoint(canvasW - w, canvasH - h)
    }

    companion object {
        fun byId(id: String?): HudAnchor = entries.firstOrNull { it.id == id } ?: TOP_LEFT
    }
}

/** Pure-Kotlin 2D point (keeps HUD math unit-testable without Android). */
data class HudPoint(val x: Float, val y: Float)

/** Everything a HUD element needs to measure and draw itself for one frame. */
data class HudFrame(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val density: Float,
    val timeMs: Long,
    val dtMs: Double,
    val state: GameState,
    val stats: FrameStats?,
    val input: InputSnapshot,
    val demo: Boolean,
    /** True while the HUD editor is active; elements may render extra affordances. */
    val editorActive: Boolean = false,
)

/**
 * A single renderable HUD widget owned by a HudModule.
 *
 *  - [measure] returns the element's natural size in px at scale 1.
 *  - [draw] receives the final on-screen rect (position + size * scale).
 *  - [lastRect] is refreshed by the renderer every frame and is used by the
 *    HUD editor for hit-testing and selection outlines.
 */
abstract class HudElement(val id: String) {

    var anchor: HudAnchor = HudAnchor.TOP_LEFT
    /** Offset from the anchor, in dp (density-independent). */
    var offsetX = 12f
    var offsetY = 12f
    /** Uniform scale applied to the measured size. */
    var scale = 1f

    var lastRect: RectF = RectF()
        internal set

    abstract fun measure(frame: HudFrame): SizeF
    abstract fun draw(canvas: Canvas, frame: HudFrame, rect: RectF)

    /** Corner hit-test with padding, used by the HUD editor. */
    fun hitTest(x: Float, y: Float, slop: Float): Boolean =
        x >= lastRect.left - slop && x <= lastRect.right + slop &&
            y >= lastRect.top - slop && y <= lastRect.bottom + slop
}

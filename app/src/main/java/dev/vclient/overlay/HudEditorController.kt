package dev.vclient.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import dev.vclient.core.VClientCore
import dev.vclient.core.hud.HudElement
import dev.vclient.core.hud.HudFrame
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * HUD editor: while active, the HUD surface becomes touchable and elements
 * can be dragged (move), pinched (scale) and tapped (select). A grid + snap
 * keeps layouts tidy; the editor toolbar (owned by the service) exposes
 * snapping, centering and reset actions.
 */
class HudEditorController(
    private val view: HudSurfaceView,
    private val core: VClientCore,
    private val onActiveChanged: (Boolean) -> Unit,
) {

    var active = false
        private set
    var selected: HudElement? = null
        private set
    var snapToGrid = true

    private var lastFrame: HudFrame? = null
    private var lastX = 0f
    private var lastY = 0f
    private var pinchStartDistance = 0f
    private var pinchStartScale = 1f
    private val doneRect = RectF()
    private val snapRect = RectF()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1AFFFFFF
        strokeWidth = 1f
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x8022D3EE.toInt()
    }
    private val selectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF8B5CF6.toInt()
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8B5CF6.toInt() }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE6E9F2.toInt()
        textSize = 14f // fixed dp set at draw via density
    }

    fun toggle() = setActive(!active)

    fun setActive(next: Boolean) {
        if (active == next) return
        active = next
        if (!next) selected = null
        onActiveChanged(active)
        core.logger.i(TAG, "HUD editor ${if (next) "enabled" else "disabled"}")
    }

    // --- gestures --------------------------------------------------------------

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                // Editor control chips (drawn in drawOverlays).
                if (doneRect.contains(event.x, event.y)) {
                    setActive(false)
                    return true
                }
                if (snapRect.contains(event.x, event.y)) {
                    snapToGrid = !snapToGrid
                    return true
                }
                selected = hitTest(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2 && selected != null) {
                    pinchStartDistance = pointerDistance(event)
                    pinchStartScale = selected!!.scale
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val element = selected ?: return true
                if (event.pointerCount >= 2 && pinchStartDistance > 10f) {
                    val dist = pointerDistance(event)
                    val ratio = dist / pinchStartDistance
                    element.scale = (pinchStartScale * ratio).coerceIn(0.5f, 3f)
                } else {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    moveSelected(dx, dy)
                }
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pinchStartDistance = 0f
                if (selected != null) core.profiles.markDirty()
                return true
            }
        }
        return true
    }

    private fun moveSelected(dxPx: Float, dyPx: Float) {
        val element = selected ?: return
        val frame = lastFrame ?: return
        element.offsetX += dxPx / frame.density
        element.offsetY += dyPx / frame.density
        if (snapToGrid) {
            element.offsetX = snap(element.offsetX)
            element.offsetY = snap(element.offsetY)
        }
        clampToScreen(element, frame)
    }

    private fun clampToScreen(element: HudElement, frame: HudFrame) {
        val rect = element.lastRect
        if (rect.isEmpty) return
        if (rect.left < 0) element.offsetX -= rect.left / frame.density
        if (rect.top < 0) element.offsetY -= rect.top / frame.density
        if (rect.right > frame.canvasWidth) {
            element.offsetX -= (rect.right - frame.canvasWidth) / frame.density
        }
        if (rect.bottom > frame.canvasHeight) {
            element.offsetY -= (rect.bottom - frame.canvasHeight) / frame.density
        }
    }

    private fun pointerDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return hypot(dx, dy)
    }

    private fun hitTest(x: Float, y: Float): HudElement? {
        val slop = 12f * (lastFrame?.density ?: 1f)
        val candidates = core.modules.hudModules.filter { it.enabled }
        for (module in candidates.reversed()) {
            if (module.hud.hitTest(x, y, slop)) return module.hud
        }
        return null
    }

    // --- toolbar actions ---------------------------------------------------------

    fun centerHorizontal() {
        val element = selected ?: return
        val frame = lastFrame ?: return
        element.offsetX = 0f
        element.anchor = when (element.anchor) {
            dev.vclient.core.hud.HudAnchor.TOP_LEFT -> dev.vclient.core.hud.HudAnchor.TOP_CENTER
            dev.vclient.core.hud.HudAnchor.CENTER_LEFT -> dev.vclient.core.hud.HudAnchor.CENTER
            dev.vclient.core.hud.HudAnchor.BOTTOM_LEFT -> dev.vclient.core.hud.HudAnchor.BOTTOM_CENTER
            else -> element.anchor
        }
        core.profiles.markDirty()
    }

    fun resetScale() {
        selected?.scale = 1f
        core.profiles.markDirty()
    }

    // --- overlays -------------------------------------------------------------------

    fun drawOverlays(canvas: Canvas, frame: HudFrame) {
        lastFrame = frame
        val d = frame.density

        // Grid
        val step = GRID_DP * d
        var x = step
        while (x < frame.canvasWidth) {
            canvas.drawLine(x, 0f, x, frame.canvasHeight, gridPaint)
            x += step
        }
        var y = step
        while (y < frame.canvasHeight) {
            canvas.drawLine(0f, y, frame.canvasWidth, y, gridPaint)
            y += step
        }

        // Outlines + selection
        for (module in core.modules.hudModules) {
            if (!module.enabled) continue
            val element = module.hud
            if (element === selected) {
                canvas.drawRect(element.lastRect, selectPaint)
                val r = 6f * d
                canvas.drawCircle(element.lastRect.left, element.lastRect.top, r, handlePaint)
                canvas.drawCircle(element.lastRect.right, element.lastRect.top, r, handlePaint)
                canvas.drawCircle(element.lastRect.left, element.lastRect.bottom, r, handlePaint)
                canvas.drawCircle(element.lastRect.right, element.lastRect.bottom, r, handlePaint)
            } else {
                canvas.drawRect(element.lastRect, outlinePaint)
            }
        }

        // Status hint
        hintPaint.textSize = 13f * d
        val hint = buildString {
            append("HUD Editor — drag: move · pinch: scale · tap: select")
            if (snapToGrid) append(" · snap ${GRID_DP.toInt()}dp")
        }
        val textW = hintPaint.measureText(hint)
        val bar = RectF(
            frame.canvasWidth / 2f - textW / 2f - 10f * d,
            8f * d,
            frame.canvasWidth / 2f + textW / 2f + 10f * d,
            8f * d + 2 * d + 13f * d,
        )
        dev.vclient.core.hud.HudRender.drawPanel(canvas, bar, 0xE60E1420.toInt(), 8f * d)
        canvas.drawText(hint, bar.left + 10f * d, bar.top + 3f * d - hintPaint.ascent(), hintPaint)

        // Control chips (top-right): snap toggle + done
        val chipText = 13f * d
        val chipH = 30f * d
        val chipPad = 12f * d
        val snapLabel = if (snapToGrid) "Snap ✓" else "Snap ✕"
        val doneLabel = "Done"
        hintPaint.textSize = chipText
        val snapW = hintPaint.measureText(snapLabel) + chipPad * 2
        val doneW = hintPaint.measureText(doneLabel) + chipPad * 2
        val chipTop = 8f * d
        val margin = 12f * d
        doneRect.set(
            frame.canvasWidth - margin - doneW, chipTop,
            frame.canvasWidth - margin, chipTop + chipH,
        )
        snapRect.set(
            doneRect.left - margin - snapW, chipTop,
            doneRect.left - margin, chipTop + chipH,
        )
        dev.vclient.core.hud.HudRender.drawPanel(canvas, doneRect, 0xFF6D28D9.toInt(), chipH / 2f)
        hintPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText(
            doneLabel,
            doneRect.centerX() - hintPaint.measureText(doneLabel) / 2f,
            doneRect.centerY() - (hintPaint.ascent() + hintPaint.descent()) / 2f,
            hintPaint,
        )
        dev.vclient.core.hud.HudRender.drawPanel(
            canvas, snapRect, 0xE60E1420.toInt(), chipH / 2f,
            borderColor = if (snapToGrid) 0xFF22D3EE.toInt() else 0x66FFFFFF.toInt(),
            borderWidthPx = d,
        )
        hintPaint.color = if (snapToGrid) 0xFF22D3EE.toInt() else 0xFFE6E9F2.toInt()
        canvas.drawText(
            snapLabel,
            snapRect.centerX() - hintPaint.measureText(snapLabel) / 2f,
            snapRect.centerY() - (hintPaint.ascent() + hintPaint.descent()) / 2f,
            hintPaint,
        )
        hintPaint.color = 0xFFE6E9F2.toInt()
    }

    private fun snap(v: Float): Float = (v / SNAP_DP).roundToInt() * SNAP_DP

    companion object {
        const val TAG = "HudEditor"
        const val GRID_DP = 16f
        const val SNAP_DP = 8f
    }
}

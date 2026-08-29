package dev.vclient.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import dev.vclient.core.VClientCore
import dev.vclient.core.game.InputTracker
import dev.vclient.core.hud.HudFrame
import dev.vclient.core.module.TickContext
import dev.vclient.core.runtime.FrameStats
import dev.vclient.core.runtime.FrameStatsCalculator
import dev.vclient.core.runtime.NativeBridge

/**
 * The HUD render surface. One Choreographer-driven canvas:
 *
 *   doFrame -> frame stats (native) -> poll data source -> tick modules
 *           -> request invalidation -> onDraw lays out + draws HUD elements.
 *
 * The view is attached as a full-screen NOT_TOUCHABLE overlay window while
 * gaming; the HUD editor flips the window to touchable and routes events to
 * [editor].
 */
class HudSurfaceView(
    context: Context,
    private val core: VClientCore,
) : View(context) {

    private val choreographer = Choreographer.getInstance()
    private val fallbackStats = FrameStatsCalculator()
    private var lastFrameNanos = 0L
    private var lastSlowTickMs = 0L
    private var attached = false

    // Latest frame inputs, shared between the tick and the draw pass.
    private var lastDtMs = 16.7
    private var lastStats: FrameStats? = null

    val editor = HudEditorController(this, core) { active -> onEditorActiveChanged(active) }
    var onEditorActiveChanged: (Boolean) -> Unit = {}

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(nanos: Long) {
            if (!attached) return
            val dtMs = if (lastFrameNanos == 0L) 16.7 else (nanos - lastFrameNanos) / 1_000_000.0
            lastFrameNanos = nanos
            lastDtMs = dtMs

            lastStats = NativeBridge.frameStats(dtMs) ?: fallbackStats.push(dtMs)
            val nowMs = System.currentTimeMillis()
            val state = core.session.poll(nowMs)
            val slowTick = nowMs - lastSlowTickMs >= SLOW_TICK_MS
            if (slowTick) lastSlowTickMs = nowMs

            core.modules.tick(
                TickContext(
                    dtMs = dtMs,
                    timeMs = nowMs,
                    slowTick = slowTick,
                    state = state,
                    stats = lastStats,
                    demo = core.session.demo,
                )
            )

            postInvalidateOnAnimation()
            choreographer.postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        attached = false
        choreographer.removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val focused = core.session.gameFocused.value
        val gameVisible = focused != false || editor.active
        if (!gameVisible || !core.session.active.value) return

        val nowMs = System.currentTimeMillis()
        val frame = HudFrame(
            canvasWidth = width.toFloat(),
            canvasHeight = height.toFloat(),
            density = resources.displayMetrics.density,
            timeMs = nowMs,
            dtMs = lastDtMs,
            state = core.session.state.value,
            stats = lastStats,
            input = InputTracker.snapshot(),
            demo = core.session.demo,
            editorActive = editor.active,
        )

        val drawRect = RectF()
        for (module in core.modules.hudModules) {
            if (!module.enabled) continue
            core.modules.guard(module) {
                val element = module.hud
                val size = element.measure(frame)
                val w = size.width * element.scale
                val h = size.height * element.scale
                val base = element.anchor.resolve(frame.canvasWidth, frame.canvasHeight, w, h)
                val left = base.x + element.offsetX * frame.density
                val top = base.y + element.offsetY * frame.density
                drawRect.set(left, top, left + w, top + h)
                element.lastRect = RectF(drawRect)
                element.draw(canvas, frame, drawRect)
            }
        }

        if (editor.active) {
            editor.drawOverlays(canvas, frame)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (editor.active) return editor.onTouch(event)
        return false
    }

    companion object {
        const val SLOW_TICK_MS = 500L
    }
}

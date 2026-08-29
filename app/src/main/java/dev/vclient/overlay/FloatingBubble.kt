package dev.vclient.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * The floating "V" bubble: drag to move (position persists), tap to open
 * V Menu, long-press to toggle the HUD editor.
 */
class FloatingBubble(
    context: Context,
    private val onOpenMenu: () -> Unit,
    private val onToggleEditor: () -> Unit,
    private val onPositionChanged: (Float, Float) -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val sizePx = (48 * density).toInt()
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0x6622D3EE.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        textSize = 22f * density
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onOpenMenu()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onToggleEditor()
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                val params = layoutParams as? WindowManager.LayoutParams ?: return false
                params.x += dx.toInt()
                params.y += dy.toInt()
                params.x = params.x.coerceIn(0, resources.displayMetrics.widthPixels - sizePx)
                params.y = params.y.coerceIn(0, resources.displayMetrics.heightPixels - sizePx)
                windowManagerUpdate(params)
                return true
            }
        },
    )

    var windowManagerUpdate: (WindowManager.LayoutParams) -> Unit = {}

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        circlePaint.shader = RadialGradient(
            w / 2f, h / 3f, h * 0.9f,
            0xFFA78BFA.toInt(), 0xFF6D28D9.toInt(),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val r = width / 2f
        canvas.drawCircle(r, r, r - 1f, circlePaint)
        canvas.drawCircle(r, r, r - 1.5f * density, borderPaint)
        val baseline = r - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText("V", r, baseline, textPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            val params = layoutParams as? WindowManager.LayoutParams
            if (params != null && abs(event.eventTime - event.downTime) > 150) {
                onPositionChanged(params.x.toFloat(), params.y.toFloat())
            }
        }
        return handled || true
    }

    companion object {
        fun layoutParams(context: Context, startX: Float, startY: Float): WindowManager.LayoutParams {
            val dm = context.resources.displayMetrics
            val x = if (startX == Float.MIN_VALUE) dm.widthPixels - 72f * dm.density else startX
            val y = if (startY == Float.MIN_VALUE) 120f * dm.density else startY
            return WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x.toInt()
                this.y = y.toInt()
            }
        }
    }
}

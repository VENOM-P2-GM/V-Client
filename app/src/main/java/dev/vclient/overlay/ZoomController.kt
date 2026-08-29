package dev.vclient.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import dev.vclient.core.logging.VLogger
import dev.vclient.modules.ZoomModule
import dev.vclient.overlay.accessibility.VAccessibilityService

/**
 * Zoom — controls Android's magnification controller through the optional
 * accessibility service, and (optionally) renders a small floating toggle
 * button above the game.
 */
class ZoomController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val logger: VLogger,
) {

    private var zoomed = false
    private var buttonView: TextView? = null

    private fun zoomModule(): ZoomModule? =
        dev.vclient.core.VClientCore.core.modules.byId(ZoomModule.ID) as? ZoomModule

    /** Called when the Zoom module toggles; keeps the button window in sync. */
    fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            setZoomed(false)
            removeButton()
            return
        }
        val module = zoomModule() ?: return
        if (module.wantsButton()) addButton()
        // Keep the current zoom state applied (user may re-enable while zoomed).
        if (zoomed) apply()
    }

    fun toggle() = setZoomed(!zoomed)

    fun isZoomed(): Boolean = zoomed

    private fun setZoomed(next: Boolean) {
        zoomed = next
        apply()
        refreshButton()
    }

    private fun apply() {
        val service = VAccessibilityService.instance
        val module = zoomModule()
        if (service == null) {
            logger.w(TAG, "Zoom needs the V Client accessibility service (Settings → Accessibility)")
            return
        }
        if (zoomed && module != null) {
            val dm = context.resources.displayMetrics
            val cx = dm.widthPixels / 2f
            val cy = dm.heightPixels / 2f
            runCatching { service.magnify(module.currentScale(), cx, cy, module.wantsAnimation()) }
                .onFailure { logger.e(TAG, "Magnify failed", it) }
        } else {
            runCatching { service.resetMagnification(module?.wantsAnimation() ?: true) }
                .onFailure { logger.e(TAG, "Reset magnification failed", it) }
        }
    }

    // --- floating toggle button ----------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun addButton() {
        if (buttonView != null) return
        val density = context.resources.displayMetrics.density
        val button = TextView(context).apply {
            text = "⤢"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE66D28D9.toInt())
                setSize((40 * density).toInt(), (40 * density).toInt())
            }
            setOnClickListener { toggle() }
        }
        val size = (40 * density).toInt()
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = (8 * density).toInt()
        }
        runCatching { windowManager.addView(button, params) }.onFailure {
            logger.e(TAG, "Failed to add zoom button", it)
            return
        }
        buttonView = button
    }

    private fun removeButton() {
        buttonView?.let { runCatching { windowManager.removeView(it) } }
        buttonView = null
    }

    private fun refreshButton() {
        val button = buttonView ?: return
        button.text = if (zoomed) "⤡" else "⤢"
        (button.background as? GradientDrawable)?.setColor(
            if (zoomed) 0xE622D3EE.toInt() else 0xE66D28D9.toInt()
        )
    }

    fun destroy() {
        setZoomed(false)
        removeButton()
    }

    companion object {
        const val TAG = "ZoomController"
    }
}

package dev.vclient.overlay.vmenu

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import dev.vclient.core.VClientCore
import dev.vclient.ui.theme.VClientTheme

/**
 * The in-game V Menu window: a bottom-sheet style overlay panel with a dim
 * (and on Android 12+, blurred) backdrop that closes on outside taps. The
 * window is focusable while open so search/numeric fields work with the
 * soft keyboard.
 */
class VMenuWindow(private val context: Context) {

    private val core = VClientCore.core
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var host: OverlayComposeHost? = null
    private var backdrop: View? = null

    val isVisible: Boolean get() = host != null

    fun show(onClose: () -> Unit, onOpenEditor: () -> Unit) {
        if (host != null) hide()
        addBackdrop(onClose)

        val newHost = OverlayComposeHost(context)
        newHost.view.setContent {
            VClientTheme {
                VMenuRoot(onClose = onClose, onOpenEditor = onOpenEditor)
            }
        }

        val dm = context.resources.displayMetrics
        val density = dm.density
        val width = minOf((dm.widthPixels * 0.94f).toInt(), (720 * density).toInt())
        val height = (dm.heightPixels * 0.82f).toInt()

        val params = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        windowManager.addView(newHost.view, params)
        host = newHost
        core.logger.d(TAG, "V Menu opened")
    }

    fun hide() {
        host?.let { h ->
            runCatching { windowManager.removeView(h.view) }
            h.dispose()
        }
        host = null
        backdrop?.let { runCatching { windowManager.removeView(it) } }
        backdrop = null
        core.logger.d(TAG, "V Menu closed")
    }

    private fun addBackdrop(onOutsideTap: () -> Unit) {
        val dimOnly = !core.settings.vmenuBlurBackdrop || Build.VERSION.SDK_INT < 31
        val density = context.resources.displayMetrics.density
        val view = View(context).apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) onOutsideTap()
                true
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            @Suppress("DEPRECATION")
            flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            dimAmount = 0.45f
            if (!dimOnly && Build.VERSION.SDK_INT >= 31) {
                @Suppress("DEPRECATION")
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                setBlurBehindRadius((20 * density).toInt())
            }
        }
        windowManager.addView(view, params)
        backdrop = view
    }

    companion object {
        const val TAG = "VMenu"
    }
}

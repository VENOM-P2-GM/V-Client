package dev.vclient.overlay.accessibility

import android.accessibilityservice.AccessibilityService
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.accessibilityservice.MagnificationConfig
import android.view.accessibility.AccessibilityEvent
import dev.vclient.core.VClientCore
import dev.vclient.core.game.GameLauncher
import dev.vclient.core.game.InputTracker

/**
 * Optional accessibility service powering:
 *
 *  1. Game-focus detection (window state events) so the HUD can hide when
 *     Minecraft isn't on screen — requires no content access at all.
 *  2. Zoom — controlling the system magnification controller.
 *  3. On Android 14+, observing raw touchscreen motion events so Keystrokes
 *     and the CPS counter reflect real taps, and user-calibrated regions map
 *     game controls to keys.
 *
 * Privacy: the service declares canRetrieveWindowContent="false" and never
 * reads screen content or text. It only watches package names of window
 * changes and raw touch coordinates.
 */
class VAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching {
                val info = serviceInfo
                info.setMotionEventSources(InputDevice.SOURCE_TOUCHSCREEN)
                serviceInfo = info
            }.onFailure {
                VClientCore.core.logger.w(TAG, "Motion event source registration failed", it)
            }
        }
        VClientCore.core.logger.i(TAG, "Accessibility service connected (motion events: ${Build.VERSION.SDK_INT >= 34})")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        val core = VClientCore.core
        when (packageName) {
            GameLauncher.MC_PACKAGE -> core.session.setGameFocused(true)
            // Our own overlay windows must not clear the game-focus state.
            core.appContext.packageName -> Unit
            else -> core.session.setGameFocused(false)
        }
    }

    override fun onMotionEvent(event: MotionEvent) {
        val width = resources.displayMetrics.widthPixels.toFloat()
        InputTracker.onMotion(event, width)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        runCatching { VClientCore.core.session.setGameFocused(null) }
        runCatching { InputTracker.clearAllKeys() }
    }

    // --- Magnification (Zoom module) ---------------------------------------------

    fun magnify(scale: Float, centerX: Float, centerY: Float, animate: Boolean) {
        val controller = magnificationController
        if (Build.VERSION.SDK_INT >= 33) {
            val config = MagnificationConfig.Builder()
                .setMode(MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN)
                .setScale(scale)
                .setCenterX(centerX)
                .setCenterY(centerY)
                .build()
            controller.setMagnificationConfig(config, animate)
        } else {
            @Suppress("DEPRECATION")
            controller.setScale(scale, animate)
            @Suppress("DEPRECATION")
            controller.setCenter(centerX, centerY, animate)
        }
    }

    fun resetMagnification(animate: Boolean) {
        magnificationController.reset(animate)
    }

    companion object {
        const val TAG = "VAccessibility"

        @Volatile var instance: VAccessibilityService? = null
            private set

        val isConnected: Boolean get() = instance != null
        val motionEventsAvailable: Boolean get() = Build.VERSION.SDK_INT >= 34 && instance != null
    }
}

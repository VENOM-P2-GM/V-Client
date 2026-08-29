package dev.vclient.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.vclient.core.logging.VLogger
import dev.vclient.modules.FullbrightModule
import dev.vclient.modules.MotionBlurModule
import dev.vclient.core.runtime.NativeBridge
import kotlin.math.abs

/**
 * Full-screen effect windows drawn above the game:
 *
 *  - Fullbright: translucent light filter (works on any Android 8+ device).
 *  - Motion Blur: a transparent window using Android 12+ cross-window
 *    blur-behind; the radius is driven by gyroscope angular velocity through
 *    the native MotionBlur model (or a Kotlin fallback).
 */
class EffectWindows(
    private val context: Context,
    private val windowManager: WindowManager,
    private val logger: VLogger,
) : SensorEventListener {

    private var filterView: View? = null
    private var blurView: View? = null
    private var blurModule: MotionBlurModule? = null
    private var sensorManager: SensorManager? = null
    private var lastBlurApplyMs = 0L
    private var fallbackRadius = 0f

    /** True when the OS reports cross-window blur as available (Android 12+). */
    val blurSupported: Boolean =
        Build.VERSION.SDK_INT >= 31 && runCatching { windowManager.isCrossWindowBlurEnabled }.getOrDefault(false)

    // --- Fullbright -------------------------------------------------------------

    fun setFullbright(enabled: Boolean, module: FullbrightModule) {
        if (enabled) {
            if (filterView != null) return
            val view = View(context).apply {
                setBackgroundColor(applyAlpha(module.filterColor(), module.filterAlpha()))
            }
            windowManager.addView(view, overlayParams())
            filterView = view
            logger.d(TAG, "Fullbright filter ON (alpha=${module.filterAlpha()})")
        } else {
            filterView?.let { runCatching { windowManager.removeView(it) } }
            filterView = null
            logger.d(TAG, "Fullbright filter OFF")
        }
    }

    // --- Motion Blur --------------------------------------------------------------

    fun setMotionBlur(enabled: Boolean, module: MotionBlurModule) {
        if (enabled && !blurSupported) {
            logger.w(TAG, "Motion Blur requested but cross-window blur is unavailable on this device")
            module.setEnabled(false)
            return
        }
        if (enabled) {
            if (blurView != null) return
            blurModule = module
            NativeBridge.motionBlurConfigure(module.currentSensitivity(), module.currentMaxRadiusDp().toFloat())
            val view = View(context)
            val params = overlayParams()
            if (Build.VERSION.SDK_INT >= 31) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                params.dimAmount = 0.01f
                params.setBlurBehindRadius(0)
            }
            windowManager.addView(view, params)
            blurView = view
            registerGyro()
            logger.d(TAG, "Motion blur window ON")
        } else {
            unregisterGyro()
            blurView?.let { runCatching { windowManager.removeView(it) } }
            blurView = null
            blurModule = null
            logger.d(TAG, "Motion blur window OFF")
        }
    }

    private fun registerGyro() {
        val sm = sensorManager ?: context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = sm
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensor ->
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun unregisterGyro() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val module = blurModule ?: return
        val view = blurView ?: return
        if (event == null || event.sensor?.type != Sensor.TYPE_GYROSCOPE) return
        val now = System.currentTimeMillis()
        if (now - lastBlurApplyMs < BLUR_THROTTLE_MS) return
        lastBlurApplyMs = now

        val wx = event.values.getOrNull(0) ?: 0f
        val wy = event.values.getOrNull(1) ?: 0f
        val dtMs = 1000.0 / 60.0
        val maxRadiusDp = module.currentMaxRadiusDp()
        val sensitivity = module.currentSensitivity()

        val radiusDp = if (NativeBridge.available) {
            NativeBridge.motionBlurRadius(wx.toDouble(), wy.toDouble(), dtMs)
        } else {
            // Kotlin fallback: smooth angular speed -> radius.
            val speed = abs(wx) + abs(wy)
            fallbackRadius = fallbackRadius * 0.85f + (speed * sensitivity * 12f) * 0.15f
            fallbackRadius.coerceIn(0f, maxRadiusDp.toFloat())
        }
        val radiusPx = (radiusDp.coerceIn(0f, maxRadiusDp.toFloat()) * context.resources.displayMetrics.density).toInt()
        runCatching {
            val params = view.layoutParams as WindowManager.LayoutParams
            params.setBlurBehindRadius(radiusPx)
            windowManager.updateViewLayout(view, params)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun destroy() {
        filterView?.let { runCatching { windowManager.removeView(it) } }
        filterView = null
        blurModule = null
        unregisterGyro()
        blurView?.let { runCatching { windowManager.removeView(it) } }
        blurView = null
    }

    // --- helpers -------------------------------------------------------------------

    private fun overlayParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun applyAlpha(argb: Int, alphaFraction: Float): Int =
        (argb and 0x00FFFFFF) or ((255 * alphaFraction).toInt().coerceIn(0, 255) shl 24)

    companion object {
        const val TAG = "EffectWindows"
        const val BLUR_THROTTLE_MS = 24L
    }
}

package dev.vclient.modules

import dev.vclient.core.module.Module
import dev.vclient.core.module.ModuleCategory
import dev.vclient.core.runtime.ClientHooks
import dev.vclient.core.settings.FloatSetting
import dev.vclient.core.settings.IntSetting
import dev.vclient.core.settings.settingOf

/**
 * Motion Blur — device-motion-driven blur of the screen behind a
 * cross-window blur overlay (Android 12+ `setBlurBehindRadius`). The blur
 * radius is derived from gyroscope angular velocity through the native
 * MotionBlur model, so fast turns produce stronger blur. Devices without
 * cross-window blur fall back to disabled-with-notice.
 */
class MotionBlurModule : Module(
    ID, NAME, ModuleCategory.VISUAL,
    "Gyroscope-driven motion blur using Android 12+ cross-window blur.",
) {

    private val sensitivity by settingOf(
        FloatSetting("sensitivity", "Sensitivity", 1.0f, 0.3f, 3.0f, 0.1f, "x", 1,
            "How quickly blur appears when you move the device.")
    )
    private val maxRadius by settingOf(
        IntSetting("max_radius", "Max blur", 18, 2, 40, 1, "dp")
    )

    override fun onEnabled() {
        ClientHooks.blurRequest?.invoke(true)
    }

    override fun onDisabled() {
        ClientHooks.blurRequest?.invoke(false)
    }

    fun currentSensitivity(): Float = sensitivity
    fun currentMaxRadiusDp(): Int = maxRadius

    companion object {
        const val ID = "motion_blur"
        const val NAME = "Motion Blur"
    }
}

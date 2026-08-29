package dev.vclient.modules

import dev.vclient.core.module.Module
import dev.vclient.core.module.ModuleCategory
import dev.vclient.core.runtime.ClientHooks
import dev.vclient.core.settings.ColorSetting
import dev.vclient.core.settings.FloatSetting
import dev.vclient.core.settings.settingOf

/**
 * Fullbright — a light overlay filter that lifts perceived screen brightness
 * in caves and at night. Implemented as a translucent full-screen window
 * (like screen-filter apps); the game itself and its files are untouched.
 */
class FullbrightModule : Module(
    ID, NAME, ModuleCategory.VISUAL,
    "Brightness filter over the game — works everywhere, modifies nothing.",
) {

    private val strength by settingOf(
        FloatSetting("strength", "Strength", 0.18f, 0.05f, 0.40f, 0.01f, "", 2)
    )
    private val tint by settingOf(
        ColorSetting("tint", "Tint", 0xFFFFF6E0.toInt(), "Slightly warm white reads best.")
    )

    override fun onEnabled() {
        ClientHooks.filterRequest?.invoke(true)
    }

    override fun onDisabled() {
        ClientHooks.filterRequest?.invoke(false)
    }

    fun filterAlpha(): Float = strength
    fun filterColor(): Int = tint

    companion object {
        const val ID = "fullbright"
        const val NAME = "Fullbright"
    }
}

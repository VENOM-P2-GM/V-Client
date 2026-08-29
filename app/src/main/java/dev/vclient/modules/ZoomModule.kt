package dev.vclient.modules

import dev.vclient.core.module.Module
import dev.vclient.core.module.ModuleCategory
import dev.vclient.core.runtime.ClientHooks
import dev.vclient.core.settings.BoolSetting
import dev.vclient.core.settings.FloatSetting
import dev.vclient.core.settings.settingOf

/**
 * Zoom — magnifies the screen through the accessibility magnification
 * controller (an official Android API; nothing is injected into the game).
 * Toggle it from V Menu or the floating zoom button while in-game.
 */
class ZoomModule : Module(
    ID, NAME, ModuleCategory.VISUAL,
    "Camera zoom via Android's magnification controller. " +
        "Requires the optional V Client accessibility service.",
) {

    private val zoomScale by settingOf(
        FloatSetting("zoom_scale", "Zoom", 3.0f, 1.5f, 8.0f, 0.5f, "x", 1,
            "Magnification factor while zoomed in.")
    )
    private val animate by settingOf(BoolSetting("animate", "Animate", true))
    private val showButton by settingOf(
        BoolSetting("show_button", "Floating zoom button", true, "A small on-screen toggle while in-game.")
    )

    override fun onEnabled() {
        ClientHooks.zoomRequest?.invoke(true)
    }

    override fun onDisabled() {
        ClientHooks.zoomRequest?.invoke(false)
    }

    fun currentScale(): Float = zoomScale
    fun wantsAnimation(): Boolean = animate
    fun wantsButton(): Boolean = showButton

    companion object {
        const val ID = "zoom"
        const val NAME = "Zoom"
    }
}

package dev.vclient.core.module

import android.graphics.Canvas
import android.graphics.RectF
import dev.vclient.core.game.GameState
import dev.vclient.core.hud.HudElement
import dev.vclient.core.runtime.ClientHooks
import dev.vclient.core.runtime.FrameStats
import dev.vclient.core.settings.BoolSetting
import dev.vclient.core.settings.ColorSetting
import dev.vclient.core.settings.IntSetting
import dev.vclient.core.settings.Setting
import dev.vclient.core.settings.SettingContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ModuleCategory(val display: String) {
    HUD("HUD"),
    VISUAL("Visual"),
    UTILITY("Utility"),
}

/** Data passed to [Module.onTick] once per rendered frame. */
data class TickContext(
    val dtMs: Double,
    val timeMs: Long,
    /** True roughly twice per second; use for expensive polling (CPU, battery...). */
    val slowTick: Boolean,
    val state: GameState,
    val stats: FrameStats?,
    val demo: Boolean,
)

/**
 * Base class for every V Client module.
 *
 * Lifecycle: [onEnabled] / [onDisabled] are invoked when the module toggles.
 * [onTick] runs once per frame while enabled; it is wrapped by the
 * ModuleManager's error guard, so a throwing module can never crash the HUD.
 *
 * Settings are declared as class properties and automatically become part of
 * the module's serialized profile state.
 */
abstract class Module(
    val id: String,
    val name: String,
    val category: ModuleCategory,
    val description: String = "",
) {
    val settings = SettingContainer()

    private val _enabledFlow = MutableStateFlow(false)
    val enabledFlow: StateFlow<Boolean> get() = _enabledFlow
    val enabled: Boolean get() = _enabledFlow.value

    fun setEnabled(next: Boolean) {
        if (next == _enabledFlow.value) return
        try {
            if (next) onEnabled() else onDisabled()
        } catch (t: Throwable) {
            // A failing lifecycle hook aborts the toggle instead of leaving
            // the module in an inconsistent half-enabled state.
            android.util.Log.e("VClient/Module", "Lifecycle hook failed for $id", t)
            return
        }
        _enabledFlow.value = next
        ClientHooks.dispatchModuleChanged(this)
    }

    fun toggle() = setEnabled(!enabled)

    protected open fun onEnabled() {}
    protected open fun onDisabled() {}
    open fun onTick(ctx: TickContext) {}

    protected fun <T> settingOf(setting: Setting<T>): Setting<T> = settings.add(setting)

    override fun toString(): String = "Module($id)"
}

/**
 * Base class for modules that render a HUD element on the overlay.
 * Provides the shared visual settings so every HUD module looks consistent
 * and is configurable out of the box.
 */
abstract class HudModule(
    id: String,
    name: String,
    description: String = "",
    category: ModuleCategory = ModuleCategory.HUD,
) : Module(id, name, category, description) {

    abstract val hud: HudElement

    // --- shared visual settings (ids are stable -> profile-portable) -----------
    protected val showBackground by settingOf(
        BoolSetting("background", "Background", true, "Draw a panel behind the element.")
    )
    protected val backgroundColor by settingOf(
        ColorSetting("background_color", "Background color", 0xE60E1420.toInt())
    )
    protected val textColorSetting by settingOf(
        ColorSetting("text_color", "Text color", 0xFFE6E9F2.toInt())
    )
    protected val accentColor by settingOf(
        ColorSetting("accent_color", "Accent color", 0xFF8B5CF6.toInt(), "Used for values and highlights.")
    )
    protected val textSizeSetting by settingOf(
        IntSetting("text_size", "Text size", 13, 8, 28, 1, "dp")
    )
    protected val textShadow by settingOf(
        BoolSetting("text_shadow", "Text shadow", true)
    )

    fun panelColor(): Int = backgroundColor
    fun textColorValue(): Int = textColorSetting
    fun accentValue(): Int = accentColor
    fun textSizePx(density: Float): Float = textSizeSetting * density
    fun textShadowEnabled(): Boolean = textShadow
    fun backgroundEnabled(): Boolean = showBackground

    /** Draws this module's standard rounded panel if the background setting is on. */
    fun drawPanel(canvas: Canvas, frame: dev.vclient.core.hud.HudFrame, rect: RectF) {
        if (!showBackground) return
        dev.vclient.core.hud.HudRender.drawPanel(
            canvas, rect, backgroundColor,
            radiusPx = 8f * frame.density,
        )
    }
}

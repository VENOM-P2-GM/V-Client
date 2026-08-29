package dev.vclient.modules

import dev.vclient.core.module.Module

/**
 * Central module catalog. Adding a module = create the class, add one line
 * here — registration, settings persistence, HUD layout, V Menu UI and
 * profile capture all pick it up automatically.
 */
object ModuleRegistry {

    fun createAll(): List<Module> = listOf(
        // HUD
        FpsCounterModule(),
        CoordinatesModule(),
        ArmorHudModule(),
        InventoryHudModule(),
        PotionHudModule(),
        KeystrokesModule(),
        ClockModule(),
        CpsCounterModule(),
        PerformanceHudModule(),
        // Visual
        CrosshairModule(),
        ZoomModule(),
        FullbrightModule(),
        MotionBlurModule(),
        // Utility
        WaypointsModule(),
    )
}

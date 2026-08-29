package dev.vclient.core.config

import dev.vclient.core.hud.HudAnchor
import dev.vclient.core.logging.VLogger
import dev.vclient.core.module.HudModule
import dev.vclient.core.module.ModuleManager
import dev.vclient.core.settings.decodeJson
import dev.vclient.core.settings.encodeJson

/**
 * The only place that maps between live module objects and profile DTOs.
 * A newly added module automatically gains persistence: its settings and HUD
 * layout are picked up by [capture], and unknown module ids in a profile are
 * simply ignored by [apply] (forward/backward compatible).
 */
object ProfileSync {

    /** Captures the live session on top of [base] (waypoints etc. are preserved). */
    fun capture(modules: ModuleManager, base: ProfileData): ProfileData {
        val moduleStates = modules.all.map { module ->
            ModuleStateDto(
                id = module.id,
                enabled = module.enabled,
                settings = module.settings.all.associate { it.id to encodeJson(it) },
            )
        }
        val hudLayout = modules.hudModules.map { m: HudModule ->
            HudLayoutDto(
                elementId = m.hud.id,
                anchor = m.hud.anchor.id,
                offsetX = m.hud.offsetX,
                offsetY = m.hud.offsetY,
                scale = m.hud.scale,
            )
        }
        return base.copy(
            modules = moduleStates,
            hud = hudLayout,
            modifiedAtMs = System.currentTimeMillis(),
        )
    }

    /** Applies a profile to the live module set (missing modules keep defaults). */
    fun apply(data: ProfileData, modules: ModuleManager, logger: VLogger) {
        for (module in modules.all) {
            val state = data.modules.firstOrNull { it.id == module.id }
            module.settings.resetAll()
            state?.settings?.forEach { (settingId, element) ->
                val setting = module.settings.byId(settingId) ?: return@forEach
                if (!setting.decodeJson(element)) {
                    logger.w(TAG, "Invalid persisted value for ${module.id}.$settingId, using default")
                }
            }
            module.setEnabled(state?.enabled ?: false)
        }
        for (hudModule in modules.hudModules) {
            val layout = data.hud.firstOrNull { it.elementId == hudModule.hud.id } ?: continue
            hudModule.hud.anchor = HudAnchor.byId(layout.anchor)
            hudModule.hud.offsetX = layout.offsetX
            hudModule.hud.offsetY = layout.offsetY
            hudModule.hud.scale = layout.scale.coerceIn(0.5f, 3f)
        }
    }

    private const val TAG = "ProfileSync"
}

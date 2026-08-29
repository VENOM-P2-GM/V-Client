package dev.vclient.core.module

import dev.vclient.core.logging.VLogger
import dev.vclient.core.runtime.ClientHooks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ModuleError(
    val moduleId: String,
    val message: String,
    val atMs: Long,
)

/**
 * Owns every registered module and drives their per-frame ticks.
 *
 * Crash protection at module granularity: every tick (and every HUD draw)
 * runs through [guard]. A module that throws is logged; if it throws
 * [ERROR_LIMIT] consecutive times it is force-disabled and reported through
 * [errors] (V Menu shows a "safe mode" notice), so one broken module can
 * never take down the whole overlay.
 */
class ModuleManager(private val logger: VLogger) {

    private val modules = mutableListOf<Module>()
    private val consecutiveErrors = HashMap<String, Int>()

    private val _errors = MutableStateFlow<List<ModuleError>>(emptyList())
    val errors: StateFlow<List<ModuleError>> get() = _errors

    /** Notified after register() so new setting values auto-save. */
    var onModuleChanged: ((Module) -> Unit)? = null

    val all: List<Module> get() = modules.toList()
    val hudModules: List<HudModule> get() = modules.filterIsInstance<HudModule>()
    val enabledCount: Int get() = modules.count { it.enabled }

    fun register(module: Module) {
        check(byId(module.id) == null) { "Module id already registered: ${module.id}" }
        modules.add(module)
        modules.sortWith(compareBy({ it.category.ordinal }, { it.name }))
        // Setting changes funnel into the same "module changed" pipeline
        // (auto-save + overlay reactions) as enable/disable toggles.
        module.settings.all.forEach { setting ->
            setting.onChange { onModuleChanged?.invoke(module) }
        }
        logger.d(TAG, "Registered module ${module.id} (${module.name})")
    }

    fun byId(id: String): Module? = modules.firstOrNull { it.id == id }

    fun byCategory(category: ModuleCategory): List<Module> =
        modules.filter { it.category == category }

    fun tick(ctx: TickContext) {
        for (module in modules) {
            if (!module.enabled) continue
            guard(module) { module.onTick(ctx) }
        }
    }

    /** Runs [block] protected; used for both ticks and HUD element drawing. */
    fun guard(module: Module, block: () -> Unit) {
        try {
            block()
            consecutiveErrors.remove(module.id)
        } catch (t: Throwable) {
            val count = (consecutiveErrors[module.id] ?: 0) + 1
            consecutiveErrors[module.id] = count
            logger.e(TAG, "Module '${module.id}' error #$count", t)
            pushError(ModuleError(module.id, t.message ?: t.javaClass.simpleName, System.currentTimeMillis()))
            if (count >= ERROR_LIMIT) {
                module.setEnabled(false)
                consecutiveErrors.remove(module.id)
                logger.w(TAG, "Module '${module.id}' auto-disabled after $ERROR_LIMIT consecutive errors")
            }
        }
    }

    fun resetAll() {
        for (module in modules) {
            module.setEnabled(false)
            module.settings.resetAll()
        }
        _errors.value = emptyList()
    }

    private fun pushError(error: ModuleError) {
        _errors.value = (_errors.value + error).takeLast(20)
    }

    companion object {
        const val TAG = "ModuleManager"
        const val ERROR_LIMIT = 5
    }
}

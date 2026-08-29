package dev.vclient.core.runtime

import dev.vclient.core.module.Module

/**
 * Tiny event hub that decouples the engine layer (modules, profiles) from the
 * overlay layer (windows, controllers).
 *
 * Modules dispatch [dispatchModuleChanged] whenever their enabled-state or a
 * setting changes. Subscribers:
 *  - ProfileManager  -> schedule an auto-save of the active profile.
 *  - OverlayService  -> react to effect modules (zoom / filter / blur).
 *
 * Overlay-side request channels (zoomRequest, ...) are null while the overlay
 * service is not running, so modules degrade gracefully when V Menu is used
 * from the launcher without an active session.
 */
object ClientHooks {

    private val moduleChangedListeners = mutableListOf<(Module) -> Unit>()

    fun subscribeToModuleChanges(listener: (Module) -> Unit): () -> Unit {
        moduleChangedListeners.add(listener)
        return { moduleChangedListeners.remove(listener) }
    }

    fun dispatchModuleChanged(module: Module) {
        moduleChangedListeners.toList().forEach { runCatching { it(module) } }
    }

    /** Effect-channel: enables/disables camera magnification for the Zoom module. */
    @Volatile var zoomRequest: ((Boolean) -> Unit)? = null

    /** Effect-channel: enables/disables the Fullbright screen filter window. */
    @Volatile var filterRequest: ((Boolean) -> Unit)? = null

    /** Effect-channel: enables/disables the Motion Blur window. */
    @Volatile var blurRequest: ((Boolean) -> Unit)? = null

    @Volatile var openVMenu: (() -> Unit)? = null
    @Volatile var toggleEditor: (() -> Unit)? = null
}

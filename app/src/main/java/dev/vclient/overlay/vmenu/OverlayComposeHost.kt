package dev.vclient.overlay.vmenu

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.vclient.R

/**
 * Hosts a Compose hierarchy inside a Service-owned overlay window.
 *
 * ComposeView normally relies on the Activity's view-tree owners; here we
 * provide a minimal LifecycleOwner + SavedStateRegistryOwner and drive them
 * through the standard event sequence. dispose() must be called before the
 * view is removed from the WindowManager to release the recomposer cleanly.
 */
class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        moveTo(Lifecycle.State.RESUMED)
    }

    private fun moveTo(state: Lifecycle.State) {
        while (lifecycleRegistry.currentState < state) {
            when (lifecycleRegistry.currentState) {
                Lifecycle.State.INITIALIZED -> lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                Lifecycle.State.CREATED -> lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                Lifecycle.State.STARTED -> lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                else -> return
            }
        }
    }

    fun destroy() {
        runCatching {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
}

class OverlayComposeHost(context: Context) {

    val owner = OverlayLifecycleOwner()

    val view: ComposeView = ComposeView(ContextThemeWrapper(context, R.style.Theme_VClient)).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
    }

    fun dispose() {
        runCatching { view.disposeComposition() }
        owner.destroy()
    }
}

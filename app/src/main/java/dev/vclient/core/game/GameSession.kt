package dev.vclient.core.game

import dev.vclient.core.AppSettings
import dev.vclient.core.logging.VLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the current [GameState] and the active data source. The HUD render
 * loop calls [poll] once per frame; the result is cached in [state] for
 * Compose UI (V Menu) and modules.
 *
 * [gameFocused] is null while game-focus detection is unavailable (the
 * optional accessibility service is off); the overlay then assumes the game
 * is focused.
 */
class GameSession(
    private val settings: AppSettings,
    private val logger: VLogger,
) {

    private val _state = MutableStateFlow(GameState.EMPTY)
    val state: StateFlow<GameState> get() = _state

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> get() = _active

    private val _gameFocused = MutableStateFlow<Boolean?>(null)
    val gameFocused: StateFlow<Boolean?> get() = _gameFocused

    var source: GameDataSource = ManualDataSource(settings)
        private set

    val sourceKind: DataSourceKind get() = source.kind
    val demo: Boolean get() = source.kind == DataSourceKind.DEMO

    fun start(): DataSourceKind {
        _active.value = true
        val kind = applyDataSource(settings.dataSourceKind)
        logger.i(TAG, "Session started (source=${kind.id})")
        return kind
    }

    fun stop() {
        _active.value = false
        _state.value = GameState.EMPTY
        _gameFocused.value = null
        InputTracker.clearAllKeys()
        logger.i(TAG, "Session stopped")
    }

    fun applyDataSource(kindId: String): DataSourceKind {
        val kind = DataSourceKind.byId(kindId)
        source = when (kind) {
            DataSourceKind.MANUAL -> ManualDataSource(settings)
            DataSourceKind.DEMO -> DemoDataSource()
            DataSourceKind.BRIDGE -> BridgeDataSource()
        }
        if (!source.available) {
            logger.w(TAG, "Data source '${kind.id}' unavailable, falling back to MANUAL")
            source = ManualDataSource(settings)
            return DataSourceKind.MANUAL
        }
        return source.kind
    }

    fun poll(nowMs: Long): GameState {
        if (!_active.value) return _state.value
        val next = runCatching { source.poll(nowMs) }.getOrElse {
            GameState(source = "error", timestampMs = nowMs)
        }
        _state.value = next
        return next
    }

    /** Called by the accessibility service (true/false) or with null when disconnected. */
    fun setGameFocused(focused: Boolean?) {
        if (_gameFocused.value != focused) {
            _gameFocused.value = focused
            if (focused != null) logger.d(TAG, "Game focus: $focused")
        }
    }

    companion object { const val TAG = "GameSession" }
}

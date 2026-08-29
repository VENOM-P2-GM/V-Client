package dev.vclient.core

import android.content.Context
import dev.vclient.core.config.ProfileManager
import dev.vclient.core.config.ProfileSync
import dev.vclient.core.game.GameLauncher
import dev.vclient.core.game.GameSession
import dev.vclient.core.logging.CrashReporter
import dev.vclient.core.logging.VLogger
import dev.vclient.core.isolation.IsolatedPaths
import dev.vclient.core.module.Module
import dev.vclient.core.module.ModuleManager
import dev.vclient.core.runtime.ClientHooks
import dev.vclient.core.runtime.NativeBridge
import dev.vclient.core.signature.SignatureManager
import dev.vclient.core.version.VersionRegistry
import kotlinx.serialization.json.Json

/**
 * Composition root. Constructed once from [dev.vclient.VClientApp]; wires every
 * subsystem together and owns their lifecycle order:
 *
 *   isolation paths -> logger -> crash reporter -> native runtime ->
 *   version registry -> modules -> profiles -> signature manager -> session.
 */
class VClientCore private constructor(appContext: Context) {

    val appContext: Context = appContext.applicationContext

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    val paths: IsolatedPaths = IsolatedPaths(appContext).ensure()
    val logger: VLogger = VLogger.from(paths)
    val crashReporter: CrashReporter = CrashReporter(paths.crashes, logger)
    val settings: AppSettings = AppSettings(appContext)

    val modules: ModuleManager = ModuleManager(logger)
    val versions: VersionRegistry = VersionRegistry(logger)
    val signatures: SignatureManager = SignatureManager(appContext, paths, logger)
    val session: GameSession = GameSession(settings, logger)
    val launcher: GameLauncher = GameLauncher(appContext, logger)
    val profiles: ProfileManager = ProfileManager(appContext, paths, logger, json)

    val nativeAvailable: Boolean
    /** True when the last sessions crashed repeatedly: start with modules disabled. */
    val safeMode: Boolean

    init {
        logger.i(TAG, "V Client core starting (isolated root: ${paths.root.absolutePath})")
        crashReporter.install()
        nativeAvailable = NativeBridge.initialize(paths.root.absolutePath, logger)
        versions.loadBundled(appContext)
        versions.loadUserOverrides(paths.compat)

        dev.vclient.modules.ModuleRegistry.createAll().forEach { modules.register(it) }

        // Module changes (settings AND enable/disable toggles) -> debounced
        // auto-save of the active profile.
        modules.onModuleChanged = { profiles.markDirty() }
        ClientHooks.subscribeToModuleChanges { profiles.markDirty() }
        profiles.onAutoCapture = { ProfileSync.capture(modules, profiles.activeData.value) }

        safeMode = crashReporter.isCrashLoop()
        val active = profiles.loadInitial(settings)
        if (safeMode) {
            logger.w(TAG, "Crash loop detected — starting in SAFE MODE (all modules disabled)")
            ProfileSync.apply(active.copy(modules = active.modules.map { it.copy(enabled = false) }), modules, logger)
        } else {
            ProfileSync.apply(active, modules, logger)
        }

        signatures.verifyInstalled()
        logger.i(TAG, "Core ready: ${modules.all.size} modules, profile '${profiles.activeName.value}', safeMode=$safeMode")
    }

    fun shutdown() {
        profiles.flush()
        session.stop()
        NativeBridge.shutdown()
    }

    companion object {
        private const val TAG = "VClientCore"

        lateinit var instance: VClientCore
            private set

        val core: VClientCore get() = instance

        fun create(context: Context): VClientCore {
            check(!this::instance.isInitialized) { "VClientCore already initialized" }
            val core = VClientCore(context)
            instance = core
            return core
        }
    }
}

package dev.vclient.core.game

import android.content.Context
import android.content.Intent
import dev.vclient.core.logging.VLogger

/**
 * Thin wrapper around launching the *stock* Minecraft app. V Client never
 * modifies, repackages or re-signs the game — it starts the installed
 * package's own launch activity and simply keeps its overlay running above it.
 */
class GameLauncher(private val context: Context, private val logger: VLogger) {

    fun isInstalled(): Boolean = installedVersionName() != null

    fun installedVersionName(): String? = runCatching {
        context.packageManager.getPackageInfo(MC_PACKAGE, 0).versionName
    }.getOrNull()

    fun launchIntent(): Intent? =
        context.packageManager.getLaunchIntentForPackage(MC_PACKAGE)

    /** Launches Minecraft; returns false when the launch intent can't be resolved. */
    fun launch(): Boolean {
        val intent = launchIntent()
        if (intent == null) {
            logger.w(TAG, "No launch intent for $MC_PACKAGE")
            return false
        }
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            logger.i(TAG, "Launched $MC_PACKAGE")
            true
        }.getOrElse {
            logger.e(TAG, "Failed to launch Minecraft", it)
            false
        }
    }

    companion object {
        const val TAG = "GameLauncher"
        const val MC_PACKAGE = "com.mojang.minecraftpe"
    }
}

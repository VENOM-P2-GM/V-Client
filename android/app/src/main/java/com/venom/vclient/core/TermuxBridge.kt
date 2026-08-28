package com.venom.vclient.core

import android.content.Context
import android.content.Intent

/**
 * On Android, Minecraft Java Edition runs inside Termux.
 * We hand Termux the full launch command (built for the isolated profile env)
 * via the documented com.termux.RUN_COMMAND intent.
 */
object TermuxBridge {
    private const val TERMUX = "com.termux"

    fun isInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(TERMUX, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun runCommand(ctx: Context, command: String): Boolean {
        return try {
            val intent = Intent("com.termux.RUN_COMMAND")
                .putExtra("com.termux.RUN_COMMAND_NAME", command)
                .setPackage(TERMUX)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

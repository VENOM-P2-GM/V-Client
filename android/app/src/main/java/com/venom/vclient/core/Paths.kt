package com.venom.vclient.core

import android.content.Context
import java.io.File

/**
 * Isolated environment paths.
 * Every profile owns a complete, self-contained game environment:
 * nothing is shared between profiles (files, session, saves, logs).
 */
object Paths {
    fun profilesDir(ctx: Context): File = File(ctx.filesDir, "profiles").apply { mkdirs() }

    fun profileDir(ctx: Context, id: String): File = File(profilesDir(ctx), id)

    fun envOf(root: File): EnvPaths = EnvPaths(root)
}

class EnvPaths(val root: File) {
    val envJson: File = File(root, "env.json")
    val game: File = File(root, "game")
    val versions: File = File(game, "versions")
    val libraries: File = File(game, "libraries")
    val assets: File = File(game, "assets")
    val assetIndexes: File = File(assets, "indexes")
    val assetsObjects: File = File(assets, "objects")
    val saves: File = File(root, "saves")
    val mods: File = File(root, "mods")
    val resourcepacks: File = File(root, "resourcepacks")
    val shaderpacks: File = File(root, "shaderpacks")
    val config: File = File(root, "config")
    val options: File = File(root, "options.txt")
    val auth: File = File(root, "auth")
    val session: File = File(auth, "session.json")
    val logs: File = File(root, "logs")
    val gameLog: File = File(logs, "game.log")
    val reports: File = File(root, "reports")

    fun ensure() {
        listOf(
            game, versions, libraries, assets, assetIndexes, assetsObjects,
            saves, mods, resourcepacks, shaderpacks, config, auth, logs, reports
        ).forEach { it.mkdirs() }
    }
}

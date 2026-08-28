package com.venom.vclient.core

import org.json.JSONObject
import java.io.File

/**
 * Builds the full JVM argv from a Mojang version JSON.
 * Supports modern (arguments.jvm / arguments.game) and legacy (minecraftArguments) formats.
 */
object JavaLaunch {
    private const val SEP = ":"

    private fun subst(s: String, map: Map<String, String>): String =
        Regex("\\$\\{(\\w+)\\}").replace(s) { m -> map[m.groupValues[1]] ?: "" }

    fun build(
        version: JSONObject,
        gameDir: File,
        assetsRoot: File,
        assetsIndexName: String,
        nativesDir: File,
        libraryDir: File,
        libraries: List<File>,
        clientJar: File,
        memMax: Int,
        memMin: Int,
        account: Account,
        extraJvm: List<String> = emptyList()
    ): List<String> {
        val map = mutableMapOf(
            "auth_player_name" to account.name,
            "version_name" to (version.optString("id", "v-client")),
            "game_directory" to gameDir.absolutePath,
            "assets_root" to assetsRoot.absolutePath,
            "assets_index_name" to assetsIndexName,
            "auth_uuid" to account.uuid,
            "auth_access_token" to account.accessToken,
            "auth_session" to account.accessToken,
            "user_type" to (if (account.type == "microsoft") "msa" else "mojang"),
            "version_type" to (version.optString("type", "release")),
            "user_properties" to "uuid=${account.uuid}:username=${account.name}:type=offline",
            "clientid" to "",
            "xuid" to "",
            "authentication_server" to "https://authserver.mojang.com",
            "sessionid" to "",
            "authorization_token" to "",
            "launcher_name" to "V Client",
            "launcher_version" to "1.0.0",
            "classpath" to (libraries + clientJar).joinToString(SEP) { it.absolutePath },
            "natives_directory" to nativesDir.absolutePath,
            "library_directory" to libraryDir.absolutePath,
            "max_mem" to "${memMax}M",
            "min_mem" to "${memMin}M"
        )

        val jvm = mutableListOf<String>()
        val argsJvm = version.optJSONObject("arguments")?.optJSONArray("jvm")
        if (argsJvm != null) {
            for (i in 0 until argsJvm.length()) {
                val el = argsJvm.opt(i)
                val value: String? = when (el) {
                    is JSONObject -> if (Mojang.rulesAllow(el.optJSONArray("rules"))) el.optString("value").takeIf { it.isNotBlank() } else null
                    is String -> el
                    else -> null
                } ?: continue
                val v = subst(value, map)
                if (v.isNotBlank()) jvm.add(v)
            }
        } else {
            jvm.add("-Djava.library.path=${nativesDir.absolutePath}")
            jvm.add("-Dminecraft.launcher.brand=v-client")
            jvm.add("-cp")
            jvm.add(map["classpath"] ?: "")
        }
        if (jvm.none { it.startsWith("-Xmx") }) {
            jvm.add(0, "-Xms${memMin}M")
            jvm.add(0, "-Xmx${memMax}M")
        }
        jvm.addAll(extraJvm)

        val argsGame = version.optJSONObject("arguments")?.optJSONArray("game")
        val gameArgs: List<String> = if (argsGame != null) {
            (0 until argsGame.length()).map { subst(argsGame.getString(it), map) }
        } else {
            version.optString("minecraftArguments", "")
                .split(" ")
                .filter { it.isNotBlank() }
                .map { subst(it, map) }
        }

        val mainClass = version.optString("mainClass")
        if (mainClass.isBlank()) throw IllegalStateException("version JSON has no mainClass")
        return jvm + mainClass + gameArgs.filter { it.isNotBlank() }
    }
}

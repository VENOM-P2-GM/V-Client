package com.venom.vclient.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Minecraft (Mojang) metadata helpers: manifest, version JSON,
 * library resolution, OS rules, asset URLs.
 */
object Mojang {
    const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    const val ASSET_BASE = "https://resources.download.minecraft.net/"

    private val osArch: String = System.getProperty("os.arch", "aarch64")

    data class LibSpec(val url: String, val sha1: String?, val size: Long, val rel: String)

    fun mavenPath(name: String): String? {
        val p = name.split(":")
        if (p.size < 3) return null
        val g = p[0]
        val a = p[1]
        val v = p[2]
        val file = if (p.size >= 4) "$a-$v-${p[3]}.jar" else "$a-$v.jar"
        return "${g.replace('.', '/')}/$a/$v/$file"
    }

    fun rulesAllow(rules: JSONArray?): Boolean {
        if (rules == null) return true
        var allow = false
        var dis = false
        for (i in 0 until rules.length()) {
            val r = rules.getJSONObject(i)
            val os = r.optJSONObject("os")
            val osOk = os == null ||
                (os.optString("name") == "linux" &&
                    (os.optString("arch", "all") == "all" || os.optString("arch") == osArch))
            if (osOk) {
                when (r.optString("action")) {
                    "allow" -> allow = true
                    "disallow" -> dis = true
                }
            }
        }
        return allow && !dis
    }

    fun clientSide(lib: JSONObject): Boolean {
        if (!lib.optBoolean("clientreq", true)) return false
        return rulesAllow(lib.optJSONArray("rules"))
    }

    fun artifact(lib: JSONObject): LibSpec? {
        val dl = lib.optJSONObject("downloads")?.optJSONObject("artifact")
        if (dl != null) {
            val rel = dl.optString("path").ifEmpty { mavenPath(lib.optString("name")) } ?: return null
            return LibSpec(
                url = dl.optString("url"),
                sha1 = dl.optString("sha1").takeIf { it.isNotBlank() },
                size = dl.optLong("size"),
                rel = rel
            )
        }
        val base = lib.optString("url")
        if (base.isNotBlank()) {
            val rel = mavenPath(lib.optString("name")) ?: return null
            return LibSpec(url = base.trimEnd('/') + "/" + rel, sha1 = null, size = 0, rel = rel)
        }
        return null
    }

    fun natives(lib: JSONObject): LibSpec? {
        val n = lib.opt("natives") ?: return null
        val classifier: String? = when (n) {
            is String -> n.replace("\${arch}", if (osArch == "x86_64") "64" else osArch)
            is JSONObject -> n.optString("linux").takeIf { it.isNotBlank() }
            is JSONArray -> "natives-linux"
            else -> null
        } ?: return null
        val cls = lib.optJSONObject("downloads")?.optJSONObject("classifiers")?.optJSONObject(classifier)
        if (cls != null) {
            val rel = cls.optString("path").ifEmpty { mavenPath(lib.optString("name") + ":" + classifier) } ?: return null
            return LibSpec(
                url = cls.optString("url"),
                sha1 = cls.optString("sha1").takeIf { it.isNotBlank() },
                size = cls.optLong("size"),
                rel = rel
            )
        }
        val base = lib.optString("url")
        if (base.isNotBlank()) {
            val rel = mavenPath(lib.optString("name") + ":" + classifier) ?: return null
            return LibSpec(url = base.trimEnd('/') + "/" + rel, sha1 = null, size = 0, rel = rel)
        }
        return null
    }

    fun assetUrl(hash: String): String = ASSET_BASE + hash.substring(0, 2) + "/" + hash
}

package com.venom.vclient.core

import android.content.Context
import org.json.JSONObject
import java.io.File

data class Profile(
    val id: String,
    val name: String,
    val engine: String,
    val version: String,
    val description: String,
    val createdAt: Long,
    val lastLaunch: Long?,
    val launchCount: Int,
    val memoryMax: Int,
    val memoryMin: Int
)

class ProfilesRepository(private val ctx: Context) {
    private val dir: File = Paths.profilesDir(ctx)

    fun list(): List<Profile> {
        val out = mutableListOf<Profile>()
        dir.listFiles()?.filter { it.isDirectory }?.forEach { d ->
            val j = Json.read(File(d, "env.json")) ?: return@forEach
            out.add(
                Profile(
                    id = d.name,
                    name = j.optString("name", d.name),
                    engine = j.optString("engine", "vengine"),
                    version = j.optString("version", "1.1.0"),
                    description = j.optString("description", ""),
                    createdAt = j.optLong("createdAt", 0),
                    lastLaunch = if (j.isNull("lastLaunch")) null else j.optLong("lastLaunch"),
                    launchCount = j.optInt("launchCount", 0),
                    memoryMax = j.optInt("memoryMax", 4096),
                    memoryMin = j.optInt("memoryMin", 1024)
                )
            )
        }
        return out.sortedBy { it.createdAt }
    }

    fun get(id: String): Profile? = list().firstOrNull { it.id == id }

    fun envOf(id: String): EnvPaths = Paths.envOf(Paths.profileDir(ctx, id))

    fun create(name: String, engine: String, version: String, description: String, memMax: Int, memMin: Int): Profile {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "profile" }
        var id = base
        var guard = 0
        while (File(dir, id).exists()) {
            guard++
            if (guard > 50) throw IllegalStateException("could not allocate profile id")
            id = "$base-${System.nanoTime().toString(16).take(4)}"
        }
        val p = Profile(
            id = id,
            name = name.trim(),
            engine = engine,
            version = version,
            description = description,
            createdAt = System.currentTimeMillis(),
            lastLaunch = null,
            launchCount = 0,
            memoryMax = memMax,
            memoryMin = memMin
        )
        envOf(id).ensure()
        save(p)
        return p
    }

    private fun save(p: Profile) {
        Json.write(
            envOf(p.id).envJson,
            JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("engine", p.engine)
                put("version", p.version)
                put("description", p.description)
                put("isolated", true)
                put("createdAt", p.createdAt)
                put("lastLaunch", p.lastLaunch ?: JSONObject.NULL)
                put("launchCount", p.launchCount)
                put("memoryMax", p.memoryMax)
                put("memoryMin", p.memoryMin)
            }
        )
    }

    fun touchLaunch(id: String) {
        val f = envOf(id).envJson
        val j = Json.read(f) ?: return
        j.put("lastLaunch", System.currentTimeMillis())
        j.put("launchCount", j.optInt("launchCount", 0) + 1)
        Json.write(f, j)
    }

    fun delete(id: String) {
        if (!Regex("^[a-zA-Z0-9][a-zA-Z0-9-]{0,63}$").matches(id)) return
        Paths.profileDir(ctx, id).deleteRecursively()
    }
}

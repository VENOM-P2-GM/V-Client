package com.venom.vclient.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/**
 * Minecraft Bedrock support.
 *
 * Each bedrock profile owns an isolated environment:
 *   bedrock/
 *   ├── behavior_packs/<uuid>/   its own behavior packs
 *   ├── resource_packs/<uuid>/   its own resource packs
 *   ├── templates/
 *   ├── addons/                  raw .mcaddon/.mcpack files (for deploy to the game)
 *   ├── config/game.json         level/pack configuration
 *   └── packs.json               registry
 *
 * Deploy = open the raw pack file with the Minecraft app (ACTION_VIEW) —
 * the same non-root flow the game itself uses to import addons.
 */
object Bedrock {
    const val MC_PACKAGE = "com.mojang.minecraftpe"
    private const val PROVIDER_AUTHORITY = "com.venom.vclient.fileprovider"

    class Pack(
        val uuid: String,
        val name: String,
        val type: String, // "behavior" | "resource" | "template"
        val version: String,
        val enabled: Boolean,
        val file: String? // raw file name in addons/
    )

    fun bedrockDir(env: EnvPaths): File = File(env.root, "bedrock").apply { mkdirs() }

    fun packsDir(env: EnvPaths, type: String): File =
        File(bedrockDir(env), type + "_packs").apply { mkdirs() }

    private fun registryFile(env: EnvPaths): File = File(bedrockDir(env), "packs.json")

    fun listPacks(env: EnvPaths): List<Pack> {
        val arr = Json.readArray(registryFile(env))
        val out = mutableListOf<Pack>()
        for (i in 0 until arr.length()) {
            val j = arr.getJSONObject(i)
            out.add(
                Pack(
                    uuid = j.optString("uuid"),
                    name = j.optString("name"),
                    type = j.optString("type", "behavior"),
                    version = j.optString("version", "1.0.0"),
                    enabled = j.optBoolean("enabled", true),
                    file = j.optString("file").takeIf { it.isNotBlank() }
                )
            )
        }
        return out
    }

    private fun savePacks(env: EnvPaths, packs: List<Pack>) {
        val arr = JSONArray()
        packs.forEach {
            arr.put(
                JSONObject().apply {
                    put("uuid", it.uuid)
                    put("name", it.name)
                    put("type", it.type)
                    put("version", it.version)
                    put("enabled", it.enabled)
                    put("file", it.file ?: "")
                }
            )
        }
        Json.write(registryFile(env), arr)
    }

    fun setEnabled(env: EnvPaths, uuid: String, enabled: Boolean) {
        val packs = listPacks(env).map { if (it.uuid == uuid) it.copy(enabled = enabled) else it }
        savePacks(env, packs)
    }

    fun removePack(env: EnvPaths, uuid: String) {
        val pack = listPacks(env).firstOrNull { it.uuid == uuid } ?: return
        File(packsDir(env, pack.type), uuid).deleteRecursively()
        pack.file?.let { File(bedrockDir(env), "addons/" + it).delete() }
        savePacks(env, listPacks(env).filterNot { it.uuid == uuid })
    }

    /**
     * Install a .mcpack / .mpack / .mcaddon into the isolated environment.
     * Returns the packs that were added.
     */
    fun installPacks(env: EnvPaths, file: File, onLog: (String) -> Unit): List<Pack> {
        val added = mutableListOf<Pack>()
        if (file.name.endsWith(".mcaddon", ignoreCase = true)) {
            ZipFile(file).use { zf ->
                File(bedrockDir(env), "addons").mkdirs()
                file.copyTo(File(bedrockDir(env), "addons/" + file.name), overwrite = true)
                val tmpDir = File(bedrockDir(env), "tmp").apply { mkdirs() }
                val entries = zf.entries()
                val inners = mutableListOf<File>()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    val n = e.name.substringAfterLast('/')
                    if (n.endsWith(".mcpack", true) || n.endsWith(".mpack", true)) {
                        val tmp = File(tmpDir, n)
                        zf.getInputStream(e).use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
                        inners.add(tmp)
                    }
                }
                for (inner in inners) {
                    try {
                        added.addAll(installPacks(env, inner, onLog))
                    } catch (ex: Exception) {
                        onLog("[pack] skipped ${inner.name}: ${ex.message}")
                    }
                    inner.delete()
                }
                tmpDir.deleteRecursively()
            }
        } else {
            ZipFile(file).use { zf ->
                val manifestEntry = zf.getEntry("manifest.json")
                    ?: throw IllegalStateException("not a valid bedrock pack (no manifest.json)")
                val manifestText = zf.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
                val manifest = JSONObject(manifestText)
                val header = manifest.optJSONObject("header")
                    ?: throw IllegalStateException("bad manifest (no header)")
                val uuid = header.optString("uuid", "")
                if (uuid.isBlank()) throw IllegalStateException("manifest has no header.uuid")
                val name = header.optString("name", uuid)
                val verArr = header.optJSONArray("version")
                val version = (0..2).joinToString(".") { i -> (verArr?.optInt(i, 0) ?: 0).toString() }

                // pack type from modules
                var type = "behavior"
                manifest.optJSONArray("modules")?.let { modules ->
                    for (i in 0 until modules.length()) {
                        when (modules.getJSONObject(i).optString("type", "")) {
                            "resources" -> type = "resource"
                            "world_template" -> type = "template"
                        }
                    }
                }

                // the pack contents live under the first top-level folder
                var packFolder = ""
                val es = zf.entries()
                while (es.hasMoreElements()) {
                    val e = es.nextElement()
                    if (e.isDirectory) {
                        val top = e.name.substringBefore('/')
                        if (top.isNotBlank()) {
                            packFolder = top
                            break
                        }
                    }
                }

                val target = File(packsDir(env, type), uuid)
                if (target.exists()) target.deleteRecursively()
                target.mkdirs()
                File(target, "manifest.json").writeText(manifestText)

                val es2 = zf.entries()
                while (es2.hasMoreElements()) {
                    val e = es2.nextElement()
                    val n = e.name
                    if (n == "manifest.json") continue
                    val sub =
                        if (packFolder.isNotEmpty() && n.startsWith(packFolder + "/")) n.substring(packFolder.length + 1)
                        else n
                    if (sub.isBlank()) continue
                    val out = File(target, sub)
                    if (!out.canonicalFile.path.startsWith(target.canonicalFile.path)) continue
                    if (e.isDirectory) {
                        out.mkdirs()
                        continue
                    }
                    out.parentFile?.mkdirs()
                    zf.getInputStream(e).use { ins -> out.outputStream().use { ins.copyTo(it) } }
                }

                // keep the raw file so it can be deployed (opened) by the game
                File(bedrockDir(env), "addons").mkdirs()
                file.copyTo(File(bedrockDir(env), "addons/" + file.name), overwrite = true)

                onLog("[pack] $name ($uuid) v$version → ${type}_packs/")
                val packs = listPacks(env).filterNot { it.uuid == uuid }
                val addedPack = Pack(uuid, name, type, version, true, file.name)
                savePacks(env, packs + addedPack)
                added.add(addedPack)
            }
        }
        return added
    }

    /** Content Uri (via FileProvider) of the pack's raw file, ready to be opened by the game. */
    fun deployUri(ctx: Context, env: EnvPaths, pack: Pack): Uri? {
        val f = File(bedrockDir(env), "addons/" + (pack.file ?: pack.uuid + ".mcpack"))
        return if (f.isFile) FileProvider.getUriForFile(ctx, PROVIDER_AUTHORITY, f) else null
    }

    /** Open a pack file with Minecraft (the game imports it). */
    fun openWithMinecraft(ctx: Context, uri: Uri): Boolean = try {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/octet-stream")
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    fun isMinecraftInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(MC_PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun openMinecraft(ctx: Context): Boolean = try {
        val intent = ctx.packageManager.getLaunchIntentForPackage(MC_PACKAGE)
            ?: return openMarket(ctx)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    fun openMarket(ctx: Context): Boolean = try {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + MC_PACKAGE)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
        true
    } catch (e: Exception) {
        try {
            ctx.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + MC_PACKAGE)
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
            true
        } catch (e2: Exception) {
            false
        }
    }
}

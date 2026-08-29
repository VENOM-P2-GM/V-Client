package dev.vclient.core.isolation

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Single source of truth for every path V Client writes to.
 *
 * Isolation contract:
 *  - V Client NEVER writes to Minecraft's storage (its app data dir, its
 *    external files, or anywhere inside the APK). We only read package
 *    metadata (version + signing certs) through PackageManager.
 *  - Everything we produce lives under one root: `<our sandbox>/files/vclient`.
 *    Deleting that directory resets V Client completely.
 */
class IsolatedPaths(context: Context) {

    val root: File = File(context.filesDir, "vclient")

    /** One directory per profile (profile.json + future per-profile data). */
    val profiles: File get() = File(root, "profiles")

    /** File logs: app-YYYYMMDD.log, native.log. */
    val logs: File get() = File(root, "logs")

    /** Uncaught Kotlin/Java crash reports + native tombstones + history. */
    val crashes: File get() = File(root, "crashes")

    /** Throw-away data (HUD editor previews, exported temp files...). */
    val cache: File get() = File(root, "cache")

    /** User-imported version compatibility overrides. */
    val compat: File get() = File(root, "compat")

    /** APK signature trust store (digests we have explicitly trusted). */
    val signatures: File get() = File(root, "signatures")

    fun ensure(): IsolatedPaths {
        for (dir in listOf(root, profiles, logs, crashes, cache, compat, signatures)) {
            if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
                throw IOException("Cannot create isolated directory: $dir")
            }
        }
        return this
    }

    fun wipeCache() {
        cache.listFiles()?.forEach { it.deleteRecursively() }
    }

    companion object {
        /** Atomic file write: temp file + rename, so crashes can't corrupt configs. */
        fun atomicWrite(file: File, content: String) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(file)) {
                // Fall back for filesystems that can't atomic-rename.
                file.writeText(content)
                tmp.delete()
            }
        }

        fun readTextOrNull(file: File): String? =
            if (file.isFile) runCatching { file.readText() }.getOrNull() else null
    }
}

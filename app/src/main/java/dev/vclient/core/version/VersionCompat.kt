package dev.vclient.core.version

import android.content.Context
import dev.vclient.core.isolation.IsolatedPaths
import dev.vclient.core.logging.VLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Parsed Minecraft Bedrock version with correct segment ordering:
 * 1.21.100 > 1.21.9 > 1.21 > 1.20.51.
 */
data class MinecraftVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val raw: String,
) : Comparable<MinecraftVersion> {

    val rank: Int get() = major * 1_000_000 + minor * 1_000 + patch
    override fun compareTo(other: MinecraftVersion): Int = rank.compareTo(other.rank)
    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?")

        /** Parses "1.21.100", "1.21.100.06" (build suffix ignored), etc. */
        fun parse(raw: String?): MinecraftVersion? {
            if (raw.isNullOrBlank()) return null
            val match = pattern.find(raw.trim()) ?: return null
            return MinecraftVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toIntOrNull() ?: 0,
                raw = raw.trim(),
            )
        }
    }
}

enum class CompatStatus(val id: String, val display: String, val detail: String) {
    SUPPORTED("supported", "Supported", "Validated release."),
    EXPERIMENTAL("experimental", "Experimental", "Not fully validated yet."),
    UNSUPPORTED("unsupported", "Unsupported", "Known issues on this release."),
    UNKNOWN("unknown", "Unknown", "Not in the registry yet.");

    companion object {
        fun byId(id: String?): CompatStatus =
            entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}

data class VersionInfo(
    val version: String,
    val parsed: MinecraftVersion?,
    val status: CompatStatus,
    val note: String = "",
)

@Serializable
private data class VersionEntryDto(val version: String, val status: String = "experimental", val note: String = "")

@Serializable
private data class VersionListDto(val formatVersion: Int = 1, val versions: List<VersionEntryDto> = emptyList())

/**
 * Version compatibility registry.
 *
 * Overlay-based features are inherently version-independent (they never hook
 * the game), so the registry primarily records *validation state*: which
 * releases V Client has been tested against. Entries ship in
 * assets/compat/versions.json and can be extended/overridden by a
 * user-imported user_versions.json inside the isolated compat/ directory —
 * which is how new Minecraft versions gain "supported" status without an app
 * update.
 */
class VersionRegistry(private val logger: VLogger) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var entries: List<VersionInfo> = emptyList()

    val all: List<VersionInfo> get() = entries

    fun loadBundled(context: Context) {
        val text = runCatching {
            context.assets.open(BUNDLED_PATH).bufferedReader().use { it.readText() }
        }.getOrElse {
            logger.w(TAG, "Bundled version registry missing", it)
            return
        }
        entries = parse(text)
        logger.i(TAG, "Loaded ${entries.size} version entries")
    }

    fun loadUserOverrides(dir: File) {
        val text = IsolatedPaths.readTextOrNull(File(dir, USER_FILE)) ?: return
        val overrides = parse(text).associateBy { it.version }
        if (overrides.isEmpty()) return
        entries = entries.filterNot { overrides.containsKey(it.version) } + overrides.values
        entries = entries.sortedByDescending { it.parsed?.rank ?: -1 }
        logger.i(TAG, "Merged ${overrides.size} user version overrides")
    }

    fun infoFor(versionName: String?): VersionInfo {
        if (versionName.isNullOrBlank()) return VersionInfo("", null, CompatStatus.UNKNOWN)
        entries.firstOrNull { it.version == versionName }?.let { return it }

        val parsed = MinecraftVersion.parse(versionName)
            ?: return VersionInfo(versionName, null, CompatStatus.UNKNOWN, "Unparseable version string")

        val known = entries.mapNotNull { info -> info.parsed?.let { info to it } }
        val maxKnown = known.maxByOrNull { it.second.rank }
        return if (maxKnown != null && parsed.rank > maxKnown.second.rank) {
            VersionInfo(
                versionName, parsed, CompatStatus.EXPERIMENTAL,
                "Newer than the registry (latest validated: ${maxKnown.second}). " +
                    "Overlay features are version-independent and should work."
            )
        } else {
            VersionInfo(versionName, parsed, CompatStatus.UNKNOWN, "Not in the registry. Overlay features should work.")
        }
    }

    private fun parse(text: String): List<VersionInfo> = runCatching {
        json.decodeFromString(VersionListDto.serializer(), text).versions
            .filter { it.version.isNotBlank() }
            .map { VersionInfo(it.version, MinecraftVersion.parse(it.version), CompatStatus.byId(it.status), it.note) }
            .sortedByDescending { it.parsed?.rank ?: -1 }
    }.getOrElse {
        logger.e(TAG, "Failed to parse version registry", it)
        emptyList()
    }

    companion object {
        const val TAG = "VersionCompat"
        const val BUNDLED_PATH = "compat/versions.json"
        const val USER_FILE = "user_versions.json"
    }
}

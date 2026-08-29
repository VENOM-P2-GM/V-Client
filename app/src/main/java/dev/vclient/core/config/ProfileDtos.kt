package dev.vclient.core.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * On-disk profile format. `formatVersion` allows future migrations; unknown
 * fields are ignored on load so profiles survive client upgrades.
 */
@Serializable
data class ProfileData(
    val formatVersion: Int = FORMAT_VERSION,
    val name: String,
    val createdAtMs: Long = 0L,
    val modifiedAtMs: Long = 0L,
    /** Minecraft version this profile was last used with (informational). */
    val gameVersion: String = "",
    val modules: List<ModuleStateDto> = emptyList(),
    val hud: List<HudLayoutDto> = emptyList(),
    val waypoints: List<WaypointDto> = emptyList(),
) {
    companion object { const val FORMAT_VERSION = 1 }
}

@Serializable
data class ModuleStateDto(
    val id: String,
    val enabled: Boolean = false,
    /** settingId -> persisted value (see dev.vclient.core.settings.encodeJson). */
    val settings: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class HudLayoutDto(
    val elementId: String,
    val anchor: String = "top_left",
    val offsetX: Float = 12f,
    val offsetY: Float = 12f,
    val scale: Float = 1f,
)

@Serializable
data class WaypointDto(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val dimension: String = "OVERWORLD",
    val colorArgb: Int = 0xFF8B5CF6.toInt(),
    val visible: Boolean = true,
)

@Serializable
data class ProfileMeta(
    val name: String,
    val modifiedAtMs: Long = 0L,
    val gameVersion: String = "",
)

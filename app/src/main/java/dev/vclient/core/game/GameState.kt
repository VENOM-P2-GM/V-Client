package dev.vclient.core.game

import kotlinx.serialization.Serializable

/** Minecraft world-space vector. */
@Serializable
data class Vec3(val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0) {
    fun distanceTo(other: Vec3): Double {
        val dx = x - other.x; val dy = y - other.y; val dz = z - other.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }
    fun horizontalDistanceTo(other: Vec3): Double {
        val dx = x - other.x; val dz = z - other.z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }
}

@Serializable
enum class GameDimension(val display: String) {
    OVERWORLD("Overworld"),
    NETHER("Nether"),
    THE_END("The End");

    companion object {
        fun byId(id: String?): GameDimension = entries.firstOrNull { it.name == id } ?: OVERWORLD
    }
}

/** Display-friendly item slot (string ids keep the model version-agnostic). */
@Serializable
data class ItemSlot(
    val index: Int = 0,
    val name: String = "",
    val count: Int = 1,
    val durability: Int = 0,
    val maxDurability: Int = 0,
    val colorArgb: Int = 0xFF9CA3AF.toInt(),
) {
    val hasDurability: Boolean get() = maxDurability > 0 && durability > 0
}

@Serializable
data class EffectEntry(
    val name: String,
    val amplifier: Int = 0,
    val remainingTicks: Int = 0,
    val colorArgb: Int = 0xFF22D3EE.toInt(),
)

/**
 * Version-agnostic snapshot of the player's game state. Produced by a
 * [GameDataSource]; consumed by modules and the HUD renderer.
 */
data class GameState(
    val position: Vec3? = null,
    val yawDeg: Float? = null,
    val pitchDeg: Float? = null,
    val dimension: GameDimension = GameDimension.OVERWORLD,
    val effects: List<EffectEntry> = emptyList(),
    val armor: List<ItemSlot> = emptyList(),
    val inventory: List<ItemSlot> = emptyList(),
    val source: String = "none",
    val timestampMs: Long = 0L,
) {
    val hasPosition: Boolean get() = position != null

    companion object {
        val EMPTY = GameState(source = "none")
    }
}

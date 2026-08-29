package dev.vclient.core.game

import dev.vclient.core.AppSettings
import kotlin.math.cos
import kotlin.math.sin

enum class DataSourceKind(val id: String, val display: String, val detail: String) {
    MANUAL("manual", "Manual", "You type your coordinates once; HUDs like Waypoints use them for distances."),
    DEMO("demo", "Demo", "Simulated player data — great for previewing and editing the HUD."),
    BRIDGE("bridge", "V Bridge", "Live in-game data through the native bridge transport (future feature).");

    companion object {
        fun byId(id: String?): DataSourceKind = entries.firstOrNull { it.id == id } ?: MANUAL
    }
}

/**
 * Produces [GameState] snapshots for HUD modules. Adding a new source (e.g. a
 * future native bridge or a Shizuku transport) is the single integration point
 * for live game data — modules and the renderer never change.
 */
interface GameDataSource {
    val kind: DataSourceKind
    val available: Boolean get() = true
    fun poll(nowMs: Long): GameState
}

/** User-entered static position. Always available. */
class ManualDataSource(private val settings: AppSettings) : GameDataSource {
    override val kind = DataSourceKind.MANUAL

    override fun poll(nowMs: Long): GameState = GameState(
        position = Vec3(settings.manualX.toDouble(), settings.manualY.toDouble(), settings.manualZ.toDouble()),
        yawDeg = settings.manualYaw,
        pitchDeg = 0f,
        dimension = GameDimension.byId(settings.manualDimension),
        source = kind.id,
        timestampMs = nowMs,
    )
}

/**
 * Simulated player walking a circle: position, heading, potion effects,
 * armor and inventory. Used by demo mode and the HUD editor preview.
 */
class DemoDataSource : GameDataSource {
    override val kind = DataSourceKind.DEMO

    override fun poll(nowMs: Long): GameState {
        val t = nowMs / 1000.0
        val angle = t * 0.35
        val cx = 128.0; val cz = -256.0; val r = 40.0
        val x = cx + r * cos(angle)
        val z = cz + r * sin(angle)
        // Walking the circle counterclockwise: heading follows the tangent.
        val yaw = Math.toDegrees(angle).toFloat()

        val effects = listOf(
            EffectEntry("Speed", 1, remainingTicks = (7200 - (t * 20).toInt() % 7200), colorArgb = 0xFF34D399.toInt()),
            EffectEntry("Night Vision", 0, remainingTicks = (1800 - (t * 20).toInt() % 1800), colorArgb = 0xFF8B5CF6.toInt()),
            EffectEntry("Regeneration", 2, remainingTicks = (600 - (t * 20).toInt() % 600), colorArgb = 0xFFF87171.toInt()),
        )
        val armor = listOf(
            ItemSlot(0, "Diamond Helmet", 1, 1561, 1561, 0xFF22D3EE.toInt()),
            ItemSlot(1, "Diamond Chestplate", 1, 2197, 1805, 0xFF22D3EE.toInt()),
            ItemSlot(2, "Diamond Leggings", 1, 2031, 2031, 0xFF22D3EE.toInt()),
            ItemSlot(3, "Diamond Boots", 1, 1729, 1391, 0xFF22D3EE.toInt()),
        )
        val inventory = listOf(
            ItemSlot(0, "Diamond Sword", 1, 0, 0, 0xFF22D3EE.toInt()),
            ItemSlot(1, "Ender Pearl", 12, 0, 0, 0xFF8B5CF6.toInt()),
            ItemSlot(2, "Steak", 34, 0, 0, 0xFFB45309.toInt()),
            ItemSlot(3, "Golden Apple", 6, 0, 0, 0xFFFBBF24.toInt()),
            ItemSlot(4, "Building Blocks", 432, 0, 0, 0xFF9CA3AF.toInt()),
            ItemSlot(5, "Water Bucket", 1, 0, 0, 0xFF3B82F6.toInt()),
        )
        return GameState(
            position = Vec3(x, 64.0, z),
            yawDeg = yaw,
            pitchDeg = (sin(t * 0.8) * 12).toFloat(),
            dimension = GameDimension.OVERWORLD,
            effects = effects,
            armor = armor,
            inventory = inventory,
            source = kind.id,
            timestampMs = nowMs,
        )
    }
}

/**
 * Placeholder for the native bridge transport. The native runtime ships the
 * bridge ABI (see cpp/vclient/Bridge.hpp) but no transport is compiled into
 * this build, so the source reports itself unavailable. Enabling it later
 * means implementing a transport and flipping [available] — every module and
 * the renderer already consume GameState, so nothing else changes.
 */
class BridgeDataSource : GameDataSource {
    override val kind = DataSourceKind.BRIDGE
    override val available: Boolean get() = false

    override fun poll(nowMs: Long): GameState =
        GameState(source = kind.id, timestampMs = nowMs)
}

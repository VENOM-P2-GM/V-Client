package dev.vclient.modules

import android.graphics.Canvas
import android.graphics.RectF
import android.util.SizeF
import dev.vclient.core.game.ItemSlot
import dev.vclient.core.hud.HudElement
import dev.vclient.core.hud.HudFrame
import dev.vclient.core.hud.HudRender
import dev.vclient.core.module.HudModule
import dev.vclient.core.settings.BoolSetting
import dev.vclient.core.settings.IntSetting

/** Inventory HUD — compact configurable grid of inventory tiles. */
class InventoryHudModule : HudModule(ID, NAME, "Shows your hotbar/inventory items as a compact grid.") {

    private val columns by settingOf(IntSetting("columns", "Columns", 6, 3, 9, 1, ""))
    private val maxRows by settingOf(IntSetting("max_rows", "Max rows", 1, 1, 3, 1, ""))
    private val tileSize by settingOf(IntSetting("tile_size", "Tile size", 30, 18, 48, 1, "dp"))
    private val showCounts by settingOf(BoolSetting("show_counts", "Show counts", true))
    private val showEmpty by settingOf(BoolSetting("show_empty", "Empty slots", true, "Dimmed placeholders."))

    override val hud: HudElement = Element()

    private inner class Element : HudElement(ID) {

        override fun measure(frame: HudFrame): SizeF {
            val d = frame.density
            val tile = tileSize * d
            val gap = 2f * d
            val pad = 5f * d
            val rows = rows(frame).chunked(columns).size
            return SizeF(
                pad * 2 + columns * tile + (columns - 1) * gap,
                pad * 2 + rows * tile + (rows - 1) * gap,
            )
        }

        override fun draw(canvas: Canvas, frame: HudFrame, rect: RectF) {
            drawPanel(canvas, frame, rect)
            val d = frame.density
            val tile = tileSize * d
            val gap = 2f * d
            val pad = 5f * d
            val countSize = tile * 0.32f
            val rows = rows(frame).chunked(columns)
            for ((rowIndex, row) in rows.withIndex()) {
                for ((colIndex, item) in row.withIndex()) {
                    val left = rect.left + pad + colIndex * (tile + gap)
                    val top = rect.top + pad + rowIndex * (tile + gap)
                    val tileRect = RectF(left, top, left + tile, top + tile)
                    if (item.name.isEmpty()) {
                        if (showEmpty) HudRender.drawPanel(canvas, tileRect, 0x14FFFFFF, 3f * d)
                        continue
                    }
                    HudRender.drawPanel(canvas, tileRect, HudRender.withAlpha(item.colorArgb, 56), 3f * d)
                    HudRender.drawPanel(canvas, tileRect, item.colorArgb, 3f * d, borderColor = HudRender.withAlpha(item.colorArgb, 200), borderWidthPx = d)
                    // First letter of the item as a lightweight "icon"
                    val letter = item.name.take(1)
                    val baseline = top + tile / 2f + countSize * 0.35f
                    HudRender.drawText(canvas, letter, left + tile / 2f - HudRender.textWidth(letter, countSize) / 2f, baseline, countSize * 1.6f, HudRender.withAlpha(item.colorArgb, 230), textShadowEnabled(), bold = true)
                    if (showCounts && item.count > 1) {
                        val count = "${item.count}"
                        HudRender.drawText(canvas, count, left + tile - HudRender.textWidth(count, countSize) - 1.5f * d, top + tile - 1.5f * d, countSize, 0xFFFFFFFF.toInt(), shadow = true, bold = true)
                    }
                }
            }
        }

        private fun rows(frame: HudFrame): List<ItemSlot> {
            val capacity = columns * maxRows
            val items = frame.state.inventory.take(capacity)
            if (!showEmpty) return items
            val empties = (items.size until capacity).map { ItemSlot(it, "") }
            return items + empties
        }
    }

    companion object {
        const val ID = "inventory_hud"
        const val NAME = "Inventory HUD"
    }
}

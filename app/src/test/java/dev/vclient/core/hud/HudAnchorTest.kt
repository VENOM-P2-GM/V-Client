package dev.vclient.core.hud

import org.junit.Assert.assertEquals
import org.junit.Test
class HudAnchorTest {

    private fun resolve(anchor: HudAnchor, cw: Float, ch: Float, w: Float, h: Float): Pair<Float, Float> {
        val p: PointF = anchor.resolve(cw, ch, w, h)
        return p.x to p.y
    }

    @Test
    fun `corners and centers resolve correctly`() {
        assertEquals(0f to 0f, resolve(HudAnchor.TOP_LEFT, 100f, 200f, 20f, 40f))
        assertEquals(40f to 0f, resolve(HudAnchor.TOP_CENTER, 100f, 200f, 20f, 40f))
        assertEquals(80f to 0f, resolve(HudAnchor.TOP_RIGHT, 100f, 200f, 20f, 40f))
        assertEquals(0f to 80f, resolve(HudAnchor.CENTER_LEFT, 100f, 200f, 20f, 40f))
        assertEquals(40f to 80f, resolve(HudAnchor.CENTER, 100f, 200f, 20f, 40f))
        assertEquals(80f to 80f, resolve(HudAnchor.CENTER_RIGHT, 100f, 200f, 20f, 40f))
        assertEquals(0f to 160f, resolve(HudAnchor.BOTTOM_LEFT, 100f, 200f, 20f, 40f))
        assertEquals(40f to 160f, resolve(HudAnchor.BOTTOM_CENTER, 100f, 200f, 20f, 40f))
        assertEquals(80f to 160f, resolve(HudAnchor.BOTTOM_RIGHT, 100f, 200f, 20f, 40f))
    }

    @Test
    fun `byId falls back to top-left`() {
        assertEquals(HudAnchor.TOP_LEFT, HudAnchor.byId("nonsense"))
        assertEquals(HudAnchor.CENTER, HudAnchor.byId("center"))
    }
}

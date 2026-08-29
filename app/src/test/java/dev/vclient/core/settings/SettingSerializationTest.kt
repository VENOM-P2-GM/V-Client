package dev.vclient.core.settings

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingSerializationTest {

    @Test
    fun `bool setting round trip`() {
        val s = BoolSetting("b", "B", false)
        assertFalse(s.decodeJson(JsonPrimitive(true)) == false && s.value != true)
        assertTrue(s.decodeJson(JsonPrimitive(true)))
        assertTrue(s.value)
        assertEquals(JsonPrimitive(true), s.encodeJson())
    }

    @Test
    fun `int setting clamps to range`() {
        val s = IntSetting("i", "I", 5, 0, 10)
        assertTrue(s.decodeJson(JsonPrimitive(999)))
        assertEquals(10, s.value)
        assertTrue(s.decodeJson(JsonPrimitive(-3)))
        assertEquals(0, s.value)
    }

    @Test
    fun `float setting round trip`() {
        val s = FloatSetting("f", "F", 1.0f, 0f, 2f)
        s.decodeJson(JsonPrimitive(1.5f))
        assertEquals(1.5f, s.value)
    }

    @Test
    fun `color setting round trip through hex`() {
        val s = ColorSetting("c", "C", 0xFF8B5CF6.toInt())
        assertTrue(s.decodeJson(JsonPrimitive("#FF22D3EE")))
        assertEquals(0xFF22D3EE.toInt(), s.value)
        assertEquals("#FF22D3EE", (s.encodeJson() as JsonPrimitive).content)
    }

    @Test
    fun `invalid color is rejected`() {
        val s = ColorSetting("c", "C", 0xFFFFFFFF.toInt())
        assertFalse(s.decodeJson(JsonPrimitive("not-a-color")))
        assertEquals(0xFFFFFFFF.toInt(), s.value)
    }

    @Test
    fun `mode setting only accepts known options`() {
        val s = ModeSetting("m", "M", listOf("A", "B", "C"), "A")
        assertTrue(s.decodeJson(JsonPrimitive("B")))
        assertEquals("B", s.value)
        assertFalse(s.decodeJson(JsonPrimitive("Z")))
        assertEquals("B", s.value)
    }

    @Test
    fun `container rejects duplicate ids`() {
        val container = SettingContainer()
        container.add(BoolSetting("x", "X", true))
        var second = false
        try {
            container.add(BoolSetting("x", "X2", true))
        } catch (expected: IllegalStateException) {
            second = true
        }
        assertTrue(second)
        assertEquals(1, container.all.size)
    }
}

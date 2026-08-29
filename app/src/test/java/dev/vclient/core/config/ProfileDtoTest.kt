package dev.vclient.core.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDtoTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `profile json round trip preserves everything`() {
        val profile = ProfileData(
            name = "PvP",
            createdAtMs = 100,
            modifiedAtMs = 200,
            gameVersion = "1.21.100",
            modules = listOf(
                ModuleStateDto("fps_counter", enabled = true, settings = mapOf("text_size" to JsonPrimitive(15)))
            ),
            hud = listOf(HudLayoutDto("coordinates", "top_right", 8f, 64f, 1.2f)),
            waypoints = listOf(WaypointDto("abc", "Base", 10.0, 64.0, -20.0, "OVERWORLD", 0xFF22D3EE.toInt())),
        )
        val text = json.encodeToString(ProfileData.serializer(), profile)
        val back = json.decodeFromString(ProfileData.serializer(), text)
        assertEquals(profile, back)
        assertEquals("top_right", back.hud.first().anchor)
        assertEquals(1.2f, back.hud.first().scale)
        assertEquals("Base", back.waypoints.first().name)
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val text = """
            { "name": "Future", "formatVersion": 1, "brandNewField": 42,
              "modules": [ { "id": "x", "enabled": true, "settings": {}, "futureSetting": 1 } ] }
        """.trimIndent()
        val back = json.decodeFromString(ProfileData.serializer(), text)
        assertEquals("Future", back.name)
        assertTrue(back.modules.first().enabled)
    }

    @Test
    fun `setting values are plain json primitives`() {
        val settings = mapOf(
            "background" to JsonPrimitive(true),
            "text_size" to JsonPrimitive(13),
            "opacity" to JsonPrimitive(0.8f),
            "color" to JsonPrimitive("#FF8B5CF6"),
        )
        val encoded = json.encodeToString(
            ModuleStateDto.serializer(),
            ModuleStateDto("m", enabled = true, settings = settings),
        )
        val obj = Json.parseToJsonElement(encoded).jsonObject
        assertTrue(obj.containsKey("settings"))
        assertEquals("\"#FF8B5CF6\"", obj["settings"]!!.jsonObject["color"].toString())
    }
}

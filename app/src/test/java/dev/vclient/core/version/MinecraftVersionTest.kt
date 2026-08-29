package dev.vclient.core.version

import dev.vclient.core.logging.VLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class MinecraftVersionTest {

    private fun newRegistry(): VersionRegistry {
        val tempDir = createTempDirectory("vclient-test").toFile()
        return VersionRegistry(VLogger(tempDir))
    }

    @Test
    fun `parses simple versions`() {
        val v = MinecraftVersion.parse("1.21.100")
        assertEquals(1, v?.major)
        assertEquals(21, v?.minor)
        assertEquals(100, v?.patch)
    }

    @Test
    fun `missing patch segment defaults to zero`() {
        assertEquals(0, MinecraftVersion.parse("1.21")?.patch)
    }

    @Test
    fun `build suffixes are ignored`() {
        assertEquals(100, MinecraftVersion.parse("1.21.100.06")?.patch)
    }

    @Test
    fun `garbage returns null`() {
        assertNull(MinecraftVersion.parse("beta"))
        assertNull(MinecraftVersion.parse(""))
        assertNull(MinecraftVersion.parse(null))
    }

    @Test
    fun `segment ordering beats string ordering`() {
        val a = MinecraftVersion.parse("1.21.9")!!
        val b = MinecraftVersion.parse("1.21.100")!!
        val c = MinecraftVersion.parse("1.20.51")!!
        assertTrue(b > a) // 100 > 9 numerically (but "100" < "9" as text)
        assertTrue(a > c) // 1.21 > 1.20
    }

    @Test
    fun `empty registry resolves unknown versions gracefully`() {
        val info = newRegistry().infoFor("1.21.50")
        assertEquals(CompatStatus.UNKNOWN, info.status)
    }

    @Test
    fun `user overrides merge and newer-than-registry is experimental`() {
        val registry = newRegistry()
        val dir = createTempDirectory("vclient-compat").toFile()
        File(dir, "user_versions.json").writeText(
            """
            { "versions": [
              { "version": "1.21.100", "status": "supported" },
              { "version": "1.22.0", "status": "experimental" }
            ] }
            """.trimIndent()
        )
        registry.loadUserOverrides(dir)

        assertEquals(CompatStatus.SUPPORTED, registry.infoFor("1.21.100").status)
        assertEquals(CompatStatus.EXPERIMENTAL, registry.infoFor("1.22.0").status)
        // Newer than every registry entry -> experimental by default.
        assertEquals(CompatStatus.EXPERIMENTAL, registry.infoFor("9.9.9").status)
        // Unparseable junk -> unknown.
        assertEquals(CompatStatus.UNKNOWN, registry.infoFor("weird").status)
    }
}

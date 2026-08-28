package com.venom.vclient.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VenomColors = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFE9D5FF),
    secondary = Color(0xFFA3E635),
    onSecondary = Color(0xFF1A2E05),
    background = Color(0xFF0A0A14),
    onBackground = Color(0xFFE4E4E7),
    surface = Color(0xFF12121F),
    onSurface = Color(0xFFE4E4E7),
    surfaceVariant = Color(0xFF1B1B31),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0x40FFFFFF),
    error = Color(0xFFF87171)
)

private val ToxicColors = darkColorScheme(
    primary = Color(0xFFA3E635),
    onPrimary = Color(0xFF1A2E05),
    primaryContainer = Color(0xFF365314),
    onPrimaryContainer = Color(0xFFD9F99D),
    secondary = Color(0xFF8B5CF6),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFF0A0A14),
    onBackground = Color(0xFFE4E4E7),
    surface = Color(0xFF12121F),
    onSurface = Color(0xFFE4E4E7),
    surfaceVariant = Color(0xFF1B1B31),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0x40FFFFFF),
    error = Color(0xFFF87171)
)

@Composable
fun VClientTheme(venom: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (venom) VenomColors else ToxicColors,
        content = content
    )
}

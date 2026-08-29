package dev.vclient.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Single design system shared by the launcher and the in-game V Menu so the
 * product feels like one app. Dark-first (gaming context).
 */
private val VDarkScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3B1D80),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF06222A),
    secondaryContainer = Color(0xFF0E4C5C),
    onSecondaryContainer = Color(0xFFC7F2FF),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE6E9F2),
    surface = Color(0xFF111726),
    onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF1B2334),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF2A3245),
    error = Color(0xFFF87171),
    onError = Color(0xFF2B0606),
)

@Composable
fun VClientTheme(content: @Composable () -> Unit) {
    // V Client is dark-only by design; ignore the system setting deliberately.
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = VDarkScheme,
        typography = Typography(),
        content = content,
    )
}

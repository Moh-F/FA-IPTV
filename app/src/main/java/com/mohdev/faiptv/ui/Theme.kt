package com.mohdev.faiptv.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mohdev.faiptv.core.Prefs

/** Brand colours follow the selected palette (calm navy + teal by default). */
object Brand {
    val Cyan: Color @Composable get() = MaterialTheme.colorScheme.primary
    val Violet: Color @Composable get() = MaterialTheme.colorScheme.secondary
    val Pink: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val Navy: Color @Composable get() = MaterialTheme.colorScheme.background
    val Navy2: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val Amber = Color(0xFFF59E0B)
    val Live = Color(0xFFEF4444)
    val gradient: Brush @Composable get() = Brush.linearGradient(listOf(Cyan, Violet))
}

data class Palette(val id: String, val dark: ColorScheme, val light: ColorScheme)

private fun dark(
    bg: Long, surface: Long, container: Long, high: Long, outline: Long, on: Long, onVar: Long,
    primary: Long, onPrimary: Long, secondary: Long, tertiary: Long,
) = darkColorScheme(
    primary = Color(primary), onPrimary = Color(onPrimary),
    primaryContainer = Color(primary).copy(alpha = 0.22f), onPrimaryContainer = Color(on),
    secondary = Color(secondary), onSecondary = Color(onPrimary), tertiary = Color(tertiary),
    background = Color(bg), onBackground = Color(on),
    surface = Color(surface), onSurface = Color(on),
    surfaceVariant = Color(high), onSurfaceVariant = Color(onVar),
    surfaceContainerLowest = Color(bg), surfaceContainerLow = Color(surface),
    surfaceContainer = Color(container), surfaceContainerHigh = Color(high), surfaceContainerHighest = Color(high),
    outline = Color(outline), outlineVariant = Color(outline),
    error = Color(0xFFF87171),
)

private fun light(
    bg: Long, surface: Long, container: Long, high: Long, outline: Long, on: Long, onVar: Long,
    primary: Long, secondary: Long, tertiary: Long,
) = lightColorScheme(
    primary = Color(primary), onPrimary = Color.White,
    primaryContainer = Color(primary).copy(alpha = 0.14f), onPrimaryContainer = Color(on),
    secondary = Color(secondary), onSecondary = Color.White, tertiary = Color(tertiary),
    background = Color(bg), onBackground = Color(on),
    surface = Color(surface), onSurface = Color(on),
    surfaceVariant = Color(high), onSurfaceVariant = Color(onVar),
    surfaceContainerLowest = Color(surface), surfaceContainerLow = Color(surface),
    surfaceContainer = Color(container), surfaceContainerHigh = Color(high), surfaceContainerHighest = Color(high),
    outline = Color(outline), outlineVariant = Color(outline),
    error = Color(0xFFB91C1C),
)

val palettes = listOf(
    // Calm navy + teal (default): soft contrast, readable from the sofa, light mode is not glaring
    Palette(
        "navy",
        dark(0xFF080D18, 0xFF0F1727, 0xFF152036, 0xFF1E2C48, 0xFF3B4F73, 0xFFF8FAFC, 0xFFCBD5E1, 0xFF2EE6CF, 0xFF00211C, 0xFF60C8FF, 0xFFFF7AB8),
        light(0xFFEEF2F6, 0xFFFFFFFF, 0xFFE2E8F0, 0xFFD3DCE7, 0xFF94A3B8, 0xFF0B1220, 0xFF334155, 0xFF0B7A70, 0xFF0369A1, 0xFFBE185D),
    ),
    Palette(
        "gold",
        dark(0xFF0A0A0C, 0xFF141417, 0xFF1C1C21, 0xFF28282E, 0xFF4A4A52, 0xFFFAFAFA, 0xFFD0D0D6, 0xFFD4AF37, 0xFF241B00, 0xFFE9C46A, 0xFFE76F51),
        light(0xFFF1EEE7, 0xFFFAF8F3, 0xFFE9E4D9, 0xFFDFD8CA, 0xFFC2B8A3, 0xFF2A2620, 0xFF5E574A, 0xFF8A6D1D, 0xFF9C6644, 0xFFA4161A),
    ),
    Palette(
        "red",
        dark(0xFF0F0F0F, 0xFF181818, 0xFF212121, 0xFF2D2D2D, 0xFF4D4D4D, 0xFFFAFAFA, 0xFFD0D0D0, 0xFFE50914, 0xFFFFFFFF, 0xFFFF6B6B, 0xFFFFB703),
        light(0xFFEFEFEF, 0xFFF9F9F9, 0xFFE6E6E6, 0xFFDBDBDB, 0xFFBDBDBD, 0xFF1F1F1F, 0xFF555555, 0xFFB20710, 0xFFC1121F, 0xFFB45309),
    ),
)

@Composable
fun isDark(): Boolean = when (Prefs.s.theme) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme() || UiEnv.isTv
}

@Composable
fun FaTheme(content: @Composable () -> Unit) {
    val p = palettes.firstOrNull { it.id == Prefs.s.palette } ?: palettes[0]
    // Text size (TV sofa distance): scales all sp sizes of the app
    val d = androidx.compose.ui.platform.LocalDensity.current
    val factor = when (Prefs.s.textSize) { "l" -> 1.15f; "xl" -> 1.3f; else -> 1f }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * factor),
    ) {
        MaterialTheme(colorScheme = if (isDark()) p.dark else p.light, content = content)
    }
}

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
        dark(0xFF0B1220, 0xFF111A2B, 0xFF162133, 0xFF1F2C42, 0xFF33445F, 0xFFE8EDF5, 0xFFA3B3C9, 0xFF2DD4BF, 0xFF042F2A, 0xFF38BDF8, 0xFFF472B6),
        light(0xFFECEFF3, 0xFFF7F9FB, 0xFFE3E8EE, 0xFFD8DFE8, 0xFFB4C0CF, 0xFF1E293B, 0xFF4B5A6E, 0xFF0F766E, 0xFF0369A1, 0xFFBE185D),
    ),
    Palette(
        "gold",
        dark(0xFF0D0D0F, 0xFF151518, 0xFF1C1C20, 0xFF26262B, 0xFF3E3E45, 0xFFEDEDED, 0xFFABABB3, 0xFFD4AF37, 0xFF241B00, 0xFFE9C46A, 0xFFE76F51),
        light(0xFFF1EEE7, 0xFFFAF8F3, 0xFFE9E4D9, 0xFFDFD8CA, 0xFFC2B8A3, 0xFF2A2620, 0xFF5E574A, 0xFF8A6D1D, 0xFF9C6644, 0xFFA4161A),
    ),
    Palette(
        "red",
        dark(0xFF121212, 0xFF1A1A1A, 0xFF212121, 0xFF2B2B2B, 0xFF424242, 0xFFF0F0F0, 0xFFAAAAAA, 0xFFE50914, 0xFFFFFFFF, 0xFFFF6B6B, 0xFFFFB703),
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
    MaterialTheme(colorScheme = if (isDark()) p.dark else p.light, content = content)
}

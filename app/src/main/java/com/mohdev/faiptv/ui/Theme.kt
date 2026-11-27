package com.mohdev.faiptv.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mohdev.faiptv.core.Prefs

object Brand {
    val Navy = Color(0xFF070B1F)
    val Navy2 = Color(0xFF1A1450)
    val Cyan = Color(0xFF22D3EE)
    val Violet = Color(0xFF8B5CF6)
    val Pink = Color(0xFFEC4899)
    val Amber = Color(0xFFF59E0B)
    val Live = Color(0xFFEF4444)
    val gradient = Brush.linearGradient(listOf(Cyan, Violet, Pink))
}

private val Dark = darkColorScheme(
    primary = Brand.Cyan,
    onPrimary = Color(0xFF00222B),
    secondary = Brand.Violet,
    onSecondary = Color.White,
    tertiary = Brand.Pink,
    background = Brand.Navy,
    onBackground = Color(0xFFE6E9F5),
    surface = Color(0xFF0E1430),
    onSurface = Color(0xFFE6E9F5),
    surfaceVariant = Color(0xFF1A2147),
    onSurfaceVariant = Color(0xFFA7AFCF),
    surfaceContainer = Color(0xFF121937),
    surfaceContainerHigh = Color(0xFF1A2147),
    outline = Color(0xFF39416B),
    error = Color(0xFFF87171),
)

private val Light = lightColorScheme(
    primary = Color(0xFF0E7490),
    onPrimary = Color.White,
    secondary = Color(0xFF6D28D9),
    onSecondary = Color.White,
    tertiary = Color(0xFFBE185D),
    background = Color(0xFFF4F6FD),
    onBackground = Color(0xFF0B1024),
    surface = Color.White,
    onSurface = Color(0xFF0B1024),
    surfaceVariant = Color(0xFFE6E9F7),
    onSurfaceVariant = Color(0xFF4A5275),
    surfaceContainer = Color(0xFFEDEFFA),
    surfaceContainerHigh = Color(0xFFE3E7F7),
    outline = Color(0xFFB7BEDB),
)

@Composable
fun isDark(): Boolean = when (Prefs.s.theme) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme() || UiEnv.isTv
}

@Composable
fun FaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark()) Dark else Light, content = content)
}

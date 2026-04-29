package com.mobileclaw.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class ClawColors(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val cardAlt: Color,
    val border: Color,
    val borderActive: Color,
    val text: Color,
    val subtext: Color,
    val accent: Color,
    val green: Color,
    val red: Color,
    val blue: Color,
    val purple: Color,
    val isDark: Boolean,
)

val DefaultAccent = Color(0xFFFF6B35)

fun darkClawColors(accent: Color = DefaultAccent) = ClawColors(
    bg = Color(0xFF080810),
    surface = Color(0xFF0F0F1A),
    card = Color(0xFF161624),
    cardAlt = Color(0xFF1C1C2E),
    border = Color(0xFF252538),
    borderActive = Color(0xFF3A3A5C),
    text = Color(0xFFEEEEFF),
    subtext = Color(0xFF7070A0),
    accent = accent,
    green = Color(0xFF56CF86),
    red = Color(0xFFFF5577),
    blue = Color(0xFF64B5F6),
    purple = Color(0xFFCE93D8),
    isDark = true,
)

fun lightClawColors(accent: Color = DefaultAccent) = ClawColors(
    bg = Color(0xFFF2F2F8),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFF0F0F8),
    border = Color(0xFFDDDDF0),
    borderActive = Color(0xFFBBBBDD),
    text = Color(0xFF1A1A2E),
    subtext = Color(0xFF6060A0),
    accent = accent,
    green = Color(0xFF2E9E5E),
    red = Color(0xFFD32F2F),
    blue = Color(0xFF1976D2),
    purple = Color(0xFF7B1FA2),
    isDark = false,
)

val LocalClawColors = staticCompositionLocalOf { darkClawColors() }

/** Combined dark/light + accent preset — shown as a single selectable card in settings. */
data class ThemePreset(
    val name: String,
    val darkTheme: Boolean,
    val accentColor: Long,
    val previewBg: Long,     // small swatch background
    val previewAccent: Long, // small swatch accent dot
)

val ThemePresets = listOf(
    ThemePreset("Dark · Orange", true,  0xFFFF6B35L, 0xFF080810L, 0xFFFF6B35L),
    ThemePreset("Dark · Blue",   true,  0xFF64B5F6L, 0xFF080810L, 0xFF64B5F6L),
    ThemePreset("Dark · Teal",   true,  0xFF4DD0E1L, 0xFF080810L, 0xFF4DD0E1L),
    ThemePreset("Light · Orange", false, 0xFFFF6B35L, 0xFFF2F2F8L, 0xFFFF6B35L),
    ThemePreset("Light · Purple", false, 0xFFCE93D8L, 0xFFF2F2F8L, 0xFFCE93D8L),
    ThemePreset("Light · Blue",   false, 0xFF1976D2L, 0xFFF2F2F8L, 0xFF1976D2L),
)

val AccentPresets = listOf(
    0xFFFF6B35L to "Orange",
    0xFF64B5F6L to "Blue",
    0xFF56CF86L to "Green",
    0xFFCE93D8L to "Purple",
    0xFFFF5577L to "Red",
    0xFF4DD0E1L to "Teal",
)

@Composable
fun ClawTheme(
    darkTheme: Boolean = true,
    accentColor: Long = 0xFFFF6B35L,
    content: @Composable () -> Unit,
) {
    val accent = Color(accentColor)
    val clawColors = if (darkTheme) darkClawColors(accent) else lightClawColors(accent)

    val materialColors = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = clawColors.bg,
            surface = clawColors.surface,
            onBackground = clawColors.text,
            onSurface = clawColors.text,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = clawColors.bg,
            surface = clawColors.surface,
            onBackground = clawColors.text,
            onSurface = clawColors.text,
        )
    }

    CompositionLocalProvider(LocalClawColors provides clawColors) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}

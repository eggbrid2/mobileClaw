package com.mobileclaw.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

val DefaultAccent = Color(0xFFC7F43A)

fun darkClawColors(accent: Color = DefaultAccent) = ClawColors(
    bg = Color(0xFF050505),
    surface = Color(0xFF0D0D0D),
    card = Color(0xFF151515),
    cardAlt = Color(0xFF202020),
    border = Color(0xFF292929),
    borderActive = Color(0xFF424242),
    text = Color(0xFFF7F7F4),
    subtext = Color(0xFFA0A0A0),
    accent = accent,
    green = Color(0xFF56D6BA),
    red = Color(0xFFFF6B6B),
    blue = Color(0xFFB7B7B7),
    purple = Color(0xFFD7D7D7),
    isDark = true,
)

fun lightClawColors(accent: Color = DefaultAccent) = ClawColors(
    bg = Color(0xFFF6F6F4),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFF0F0EE),
    border = Color(0xFFE5E5E1),
    borderActive = Color(0xFF1A1A1A),
    text = Color(0xFF101010),
    subtext = Color(0xFF858585),
    accent = accent,
    green = Color(0xFF1D9B7F),
    red = Color(0xFFCC3A3A),
    blue = Color(0xFF5F5F5F),
    purple = Color(0xFF6C6C6C),
    isDark = false,
)

val LocalClawColors = staticCompositionLocalOf { darkClawColors() }

private val ClawTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 48.sp, lineHeight = 56.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 36.sp, lineHeight = 44.sp),
    displaySmall  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineLarge  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    titleSmall  = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodyLarge   = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.sp),
    bodyMedium  = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodySmall   = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    labelLarge  = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelSmall  = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.sp),
)

/** Combined dark/light + accent preset — shown as a single selectable card in settings. */
data class ThemePreset(
    val name: String,
    val darkTheme: Boolean,
    val accentColor: Long,
    val previewBg: Long,     // small swatch background
    val previewAccent: Long, // small swatch accent dot
)

val ThemePresets = listOf(
    ThemePreset("AI Night", true, 0xFFC7F43AL, 0xFF050505L, 0xFFC7F43AL),
    ThemePreset("AI Day", false, 0xFFC7F43AL, 0xFFF6F6F4L, 0xFF101010L),
)

val AccentPresets = listOf(
    0xFFC7F43AL to "AI",
    0xFF56D6BAL to "Mint",
)

@Composable
fun ClawTheme(
    darkTheme: Boolean = true,
    accentColor: Long = 0xFFC7F43AL,
    content: @Composable () -> Unit,
) {
    val accent = Color(accentColor)
    val clawColors = if (darkTheme) darkClawColors(accent) else lightClawColors(accent)

    val materialColors = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF050505),
            background = clawColors.bg,
            surface = clawColors.surface,
            onBackground = clawColors.text,
            onSurface = clawColors.text,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color(0xFF101010),
            background = clawColors.bg,
            surface = clawColors.surface,
            onBackground = clawColors.text,
            onSurface = clawColors.text,
        )
    }

    CompositionLocalProvider(LocalClawColors provides clawColors) {
        MaterialTheme(colorScheme = materialColors, typography = ClawTypography, content = content)
    }
}

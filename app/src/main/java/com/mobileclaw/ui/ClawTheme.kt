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
private val TechBlueAccent = Color(0xFF2563EB)

private fun isMinimalAiAccent(accent: Color): Boolean =
    accent == DefaultAccent || accent == Color(0xFF56D6BA)

fun darkClawColors(accent: Color = DefaultAccent) = ClawColors(
    bg = if (isMinimalAiAccent(accent)) Color(0xFF050505) else Color(0xFF070B1A),
    surface = if (isMinimalAiAccent(accent)) Color(0xFF0D0D0D) else Color(0xFF0F172A),
    card = if (isMinimalAiAccent(accent)) Color(0xFF151515) else Color(0xFF111C33),
    cardAlt = if (isMinimalAiAccent(accent)) Color(0xFF202020) else Color(0xFF16233F),
    border = if (isMinimalAiAccent(accent)) Color(0xFF292929) else Color(0xFF263654),
    borderActive = if (isMinimalAiAccent(accent)) Color(0xFF424242) else accent.copy(alpha = 0.62f),
    text = Color(0xFFF7F7F4),
    subtext = if (isMinimalAiAccent(accent)) Color(0xFFA0A0A0) else Color(0xFFA8B3CF),
    accent = accent,
    green = Color(0xFF56D6BA),
    red = Color(0xFFFF6B6B),
    blue = if (isMinimalAiAccent(accent)) Color(0xFFB7B7B7) else Color(0xFF38BDF8),
    purple = if (isMinimalAiAccent(accent)) Color(0xFFD7D7D7) else Color(0xFFA78BFA),
    isDark = true,
)

fun lightClawColors(accent: Color = DefaultAccent) = ClawColors(
    bg = if (isMinimalAiAccent(accent)) Color(0xFFF6F6F4) else Color(0xFFF4F7FF),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    cardAlt = if (isMinimalAiAccent(accent)) Color(0xFFF0F0EE) else Color(0xFFEAF1FF),
    border = if (isMinimalAiAccent(accent)) Color(0xFFE5E5E1) else Color(0xFFD8E3F8),
    borderActive = if (isMinimalAiAccent(accent)) Color(0xFF1A1A1A) else accent.copy(alpha = 0.62f),
    text = Color(0xFF101010),
    subtext = if (isMinimalAiAccent(accent)) Color(0xFF858585) else Color(0xFF64708A),
    accent = accent,
    green = Color(0xFF1D9B7F),
    red = Color(0xFFCC3A3A),
    blue = if (isMinimalAiAccent(accent)) Color(0xFF5F5F5F) else Color(0xFF2563EB),
    purple = if (isMinimalAiAccent(accent)) Color(0xFF6C6C6C) else Color(0xFF7C3AED),
    isDark = false,
)

val LocalClawColors = staticCompositionLocalOf { darkClawColors() }

private val ClawTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 42.sp, lineHeight = 48.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 32.sp, lineHeight = 38.sp),
    displaySmall  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineLarge  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
    headlineSmall  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleLarge  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
    titleSmall  = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    bodyLarge   = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodyMedium  = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
    bodySmall   = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelLarge  = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.sp),
    labelSmall  = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.sp),
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
    ThemePreset("Tech Night", true, 0xFF2563EBL, 0xFF070B1AL, 0xFF38BDF8L),
    ThemePreset("Tech Day", false, 0xFF2563EBL, 0xFFF4F7FFL, 0xFF2563EBL),
)

val AccentPresets = listOf(
    0xFFC7F43AL to "AI",
    0xFF56D6BAL to "Mint",
    0xFF2563EBL to "Tech Blue",
    0xFF7C3AEDL to "Violet",
    0xFF06B6D4L to "Cyan",
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

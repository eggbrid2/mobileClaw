package com.mobileclaw.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale

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

val DefaultAccent = Color(0xFF56D6BA)
private val ChatPageAccent = Color(0xFF56D6BA)

@Suppress("UNUSED_PARAMETER")
private fun chatPageAccent(accent: Color): Color = ChatPageAccent

fun darkClawColors(accent: Color = DefaultAccent): ClawColors {
    val uiAccent = chatPageAccent(accent)
    return ClawColors(
        bg = Color(0xFF050505),
        surface = Color(0xFF0D0D0D),
        card = Color(0xFF151515),
        cardAlt = Color(0xFF202020),
        border = Color(0xFF292929),
        borderActive = Color(0xFF424242),
        text = Color(0xFFF7F7F4),
        subtext = Color(0xFFA0A0A0),
        accent = uiAccent,
        green = uiAccent,
        red = Color(0xFFFF6B6B),
        blue = Color(0xFFB7B7B7),
        purple = Color(0xFFD7D7D7),
        isDark = true,
    )
}

fun lightClawColors(accent: Color = DefaultAccent): ClawColors {
    val uiAccent = chatPageAccent(accent)
    return ClawColors(
        bg = Color(0xFFF7F4EE),
        surface = Color(0xFFFFFEFA),
        card = Color(0xFFFFFFFF),
        cardAlt = Color(0xFFF1EFE8),
        border = Color(0xFFE8E2D7),
        borderActive = Color(0xFF1A1A1A),
        text = Color(0xFF101010),
        subtext = Color(0xFF8C8A84),
        accent = uiAccent,
        green = Color(0xFF1D9B7F),
        red = Color(0xFFCC3A3A),
        blue = Color(0xFF5F5F5F),
        purple = Color(0xFF6C6C6C),
        isDark = false,
    )
}

val LocalClawColors = staticCompositionLocalOf { darkClawColors() }
val LocalAppLanguage = staticCompositionLocalOf { "zh" }

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
    ThemePreset("AI Night", true, 0xFF56D6BAL, 0xFF050505L, 0xFF56D6BAL),
    ThemePreset("AI Day", false, 0xFF56D6BAL, 0xFFF7F4EEL, 0xFF101010L),
)

val AccentPresets = listOf(
    0xFF56D6BAL to "Mint",
)

@Composable
fun ClawTheme(
    darkTheme: Boolean = true,
    accentColor: Long = 0xFF56D6BAL,
    language: String = "auto",
    content: @Composable () -> Unit,
) {
    val accent = Color(accentColor)
    val clawColors = if (darkTheme) darkClawColors(accent) else lightClawColors(accent)
    val normalizedLanguage = language.takeIf { it == "zh" || it == "en" } ?: Locale.getDefault().language
    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, language) {
        baseContext.withAppLocale(language)
    }

    val materialColors = if (darkTheme) {
        darkColorScheme(
            primary = clawColors.text,
            onPrimary = clawColors.bg,
            primaryContainer = clawColors.cardAlt,
            onPrimaryContainer = clawColors.text,
            secondary = clawColors.accent,
            onSecondary = Color(0xFF050505),
            background = clawColors.bg,
            surface = clawColors.surface,
            surfaceVariant = clawColors.cardAlt,
            onBackground = clawColors.text,
            onSurface = clawColors.text,
            onSurfaceVariant = clawColors.subtext,
            outline = clawColors.border,
        )
    } else {
        lightColorScheme(
            primary = clawColors.text,
            onPrimary = clawColors.bg,
            primaryContainer = clawColors.cardAlt,
            onPrimaryContainer = clawColors.text,
            secondary = clawColors.accent,
            onSecondary = Color(0xFF101010),
            background = clawColors.bg,
            surface = clawColors.surface,
            surfaceVariant = clawColors.cardAlt,
            onBackground = clawColors.text,
            onSurface = clawColors.text,
            onSurfaceVariant = clawColors.subtext,
            outline = clawColors.border,
        )
    }

    CompositionLocalProvider(
        LocalClawColors provides clawColors,
        LocalAppLanguage provides normalizedLanguage,
        LocalContext provides localizedContext,
    ) {
        MaterialTheme(colorScheme = materialColors, typography = ClawTypography, content = content)
    }
}

private fun Context.withAppLocale(language: String): Context {
    if (language == "auto" || language.isBlank()) return this
    val locale = Locale.forLanguageTag(language)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}

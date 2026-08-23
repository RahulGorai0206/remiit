package com.rahulgorai.remiit.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Fallback schemes, used only when the user turns dynamic colour off.
 *
 * The app follows the device accent by default, so these are the "no Material
 * You" path rather than the brand. Kept deliberately high-contrast: reminders
 * get read at a glance, often in the dark, and often mid-alarm.
 */
internal val RemiitLightScheme = lightColorScheme(
    primary = Color(0xFF31628D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE5FF),
    onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFF51606F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E4F6),
    onSecondaryContainer = Color(0xFF0D1D2A),
    tertiary = Color(0xFF67587A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDDCFF),
    onTertiaryContainer = Color(0xFF221533),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7F9FF),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4FA),
    surfaceContainer = Color(0xFFEBEEF4),
    surfaceContainerHigh = Color(0xFFE5E8EF),
    surfaceContainerHighest = Color(0xFFDFE3E9),
    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7CF),
    inverseSurface = Color(0xFF2D3135),
    inverseOnSurface = Color(0xFFEEF1F7),
)

internal val RemiitDarkScheme = darkColorScheme(
    primary = Color(0xFF9CCBFB),
    onPrimary = Color(0xFF003354),
    primaryContainer = Color(0xFF124A74),
    onPrimaryContainer = Color(0xFFCFE5FF),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF233240),
    secondaryContainer = Color(0xFF394857),
    onSecondaryContainer = Color(0xFFD4E4F6),
    tertiary = Color(0xFFD2BFE7),
    onTertiary = Color(0xFF382A4A),
    tertiaryContainer = Color(0xFF4F4061),
    onTertiaryContainer = Color(0xFFEDDCFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDFE3E9),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDFE3E9),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF262A2E),
    surfaceContainerHighest = Color(0xFF313539),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
    inverseSurface = Color(0xFFDFE3E9),
    inverseOnSurface = Color(0xFF2D3135),
)

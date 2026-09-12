package com.nexussoft.verbum.designsystem.tokens

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Colour (docs/PRODUCT.md §64). Light-first; the dark scheme exists so the OS
 * setting is honoured, it is not a designed theme (docs/DESIGN_SYSTEM.md).
 *
 * The only brand colour is the bronze accent, identical to the iOS
 * `AccentColor` asset. Everything else is neutral so the reading canvas stays
 * quiet and Material surfaces do the hierarchy work. Dynamic colour is
 * deliberately not used: the accent must be the same on every device.
 */
object Palette {
    val accentLight = Color(0xFF9A6B1F)
    val accentDark = Color(0xFFD8A84A)

    // Reading surface (docs/PRODUCT.md §40 "warm-light"): paper and ink, not #FFFFFF and #000000.
    val paperLight = Color(0xFFFAF7F1)
    val paperDark = Color(0xFF151311)
    val paperElevatedLight = Color(0xFFFFFFFF)
    val paperElevatedDark = Color(0xFF1F1C19)
    val inkLight = Color(0xFF1E1A15)
    val inkDark = Color(0xFFEBE6DE)
    val inkSecondaryLight = Color(0xFF6E655B)
    val inkSecondaryDark = Color(0xFFA39B90)
    val inkTertiaryLight = Color(0xFFA39B90)
    val inkTertiaryDark = Color(0xFF6E665C)
    val ruleLight = Color(0xFFE7E0D5)
    val ruleDark = Color(0xFF2C2823)

    val light: ColorScheme = lightColorScheme(
        primary = accentLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF4E6CC),
        onPrimaryContainer = Color(0xFF3A2A0A),
        secondary = Color(0xFF6B6259),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEDE7E0),
        onSecondaryContainer = Color(0xFF241F1A),
        background = paperLight,
        onBackground = inkLight,
        surface = paperLight,
        onSurface = inkLight,
        surfaceVariant = Color(0xFFF1ECE3),
        onSurfaceVariant = inkSecondaryLight,
        surfaceContainerLowest = paperElevatedLight,
        surfaceContainerLow = Color(0xFFFDFBF7),
        surfaceContainer = paperElevatedLight,
        surfaceContainerHigh = Color(0xFFF1ECE3),
        surfaceContainerHighest = Color(0xFFE9E3D8),
        outline = inkTertiaryLight,
        outlineVariant = ruleLight,
    )

    val dark: ColorScheme = darkColorScheme(
        primary = accentDark,
        onPrimary = Color(0xFF3A2A0A),
        primaryContainer = Color(0xFF5A4315),
        onPrimaryContainer = Color(0xFFF4E6CC),
        secondary = Color(0xFFD5CCC2),
        onSecondary = Color(0xFF3A342E),
        secondaryContainer = Color(0xFF514A43),
        onSecondaryContainer = Color(0xFFEDE7E0),
        background = paperDark,
        onBackground = inkDark,
        surface = paperDark,
        onSurface = inkDark,
        surfaceVariant = Color(0xFF241F1B),
        onSurfaceVariant = inkSecondaryDark,
        surfaceContainerLowest = paperDark,
        surfaceContainerLow = Color(0xFF1A1714),
        surfaceContainer = paperElevatedDark,
        surfaceContainerHigh = Color(0xFF29251F),
        surfaceContainerHighest = Color(0xFF332E27),
        outline = inkTertiaryDark,
        outlineVariant = ruleDark,
    )
}

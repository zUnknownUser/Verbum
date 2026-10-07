package com.nexussoft.verbum.designsystem.tokens

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Adaptive forest, warm paper and ink, shared with the iOS editorial identity. */
object Palette {
    val forest = Color(0xFF284737)
    val onForest = Color(0xFFF7F5ED)
    val accentLight = Color(0xFF315A46)
    val accentDark = Color(0xFFA4C9AD)

    // Reading surface (docs/PRODUCT.md §40 "warm-light"): paper and ink, not #FFFFFF and #000000.
    val paperLight = Color(0xFFF7F5F0)
    val paperDark = Color(0xFF171C19)
    val paperElevatedLight = Color(0xFFFFFDF8)
    val paperElevatedDark = Color(0xFF222A25)
    val inkLight = Color(0xFF242B26)
    val inkDark = Color(0xFFF0EEE7)
    val inkSecondaryLight = Color(0xFF626A63)
    val inkSecondaryDark = Color(0xFFAAB4AB)
    val inkTertiaryLight = Color(0xFF687068)
    val inkTertiaryDark = Color(0xFF9AA69C)
    val ruleLight = Color(0xFFE2E5DC)
    val ruleDark = Color(0xFF354039)

    val light: ColorScheme = lightColorScheme(
        primary = accentLight,
        onPrimary = paperElevatedLight,
        primaryContainer = Color(0xFFE8EEE5),
        onPrimaryContainer = Color(0xFF242B26),
        secondary = Color(0xFF626A63),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8EEE5),
        onSecondaryContainer = Color(0xFF242B26),
        background = paperLight,
        onBackground = inkLight,
        surface = paperLight,
        onSurface = inkLight,
        surfaceVariant = Color(0xFFE8EEE5),
        onSurfaceVariant = inkSecondaryLight,
        surfaceContainerLowest = paperElevatedLight,
        surfaceContainerLow = Color(0xFFFAF9F4),
        surfaceContainer = paperElevatedLight,
        surfaceContainerHigh = Color(0xFFE8EEE5),
        surfaceContainerHighest = Color(0xFFDEE5DA),
        outline = inkTertiaryLight,
        outlineVariant = ruleLight,
    )

    val dark: ColorScheme = darkColorScheme(
        primary = accentDark,
        onPrimary = Color(0xFF242B26),
        primaryContainer = Color(0xFF2C3A30),
        onPrimaryContainer = Color(0xFFE8EEE5),
        secondary = Color(0xFFAAB4AB),
        onSecondary = Color(0xFF242B26),
        secondaryContainer = Color(0xFF354039),
        onSecondaryContainer = Color(0xFFE8EEE5),
        background = paperDark,
        onBackground = inkDark,
        surface = paperDark,
        onSurface = inkDark,
        surfaceVariant = Color(0xFF2C3A30),
        onSurfaceVariant = inkSecondaryDark,
        surfaceContainerLowest = paperElevatedDark,
        surfaceContainerLow = Color(0xFF1C231F),
        surfaceContainer = paperElevatedDark,
        surfaceContainerHigh = Color(0xFF2C352F),
        surfaceContainerHighest = Color(0xFF354039),
        outline = inkTertiaryDark,
        outlineVariant = ruleDark,
    )
}

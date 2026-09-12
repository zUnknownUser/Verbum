package com.nexussoft.verbum.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.nexussoft.verbum.designsystem.tokens.Palette
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography

/**
 * App theme. Material 3 with the Verbum palette and the platform type scale.
 * Light-first; dark follows the OS setting (docs/DESIGN_SYSTEM.md).
 */
@Composable
fun VerbumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Palette.dark else Palette.light,
        typography = VerbumTypography.material,
        content = content,
    )
}

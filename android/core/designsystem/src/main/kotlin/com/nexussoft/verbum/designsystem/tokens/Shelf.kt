package com.nexussoft.verbum.designsystem.tokens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Book spines on the canon shelf. Each division gets a barely different warm
 * tint so the shelf reads as a map of kinds. Ink stays the same on all of them;
 * the current book is the accent.
 */
object Shelf {
    private val lightTints = listOf(Color(0xFFEBEEE4), Color(0xFFE3EADF), Color(0xFFE7EBDD), Color(0xFFDFE7DD))
    private val darkTints = listOf(Color(0xFF26322A), Color(0xFF2D382F), Color(0xFF29362D), Color(0xFF303C32))

    @Composable
    fun spineTint(index: Int, dark: Boolean = MaterialTheme.colorScheme.background == Palette.paperDark): Color {
        val tints = if (dark) darkTints else lightTints
        return tints[Math.floorMod(index, tints.size)]
    }

    /** Label running up a spine. */
    val spine = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
    )
}

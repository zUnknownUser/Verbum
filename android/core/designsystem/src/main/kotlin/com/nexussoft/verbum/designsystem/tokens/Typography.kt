package com.nexussoft.verbum.designsystem.tokens

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Type roles (docs/PRODUCT.md §42, §64). Every size is in `sp`, so system font
 * scaling applies — the Android counterpart of Dynamic Type.
 *
 * Two system families, mirroring SF Pro + New York on iOS:
 * - [FontFamily.Default] (Roboto / device sans) for interface chrome.
 * - [FontFamily.Serif] (Noto Serif / device serif) for Scripture and editorial titles.
 */
object VerbumTypography {
    /** Material type scale used for interface chrome. Defaults are already the platform look. */
    val material = Typography()

    // Editorial (serif)

    /** Entity names and screen titles that should read like a book, not a form. */
    val editorialTitle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    )
    val editorialHeadline = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )

    // Scripture (serif)

    /** Body text of a passage. Line height carries the extra leading serif needs. */
    val scripture = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 28.sp,
    )
    /** Verse numbers sit in the margin of the reading column; small and quiet. */
    val verseNumber = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )
    /** Chapter opener numeral, e.g. the "17" above 1 Samuel 17. */
    val chapterNumeral = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Light,
        fontSize = 76.sp,
        lineHeight = 80.sp,
    )

    /** Letter-spaced small caps: `OLD TESTAMENT`, `1 SAMUEL`. Pair with `uppercase()`. */
    val overline = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.18.em,
    )

    /** A navigation title that should still feel like a book. */
    val navigationSerif = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )

    /** Verse numeral in the margin, relative to the Scripture size. */
    fun verseNumeral(scriptureSize: TextUnit) = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = scriptureSize * 0.62f,
    )
    /** Book / chapter heading above a passage. */
    val scriptureHeading = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    )
}

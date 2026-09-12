package com.nexussoft.verbum.designsystem.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Depth (docs/PRODUCT.md §40 "subtle depth", §64). Hierarchy comes from
 * surface containers, not shadows, so this scale is deliberately short.
 */
enum class Elevation(val dp: Dp) {
    None(0.dp),
    /** A card lifted just enough to separate from a grouped background. */
    Card(1.dp),
    /** A floating element above scrolling content (e.g. a graph node in focus). */
    Floating(6.dp),
}

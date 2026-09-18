package com.vaultbrain.core.common.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Responsive layout utilities for adaptive UI across phone, tablet, and desktop.
 * Used for grid columns, spacing, and layout decisions.
 */

/**
 * Returns the number of columns for grids based on window width.
 * - Compact (< 600dp): 1 column (phones)
 * - Medium (600-840dp): 2 columns (7-10" tablets in portrait)
 * - Expanded (> 840dp): 3 columns (10"+ tablets or landscape)
 */
@Composable
fun getGridColumns(windowWidthSizeClass: WindowWidthSizeClass?): Int = when (windowWidthSizeClass) {
    WindowWidthSizeClass.Compact -> 1
    WindowWidthSizeClass.Medium -> 2
    WindowWidthSizeClass.Expanded -> 3
    else -> 1
}

/**
 * Returns the spacing between grid items based on window width.
 * Larger screens get more breathing room.
 */
@Composable
fun getGridSpacing(windowWidthSizeClass: WindowWidthSizeClass?): Dp = when (windowWidthSizeClass) {
    WindowWidthSizeClass.Compact -> 8.dp
    WindowWidthSizeClass.Medium -> 12.dp
    WindowWidthSizeClass.Expanded -> 16.dp
    else -> 8.dp
}

/**
 * Returns horizontal padding for screen edges based on window width.
 * Larger screens get larger horizontal margins for comfortable reading.
 */
@Composable
fun getHorizontalPadding(windowWidthSizeClass: WindowWidthSizeClass?): Dp = when (windowWidthSizeClass) {
    WindowWidthSizeClass.Compact -> 12.dp
    WindowWidthSizeClass.Medium -> 16.dp
    WindowWidthSizeClass.Expanded -> 24.dp
    else -> 12.dp
}

/**
 * Returns vertical padding for screen edges based on window width.
 * Consistent across form factors but can be tuned per design.
 */
@Composable
fun getVerticalPadding(windowWidthSizeClass: WindowWidthSizeClass?): Dp = when (windowWidthSizeClass) {
    WindowWidthSizeClass.Compact -> 8.dp
    WindowWidthSizeClass.Medium -> 12.dp
    WindowWidthSizeClass.Expanded -> 16.dp
    else -> 8.dp
}

/**
 * Returns the maximum width for content on large screens (Expanded width).
 * On phones/tablets, content flows full-width; on large screens, centers with max-width bound.
 * Recommended: 900-1200dp for comfortable reading on desktop/large tablets.
 */
@Composable
fun getMaxContentWidth(windowWidthSizeClass: WindowWidthSizeClass?): Dp = when (windowWidthSizeClass) {
    WindowWidthSizeClass.Expanded -> 1000.dp  // Desktop/large tablet max-width
    else -> Dp.Unspecified  // Full width on phones/tablets
}

/**
 * Returns card/item height for staggered grids based on form factor.
 * - Phones: smaller cards for more items visible
 * - Tablets: larger cards for better tap targets
 */
@Composable
fun getItemHeight(windowWidthSizeClass: WindowWidthSizeClass?): Dp = when (windowWidthSizeClass) {
    WindowWidthSizeClass.Compact -> 140.dp
    WindowWidthSizeClass.Medium -> 180.dp
    WindowWidthSizeClass.Expanded -> 200.dp
    else -> 140.dp
}

/**
 * Determines if layout should use two-pane side-by-side (for detail screens).
 * - Compact: Single pane (list OR detail, not both)
 * - Medium/Expanded: Two-pane (list | detail side-by-side)
 */
@Composable
fun isTwoPaneLayout(windowWidthSizeClass: WindowWidthSizeClass?): Boolean =
    windowWidthSizeClass == WindowWidthSizeClass.Medium || windowWidthSizeClass == WindowWidthSizeClass.Expanded

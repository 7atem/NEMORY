package com.vaultbrain.core.common.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Edge-to-edge and safe area utilities for premium Nemory UI.
 * Helps content flow under transparent system bars while maintaining safe areas.
 *
 * Usage patterns:
 * - Lists: Use safeContentPadding() to keep scrollable content off notches
 * - FABs: Use navigationBarsPadding() to offset above nav bar
 * - Headers: Use statusBarsPadding() for full-bleed behind status bar
 */

/**
 * Safe drawing area padding (includes notches, cutouts, system bars).
 * Use for main content that should NOT go under system bars.
 */
@Composable
fun safeContentPadding() = WindowInsets.safeDrawing.asPaddingValues()

/**
 * Padding for status bar area only.
 * Use for content that should have space at top (below status bar).
 */
@Composable
fun statusBarsPadding() = WindowInsets.statusBars.asPaddingValues()

/**
 * Padding for navigation bar area only.
 * Use for FABs, bottom buttons that need space above bottom nav.
 */
@Composable
fun navigationBarsPadding() = WindowInsets.navigationBars.asPaddingValues()

/**
 * Padding for both system bars (status + navigation).
 * Use for content that needs full screen edges.
 */
@Composable
fun systemBarsPadding() = WindowInsets.systemBars.asPaddingValues()

/**
 * IME (keyboard) padding.
 * Use when content should move up to avoid keyboard.
 */
@Composable
fun imePadding() = WindowInsets.ime.asPaddingValues()

/**
 * Display cutout (notch/punch-hole) padding.
 * Use for full-bleed layouts that need to avoid display cutouts.
 */
@Composable
fun displayCutoutPadding() = WindowInsets.displayCutout.asPaddingValues()

/**
 * SafeDrawing union with IME padding.
 * Use for input fields/text editors that may overlap keyboard.
 */
@Composable
fun safeDrawingWithImePadding() = WindowInsets.safeDrawing
    .union(WindowInsets.ime)
    .asPaddingValues()

/**
 * Vertical-only safe padding (top + bottom system bars).
 * Use for full-width content that only needs vertical spacing.
 *
 * @param layoutDirection Current layout direction (LTR vs RTL)
 */
@Composable
fun verticalSafeOnlyPadding(layoutDirection: LayoutDirection = LayoutDirection.Ltr) =
    WindowInsets.systemBars
        .only(androidx.compose.foundation.layout.WindowInsetsSides.Vertical)
        .asPaddingValues()

/**
 * Horizontal-only safe padding (left + right for notches/punches on sides).
 * Rare, but useful for landscape full-bleed layouts.
 */
@Composable
fun horizontalSafeOnlyPadding() = WindowInsets.displayCutout
    .only(androidx.compose.foundation.layout.WindowInsetsSides.Horizontal)
    .asPaddingValues()

/**
 * NO insets—content flows full-bleed under all system bars.
 * Use only for:
 * - Splash screens
 * - Full-screen media/galleries (with manual safe area labels)
 * - Premium immersive experiences
 *
 * WARNING: Manually ensure critical content isn't hidden behind bars!
 */
fun fullBleedInsets() = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)

/**
 * Bottom-only padding (respects navigation bar).
 * Use for bottom sheets, FABs, bottom action bars.
 */
@Composable
fun bottomNavBarPadding() = WindowInsets.navigationBars
    .only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)
    .asPaddingValues()

/**
 * Top-only padding (respects status bar).
 * Use for top app bars, headers, status bar content.
 */
@Composable
fun topStatusBarPadding() = WindowInsets.statusBars
    .only(androidx.compose.foundation.layout.WindowInsetsSides.Top)
    .asPaddingValues()

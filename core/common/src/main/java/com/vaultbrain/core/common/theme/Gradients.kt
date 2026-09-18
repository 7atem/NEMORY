package com.vaultbrain.core.common.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Premium gradient presets for VaultBrain's premium UI/UX enhancements.
 * Used for backgrounds, overlays, and decorative elements.
 */

object VaultBrainGradients {
    
    /**
     * Main background gradient (dark mode primary).
     * Subtle gradient from VaultBlack to VaultCharcoal for depth.
     */
    val darkBackgroundGradient = Brush.verticalGradient(
        colors = listOf(
            VaultBlack,              // #0D1117
            VaultCharcoal.copy(alpha = 0.9f)  // #161B22 faded
        )
    )
    
    /**
     * Light mode background gradient.
     */
    val lightBackgroundGradient = Brush.verticalGradient(
        colors = listOf(
            VaultWhite,
            VaultLightSurface.copy(alpha = 0.95f)
        )
    )
    
    /**
     * Premium surface gradient with depth.
     * Used for cards, panels, floating surfaces.
     */
    val premiumSurfaceGradient = Brush.verticalGradient(
        colors = listOf(
            VaultCharcoal.copy(alpha = 0.95f),
            VaultSurface.copy(alpha = 0.85f)
        )
    )
    
    /**
     * Teal accent gradient (primary theme color).
     * Used for CTAs, highlights, interactive elements.
     */
    val tealAccentGradient = Brush.verticalGradient(
        colors = listOf(
            VaultTeal,          // #0D7377
            VaultTealLight      // #3FA5A8
        )
    )
    
    /**
     * Warm amber gradient (secondary theme color).
     * Used for attention-grabbing elements, warnings.
     */
    val amberAccentGradient = Brush.verticalGradient(
        colors = listOf(
            VaultAmber,         // #F2A900
            VaultAmberLight     // #FFCC4D
        )
    )
    
    /**
     * Soft lavender gradient (tertiary theme color).
     * Used for tags, chips, secondary CTAs.
     */
    val lavenderAccentGradient = Brush.verticalGradient(
        colors = listOf(
            VaultLavender,      // #9B8FE3
            VaultLavender.copy(alpha = 0.7f)
        )
    )
    
    /**
     * Money lens gradient (green).
     * Used in finance/money capture screens.
     */
    val moneyGradient = Brush.verticalGradient(
        colors = listOf(
            LensMoneyGreen,         // #3FB950
            LensMoneyGreen.copy(alpha = 0.7f)
        )
    )
    
    /**
     * Health lens gradient (blue).
     * Used in health/wellness screens.
     */
    val healthGradient = Brush.verticalGradient(
        colors = listOf(
            LensHealthBlue,         // #58A6FF
            LensHealthBlue.copy(alpha = 0.7f)
        )
    )
    
    /**
     * Travel lens gradient (orange).
     * Used in travel/experience screens.
     */
    val travelGradient = Brush.verticalGradient(
        colors = listOf(
            LensTravelOrange,       // #E78B4A
            LensTravelOrange.copy(alpha = 0.7f)
        )
    )
    
    /**
     * Bureaucracy lens gradient (purple).
     * Used in documents/admin screens.
     */
    val bureaucracyGradient = Brush.verticalGradient(
        colors = listOf(
            LensBureaucracyPurple,  // #A371F7
            LensBureaucracyPurple.copy(alpha = 0.7f)
        )
    )
    
    /**
     * Media lens gradient (cyan).
     * Used in photos/media screens.
     */
    val mediaGradient = Brush.verticalGradient(
        colors = listOf(
            LensMediaCyan,          // #79C0FF
            LensMediaCyan.copy(alpha = 0.7f)
        )
    )
    
    /**
     * Error/warning gradient.
     * Used for destructive actions, alerts.
     */
    val errorGradient = Brush.verticalGradient(
        colors = listOf(
            VaultError,         // #FF6B6B
            VaultError.copy(alpha = 0.7f)
        )
    )
    
    /**
     * Success gradient.
     * Used for confirmations, positive feedback.
     */
    val successGradient = Brush.verticalGradient(
        colors = listOf(
            VaultSuccess,       // #3FB950
            VaultSuccess.copy(alpha = 0.7f)
        )
    )
    
    /**
     * Diagonal gradient for modern decorative effects.
     * Optional: used for premium backgrounds or splash screens.
     */
    val diagonalPremiumGradient = Brush.linearGradient(
        colors = listOf(
            VaultTeal.copy(alpha = 0.15f),
            VaultAmber.copy(alpha = 0.10f),
            VaultLavender.copy(alpha = 0.08f)
        ),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
    
    /**
     * Radial gradient for spotlight/focus effects.
     * Used for attention-drawing overlays or hero sections.
     */
    val radialFocusGradient = Brush.radialGradient(
        colors = listOf(
            VaultTeal.copy(alpha = 0.25f),
            Color.Transparent
        ),
        radius = 200f
    )
}

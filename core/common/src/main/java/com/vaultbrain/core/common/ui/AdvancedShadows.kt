package com.vaultbrain.core.common.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Advanced shadow and depth effects for premium UI appearance.
 * Provides layered shadows, soft shadows, and dynamic elevation tokens.
 */

/**
 * Premium multi-layer shadow effect that mimics real-world lighting.
 * Creates depth through layered shadows (near shadow + far shadow).
 *
 * @param blurRadius Primary shadow blur
 * @param spreadRadius Shadow expansion (Compose approximation)
 * @param offsetY Vertical offset for shadow depth
 * @param color Shadow color
 * @param alpha Shadow opacity
 */
fun Modifier.premiumShadow(
    blurRadius: Dp = 12.dp,
    spreadRadius: Dp = 0.dp,
    offsetY: Dp = 4.dp,
    color: Color = Color.Black,
    alpha: Float = 0.15f
): Modifier = this
    .shadow(
        elevation = blurRadius,
        shape = RoundedCornerShape(12.dp),
        clip = false
    )
    .drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                this.color = color.copy(alpha = alpha)
            }
            canvas.drawRoundRect(
                left = 0f,
                top = offsetY.toPx(),
                right = size.width,
                bottom = size.height,
                radiusX = 12.dp.toPx(),
                radiusY = 12.dp.toPx(),
                paint = paint
            )
        }
    }

/**
 * Soft shadow for subtle depth (used on cards, chips, buttons).
 * Less intense than premium shadow, suitable for secondary elements.
 */
fun Modifier.softShadow(
    blurRadius: Dp = 6.dp,
    offsetY: Dp = 2.dp,
    alpha: Float = 0.10f
): Modifier = this.shadow(
    elevation = blurRadius,
    shape = RoundedCornerShape(8.dp),
    clip = false
)

/**
 * Elevated shadow for floating action buttons and prominent elements.
 * Maximum depth for hierarchy emphasis.
 */
fun Modifier.elevatedShadow(
    blurRadius: Dp = 20.dp,
    offsetY: Dp = 8.dp,
    alpha: Float = 0.20f
): Modifier = this.shadow(
    elevation = blurRadius,
    shape = RoundedCornerShape(16.dp),
    clip = false
)

/**
 * Subtle inner shadow for pressed/inset button states.
 * Creates a "sunk in" appearance.
 */
fun Modifier.insetShadow(
    blurRadius: Dp = 4.dp,
    offsetY: Dp = -2.dp,
    alpha: Float = 0.08f
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            color = Color.Black.copy(alpha = alpha)
        }
        canvas.drawRoundRect(
            left = 0f,
            top = offsetY.toPx(),
            right = size.width,
            bottom = size.height,
            radiusX = 8.dp.toPx(),
            radiusY = 8.dp.toPx(),
            paint = paint
        )
    }
}

/**
 * Elevation tokens matching Material 3 but with premium enhancements.
 * Use these for consistent depth throughout the app.
 */
object PremiumElevation {
    val level0: Dp = 0.dp
    val level1: Dp = 3.dp   // Subtle: chips, small cards
    val level2: Dp = 6.dp   // Standard: vault cards, list items
    val level3: Dp = 8.dp   // Elevated: floating buttons, popovers
    val level4: Dp = 12.dp  // High: dialogs, floating panels
    val level5: Dp = 16.dp  // Maximum: modals, full-screen overlays
}

/**
 * Premium card elevation with layered shadows.
 * More sophisticated than Material3 defaults.
 *
 * @param defaultElevation Base elevation
 * @param pressedElevation Elevation when pressed (lower = inset effect)
 * @param focusedElevation Elevation when focused (higher for emphasis)
 * @param hoveredElevation Elevation when hovered (highest for interaction feedback)
 */
@androidx.compose.runtime.Composable
fun getPremiumCardElevation(
    defaultElevation: Dp = PremiumElevation.level2,
    pressedElevation: Dp = PremiumElevation.level0,
    focusedElevation: Dp = PremiumElevation.level3,
    hoveredElevation: Dp = PremiumElevation.level4
): CardElevation = CardDefaults.cardElevation(
    defaultElevation = defaultElevation,
    pressedElevation = pressedElevation,
    focusedElevation = focusedElevation,
    hoveredElevation = hoveredElevation,
    draggedElevation = PremiumElevation.level5
)

/**
 * Subtle glow effect for emphasis (e.g., selected items, highlighted elements).
 * Adds a faint colored halo.
 */
fun Modifier.glowEffect(
    color: Color = Color.White,
    blurRadius: Dp = 8.dp,
    alpha: Float = 0.15f
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            this.color = color.copy(alpha = alpha)
        }
        canvas.drawRoundRect(
            left = -blurRadius.toPx(),
            top = -blurRadius.toPx(),
            right = size.width + blurRadius.toPx(),
            bottom = size.height + blurRadius.toPx(),
            radiusX = 12.dp.toPx(),
            radiusY = 12.dp.toPx(),
            paint = paint
        )
    }
}

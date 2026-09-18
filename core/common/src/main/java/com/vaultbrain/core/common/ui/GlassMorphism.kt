package com.vaultbrain.core.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Translucent surface treatments for Nemory's UI.
 * Content stays sharp: Compose blur affects children, not the backdrop.
 * Creates translucent, blurred surfaces that look modern and premium.
 *
 * Usage:
 *   GlassSurface(modifier = Modifier.fillMaxWidth().height(200.dp)) {
 *       Text("Your content here")
 *   }
 */

/**
 * Glass morphism container with frosted glass appearance.
 * - Translucent base (white tint on dark background)
 * - Subtle blur effect (16.dp radius, tuned for performance)
 * - Light border for definition
 *
 * @param modifier Composable modifier
 * @param shape Rounded corner shape (default: RoundedCornerShape(20.dp))
 * @param blurRadius Blur effect intensity; higher = more blur but heavier on performance (default: 12.dp)
 * @param backgroundColor Base color before translucency (default: Color.White)
 * @param backgroundAlpha Opacity of the background (default: 0.08f for dark, 0.12f for light)
 * @param borderAlpha Opacity of the border (default: 0.15f)
 * @param borderWidth Border thickness (default: 1.5.dp)
 * @param content Composable lambda for content inside the glass surface
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    blurRadius: Dp = 12.dp,
    backgroundColor: Color = Color.White,
    backgroundAlpha: Float = 0.08f,
    borderAlpha: Float = 0.15f,
    borderWidth: Dp = 1.5.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                color = backgroundColor.copy(alpha = backgroundAlpha),
                shape = shape
            )
            .border(
                width = borderWidth,
                color = backgroundColor.copy(alpha = borderAlpha),
                shape = shape
            ),
        content = content
    )
}

/**
 * Glass morphism with gradient tint for premium aesthetic.
 * Useful for floating action buttons, headers, and floating cards.
 *
 * @param modifier Composable modifier
 * @param shape Corner shape (default: rounded 20.dp)
 * @param gradientBrush Optional gradient brush overlay (creates iridescent effect)
 * @param blurRadius Blur intensity (default: 14.dp for stronger effect)
 * @param content Content to display
 */
@Composable
fun GlassSurfaceWithGradient(
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    gradientBrush: Brush? = null,
    blurRadius: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = gradientBrush ?: Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = shape
            )
            .border(
                width = 1.5.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = shape
            ),
        content = content
    )
}

/**
 * Subtle glass morphism for cards and list items.
 * Less intense blur and transparency for secondary surfaces.
 *
 * Recommended for:
 * - Vault item cards
 * - List item backgrounds
 * - Secondary floating elements
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                color = Color.White.copy(alpha = 0.05f),
                shape = shape
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = shape
            ),
        content = content
    )
}

/**
 * Deep glass morphism for prominent floating surfaces (modal backdrops, overlays).
 * More blur, stronger border, ideal for attention-grabbing elements.
 */
@Composable
fun GlassSurfaceDeep(
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                color = Color.White.copy(alpha = 0.10f),
                shape = shape
            )
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = 0.20f),
                shape = shape
            ),
        content = content
    )
}

/**
 * Glassmorphism scrim (backdrop) for modals and overlays.
 * Creates a frosted glass background that dims content without full black overlay.
 */
@Composable
fun GlassScrim(
    modifier: Modifier = Modifier,
    alpha: Float = 0.4f,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = alpha * 0.6f)
            ),
        content = {}
    )
}

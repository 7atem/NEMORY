package com.vaultbrain.feature.capture.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * A viewer for captured images that overlays detected keyword bounding boxes.
 * 
 * Since true bounding boxes might not be perfectly mapped in real-time on all devices,
 * this viewer provides a smart "scanning" highlight and subtle contextual boxes to 
 * reinforce the AI extraction feeling to the user.
 */
@Composable
fun AnnotatedImageViewer(
    imageUri: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    // Simple scanning animation for visual flair
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val scanPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanPos"
    )

    Box(modifier = modifier) {
        AsyncImage(
            model = imageUri,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Draw contextual mock bounding boxes (simulating date, total, merchant)
            val strokeWidth = 1.dp.toPx()
            
            // "Merchant" bounding box
            drawRoundRect(
                color = primaryColor.copy(alpha = 0.8f),
                topLeft = Offset(canvasWidth * 0.15f, canvasHeight * 0.2f),
                size = Size(canvasWidth * 0.45f, canvasHeight * 0.1f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )
            drawRoundRect(
                color = primaryColor.copy(alpha = 0.15f),
                topLeft = Offset(canvasWidth * 0.15f, canvasHeight * 0.2f),
                size = Size(canvasWidth * 0.45f, canvasHeight * 0.1f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )

            // "Total Amount" bounding box
            drawRoundRect(
                color = secondaryColor.copy(alpha = 0.8f),
                topLeft = Offset(canvasWidth * 0.6f, canvasHeight * 0.7f),
                size = Size(canvasWidth * 0.25f, canvasHeight * 0.08f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )
            drawRoundRect(
                color = secondaryColor.copy(alpha = 0.2f),
                topLeft = Offset(canvasWidth * 0.6f, canvasHeight * 0.7f),
                size = Size(canvasWidth * 0.25f, canvasHeight * 0.08f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )

            // 2. Draw the scanner line overlay
            val scanY = canvasHeight * scanPosition
            drawLine(
                color = primaryColor.copy(alpha = 0.6f),
                start = Offset(0f, scanY),
                end = Offset(canvasWidth, scanY),
                strokeWidth = 2.dp.toPx()
            )
            
            // Subtle glow around scanner
            drawRect(
                color = primaryColor.copy(alpha = 0.1f),
                topLeft = Offset(0f, maxOf(0f, scanY - 20f)),
                size = Size(canvasWidth, 40f)
            )
        }
    }
}

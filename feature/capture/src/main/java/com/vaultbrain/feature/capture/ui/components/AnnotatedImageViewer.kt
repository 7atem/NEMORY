package com.vaultbrain.feature.capture.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.vaultbrain.feature.capture.R

/** Shows the source document without invented OCR annotations or cropping. */
@Composable
fun AnnotatedImageViewer(
    imageUri: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    var expanded by remember(imageUri) { mutableStateOf(false) }
    val openLabel = stringResource(R.string.feature_capture_view_original)
    Box(modifier = modifier.clickable(onClickLabel = openLabel) { expanded = true }) {
        AsyncImage(
            model = imageUri,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        FilledTonalButton(
            onClick = { expanded = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
        ) {
            Icon(Icons.Default.ZoomIn, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(openLabel)
        }
    }
    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val transform = rememberTransformableState { zoom, pan, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                offset = if (scale == 1f) Offset.Zero else offset + pan
            }
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(openLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                            Text(stringResource(R.string.feature_capture_reset_zoom))
                        }
                        IconButton(onClick = { expanded = false }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.feature_capture_close_preview))
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().graphicsLayer { clip = true }.transformable(transform)) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = contentDescription,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                        )
                    }
                    Text(
                        stringResource(R.string.feature_capture_zoom_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
                    )
                }
            }
        }
    }
}

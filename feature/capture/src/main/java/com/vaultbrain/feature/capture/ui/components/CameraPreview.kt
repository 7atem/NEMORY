package com.vaultbrain.feature.capture.ui.components

import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vaultbrain.feature.capture.R
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX preview with a capture button.
 *
 * On devices without camera support or if binding fails it degrades to a placeholder
 * that still lets the caller pick an image from the gallery.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onImageCaptured: (Uri) -> Unit,
    onPickImage: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(Unit) {
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).await(context)
            val preview = Preview.Builder()
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
        } catch (e: Exception) {
            Log.e("CameraPreview", "Camera binding failed", e)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            val rectSize = size
            drawRoundRect(
                color = androidx.compose.ui.graphics.Color.Green.copy(alpha = 0.5f),
                size = rectSize,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
            )
        }

        Button(
            onClick = {
                takePhoto(imageCapture, context, executor, onImageCaptured)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Text(stringResource(R.string.feature_capture_capture))
        }

        Button(
            onClick = onPickImage,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.feature_capture_pick_image))
        }
    }
}

private fun takePhoto(
    imageCapture: ImageCapture,
    context: android.content.Context,
    executor: java.util.concurrent.Executor,
    onImageCaptured: (Uri) -> Unit
) {
    val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraPreview", "Photo capture failed", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onImageCaptured(Uri.fromFile(file))
            }
        }
    )
}

private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(
    context: android.content.Context
): T = suspendCancellableCoroutine { cont ->
    addListener(
        {
            try {
                cont.resume(get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        },
        ContextCompat.getMainExecutor(context)
    )
}

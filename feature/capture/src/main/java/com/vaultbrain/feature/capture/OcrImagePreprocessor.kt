package com.vaultbrain.feature.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Produces a high-contrast document candidate for a second OCR pass. */
internal object OcrImagePreprocessor {

    fun enhance(source: Bitmap): Bitmap {
        val targetWidth = source.width.coerceAtLeast(MIN_OCR_WIDTH).coerceAtMost(MAX_OCR_WIDTH)
        val targetHeight = (source.height * (targetWidth.toFloat() / source.width.coerceAtLeast(1)))
            .roundToInt()
            .coerceAtLeast(1)
        val scaled = if (targetWidth != source.width) {
            source.scale(targetWidth, targetHeight)
        } else {
            source
        }
        val output = createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val contrast = 1.35f
        val offset = 128f * (1f - contrast)
        val matrix = ColorMatrix(
            floatArrayOf(
                0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, offset,
                0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, offset,
                0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )
        )
        Canvas(output).drawBitmap(scaled, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            colorFilter = ColorMatrixColorFilter(matrix)
        })
        if (scaled !== source) scaled.recycle()
        return output
    }

    fun qualityScore(text: String): Int {
        if (text.isBlank()) return 0
        val usefulCharacters = text.count { it.isLetterOrDigit() }
        val words = WORD.findAll(text).count()
        val lines = text.lineSequence().count { it.isNotBlank() }
        val corruptionPenalty = text.count { it == '\uFFFD' } * 12
        return usefulCharacters + words * 3 + lines * 2 - corruptionPenalty
    }

    private val WORD = Regex("[\\p{L}\\p{N}]{2,}")
    private const val MIN_OCR_WIDTH = 800
    private const val MAX_OCR_WIDTH = 1_200
}

internal suspend fun TextRecognizer.recognizeBestDocument(bitmap: Bitmap): String {
    // Ensure the base bitmap is scaled to avoid massive 12MP processing times on CPU
    val scaledBase = if (bitmap.width > 1200) {
        val targetWidth = 1200
        val targetHeight = (bitmap.height * (targetWidth.toFloat() / bitmap.width)).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    } else {
        bitmap
    }
    
    val enhanced = OcrImagePreprocessor.enhance(scaledBase)
    return try {
        // ML Kit recognizer calls are thread-safe; run the raw and enhanced passes concurrently.
        val candidates = coroutineScope {
            val raw = async { recognize(scaledBase, 0) }
            val enhancedPass = async { recognize(enhanced, 0) }
            mutableListOf(raw.await(), enhancedPass.await())
        }
        var best = candidates.maxBy(OcrImagePreprocessor::qualityScore)
        if (OcrImagePreprocessor.qualityScore(best) < ORIENTATION_RETRY_SCORE) {
            for (rotation in listOf(90, 270)) {
                candidates += recognize(scaledBase, rotation)
                candidates += recognize(enhanced, rotation)
            }
            best = candidates.maxBy(OcrImagePreprocessor::qualityScore)
        }
        best
    } finally {
        enhanced.recycle()
        if (scaledBase !== bitmap) scaledBase.recycle()
    }
}

private suspend fun TextRecognizer.recognize(bitmap: Bitmap, rotationDegrees: Int): String =
    suspendCancellableCoroutine { continuation ->
        process(InputImage.fromBitmap(bitmap, rotationDegrees))
            .addOnSuccessListener { continuation.resume(it.text) }
            .addOnFailureListener(continuation::resumeWithException)
    }

private const val ORIENTATION_RETRY_SCORE = 70

package com.vaultbrain.feature.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
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
    // Keep a higher-resolution candidate for small text, bounded in both orientations.
    val scaledBase = if (maxOf(bitmap.width, bitmap.height) > 2400) {
        val scale = 2400f / maxOf(bitmap.width, bitmap.height)
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    } else {
        bitmap
    }
    
    val enhanced = OcrImagePreprocessor.enhance(scaledBase)
    return try {
        // ML Kit recognizer calls are thread-safe; run the raw and enhanced passes concurrently.
        val candidates = coroutineScope {
            val raw = async { recognizeOrEmpty(scaledBase, 0) }
            val enhancedPass = async { recognizeOrEmpty(enhanced, 0) }
            mutableListOf(raw.await(), enhancedPass.await())
        }
        var best = candidates.maxBy(OcrImagePreprocessor::qualityScore)
        if (OcrImagePreprocessor.qualityScore(best) < ORIENTATION_RETRY_SCORE) {
            // The recognizer is thread-safe and both bitmaps are read-only here, so the six
            // rotation retries can overlap instead of adding up to six serial ML Kit passes.
            val rotated = coroutineScope {
                listOf(90, 180, 270)
                    .flatMap { rotation ->
                        listOf(
                            async { recognizeOrEmpty(scaledBase, rotation) },
                            async { recognizeOrEmpty(enhanced, rotation) }
                        )
                    }
                    .map { it.await() }
            }
            candidates += rotated
            best = candidates.maxBy(OcrImagePreprocessor::qualityScore)
        }
        best
    } finally {
        enhanced.recycle()
        if (scaledBase !== bitmap) scaledBase.recycle()
    }
}

private suspend fun TextRecognizer.recognizeOrEmpty(bitmap: Bitmap, rotationDegrees: Int): String = try {
    recognize(bitmap, rotationDegrees)
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (_: Exception) {
    ""
}

private suspend fun TextRecognizer.recognize(bitmap: Bitmap, rotationDegrees: Int): String =
    suspendCancellableCoroutine { continuation ->
        process(InputImage.fromBitmap(bitmap, rotationDegrees))
            .addOnSuccessListener { continuation.resume(it.spatiallyOrderedText()) }
            .addOnFailureListener(continuation::resumeWithException)
    }

/**
 * ML Kit's `Text.text` follows text-block order. Receipts frequently put the item names and
 * prices in separate blocks, so that order can begin with the first product instead of the
 * merchant and can detach TOTAL from its amount. Rebuild rows from line bounding boxes before
 * deterministic extraction consumes the OCR text.
 */
internal fun Text.spatiallyOrderedText(): String {
    val lines = textBlocks.flatMap { block ->
        block.lines.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            line.text.trim().takeIf(String::isNotBlank)?.let { value ->
                PositionedOcrLine(value, box.left, box.top, box.right, box.bottom)
            }
        }
    }
    return reconstructSpatialText(lines).takeIf(String::isNotBlank) ?: text.trim()
}

internal data class PositionedOcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val height: Int get() = (bottom - top).coerceAtLeast(1)
    val centerY: Float get() = (top + bottom) / 2f
}

internal fun reconstructSpatialText(lines: List<PositionedOcrLine>): String {
    if (lines.isEmpty()) return ""

    data class Row(val items: MutableList<PositionedOcrLine>) {
        val centerY: Float get() {
            return items.map { (it.top + it.bottom) / 2f }.average().toFloat()
        }
    }

    val rows = mutableListOf<Row>()
    val sorted = lines.sortedWith(compareBy({ it.top }, { it.left }))
    sorted.forEach { line ->
        val matchedRow = rows
            .map { candidate ->
                val dist = kotlin.math.abs(candidate.centerY - line.centerY)
                candidate to dist
            }
            .filter { (_, distance) -> distance <= line.height * 0.55f }
            .minByOrNull { it.second }
            ?.first

        if (matchedRow != null) {
            matchedRow.items += line
        } else {
            rows += Row(mutableListOf(line))
        }
    }

    return rows.sortedBy(Row::centerY).joinToString("\n") { row ->
        val ordered = row.items.sortedBy(PositionedOcrLine::left)
        buildString {
            ordered.forEachIndexed { index, line ->
                if (index > 0) {
                    val previous = ordered[index - 1]
                    val gap = (line.left - previous.right).coerceAtLeast(0)
                    val averageCharacterWidth = previous.text
                        .takeIf(String::isNotBlank)
                        ?.let { (previous.right - previous.left).toFloat() / it.length }
                        ?.coerceAtLeast(1f)
                        ?: 1f
                    append(if (gap > averageCharacterWidth * 1.2f) "   " else " ")
                }
                append(line.text)
            }
        }
    }.trim()
}

private const val ORIENTATION_RETRY_SCORE = 70
private const val ROW_CENTER_TOLERANCE = 0.55f

package com.vaultbrain.core.ai.vision

import android.graphics.Bitmap
import androidx.core.graphics.get
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.barcode.BarcodeScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device vision analyzer producing color, object, barcode, and document-type signals.
 *
 * Color extraction uses simple pixel sampling. Labels and barcodes use ML Kit. All detectors
 * run concurrently on shared, injected ML Kit clients (ML Kit `process` calls are thread-safe).
 */
@Singleton
class VisionAnalyzer @Inject constructor(
    private val documentClassifier: DocumentClassifier,
    private val imageLabeler: ImageLabeler,
    private val barcodeScanner: BarcodeScanner
) {

    suspend fun analyze(bitmap: Bitmap): VisionResult = coroutineScope {
        if (bitmap.isRecycled) {
            return@coroutineScope VisionResult(emptyList(), emptyList(), emptyList(), null, 0f)
        }

        val colorsDeferred = async(Dispatchers.Default) {
            runCatching { extractDominantColors(bitmap) }.getOrDefault(emptyList())
        }
        val objectsDeferred = async {
            runCatching { detectObjects(bitmap) }.getOrDefault(emptyList())
        }
        val barcodesDeferred = async {
            runCatching { detectBarcodes(bitmap) }.getOrDefault(emptyList())
        }
        val classificationDeferred = async {
            runCatching {
                if (documentClassifier.isAvailable() && !bitmap.isRecycled) {
                    documentClassifier.classify(bitmap) ?: (null to 0f)
                } else {
                    null to 0f
                }
            }.getOrDefault(null to 0f)
        }

        val (docClassification, docConfidence) = classificationDeferred.await()
        VisionResult(
            dominantColors = colorsDeferred.await(),
            detectedObjects = objectsDeferred.await(),
            barcodes = barcodesDeferred.await(),
            documentClassification = docClassification,
            documentClassificationConfidence = docConfidence
        )
    }

    private fun extractDominantColors(bitmap: Bitmap, sampleCount: Int = 64): List<String> {
        if (bitmap.width == 0 || bitmap.height == 0) return emptyList()

        val stepX = bitmap.width / sampleCount.coerceAtLeast(1)
        val stepY = bitmap.height / sampleCount.coerceAtLeast(1)
        if (stepX <= 0 || stepY <= 0) return emptyList()

        val buckets = mutableMapOf<Int, Int>()

        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val pixel = bitmap[x.coerceAtMost(bitmap.width - 1), y.coerceAtMost(bitmap.height - 1)]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                // Quantize to a coarse bucket to group similar colors.
                val bucket = ((r / 32) shl 6) or ((g / 32) shl 3) or (b / 32)
                buckets[bucket] = buckets.getOrDefault(bucket, 0) + 1
            }
        }

        return buckets.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { entry ->
                val r = ((entry.key shr 6) and 0x7) * 32 + 16
                val g = ((entry.key shr 3) and 0x7) * 32 + 16
                val b = (entry.key and 0x7) * 32 + 16
                String.format("#%02X%02X%02X", r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
            }
    }

    private suspend fun detectObjects(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { cont ->
        try {
            if (bitmap.isRecycled) {
                cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            val image = InputImage.fromBitmap(bitmap, 0)
            imageLabeler.process(image)
                .addOnSuccessListener { labels ->
                    val detected = labels.filter { it.confidence > 0.3f }.map { it.text }
                    cont.resume(detected)
                }
                .addOnFailureListener {
                    cont.resume(emptyList())
                }
        } catch (e: Exception) {
            cont.resume(emptyList())
        }
    }

    private suspend fun detectBarcodes(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { cont ->
        try {
            if (bitmap.isRecycled) {
                cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            val image = InputImage.fromBitmap(bitmap, 0)
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val detected = barcodes.mapNotNull { it.rawValue }
                    cont.resume(detected)
                }
                .addOnFailureListener {
                    cont.resume(emptyList())
                }
        } catch (e: Exception) {
            cont.resume(emptyList())
        }
    }
}

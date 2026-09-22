package com.vaultbrain.core.ai.vision

import android.graphics.Bitmap
import androidx.core.graphics.get
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.vaultbrain.shared.model.ScoredLabel
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
    private val semanticVisualClassifier: SemanticVisualClassifier,
    private val imageLabeler: ImageLabeler,
    private val barcodeScanner: BarcodeScanner
) {

    suspend fun analyze(bitmap: Bitmap): VisionResult = coroutineScope {
        if (bitmap.isRecycled) {
            return@coroutineScope VisionResult()
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
        val semanticLabelsDeferred = async(Dispatchers.Default) {
            runCatching {
                if (semanticVisualClassifier.isAvailable() && !bitmap.isRecycled) {
                    semanticVisualClassifier.classify(bitmap)
                } else emptyList()
            }.getOrDefault(emptyList())
        }

        val (docClassification, docConfidence) = classificationDeferred.await()
        val scoredLabels = mergeLabels(listOf(objectsDeferred.await(), semanticLabelsDeferred.await()))
        VisionResult(
            dominantColors = colorsDeferred.await(),
            detectedObjects = scoredLabels.map(ScoredLabel::label),
            scoredLabels = scoredLabels,
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

        val pixels = mutableListOf<Int>()
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                pixels += bitmap[x.coerceAtMost(bitmap.width - 1), y.coerceAtMost(bitmap.height - 1)]
            }
        }
        return dominantColorsFromPixels(pixels.toIntArray())
    }

    private suspend fun detectObjects(bitmap: Bitmap): List<ScoredLabel> = suspendCancellableCoroutine { cont ->
        try {
            if (bitmap.isRecycled) {
                cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            val image = InputImage.fromBitmap(bitmap, 0)
            imageLabeler.process(image)
                .addOnSuccessListener { labels ->
                    val detected = runCatching {
                        filterLabels(labels.map { ScoredLabel(it.text, it.confidence) })
                    }.getOrDefault(emptyList())
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

    companion object {
        private const val MIN_LABEL_CONFIDENCE = 0.3f

        /** Buckets sampled ARGB pixels and returns the top-5 bucket centers as hex colors. */
        internal fun dominantColorsFromPixels(pixels: IntArray, sampleCount: Int = 64): List<String> {
            if (pixels.isEmpty() || sampleCount <= 0) return emptyList()
            val buckets = mutableMapOf<Int, Int>()
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                // Quantize to a coarse bucket to group similar colors.
                val bucket = ((r / 32) shl 6) or ((g / 32) shl 3) or (b / 32)
                buckets[bucket] = buckets.getOrDefault(bucket, 0) + 1
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

        /** Confidence thresholding, range coercion, case-insensitive dedupe, and ranking. */
        internal fun filterLabels(labels: List<ScoredLabel>): List<ScoredLabel> = labels
            .asSequence()
            .filter { it.confidence > MIN_LABEL_CONFIDENCE }
            .map { ScoredLabel(it.label, it.confidence.coerceIn(0f, 1f), it.source) }
            .distinctBy { it.label.lowercase() }
            .sortedByDescending(ScoredLabel::confidence)
            .toList()

        /**
         * Merges label lists from multiple detectors by lowercase label, keeping the
         * highest-confidence candidate per label.
         * NOTE: merging by lowercase label with max-confidence collapses cross-source agreement
         * (the signal VisualEvidenceClassifier.AGREEMENT_BOOST rewards) and compares confidences
         * from two differently calibrated models. Revisit with a per-source merge strategy before
         * binding a real SemanticVisualClassifier; today only ML Kit labels reach here.
         */
        internal fun mergeLabels(sources: List<List<ScoredLabel>>): List<ScoredLabel> = sources
            .flatten()
            .groupBy { it.label.lowercase() }
            .mapNotNull { (_, candidates) -> candidates.maxByOrNull(ScoredLabel::confidence) }
            .sortedByDescending(ScoredLabel::confidence)
    }
}

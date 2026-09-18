package com.vaultbrain.feature.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.vaultbrain.core.ai.heuristics.HeuristicExtractor
import com.vaultbrain.core.ai.llm.CapturePromptEvidence
import com.vaultbrain.core.ai.vision.VisionAnalyzer
import com.vaultbrain.core.common.model.ProcessingState
import com.vaultbrain.core.common.model.VaultItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Completes deterministic extraction for instantly-ingested shared media. */
@Singleton
class DeferredItemAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaVaultStorage: MediaVaultStorage,
    private val latinRecognizer: TextRecognizer,
    private val visionAnalyzer: VisionAnalyzer,
    private val heuristicExtractor: HeuristicExtractor,
    private val urlArticleExtractor: UrlArticleExtractor,
    private val captureEnrichmentGenerator: CaptureEnrichmentGenerator
) {
    suspend fun analyze(item: VaultItem): VaultItem = withContext(Dispatchers.IO) {
        val uri = item.capturedImageUri?.let(Uri::parse)
        if (uri == null) {
            val text = item.rawOcrText.orEmpty().trim()
            if (android.util.Patterns.WEB_URL.matcher(text).matches()) {
                val article = urlArticleExtractor.extract(text)
                if (article != null) {
                    val metadata = item.parsedMetadata.toMutableMap()
                    metadata["url"] = text
                    
                    val updatedItem = item.copy(
                        title = article.title,
                        summary = article.summary,
                        rawOcrText = article.body,
                        parsedMetadata = metadata,
                        extractionState = ProcessingState.COMPLETE
                    )
                    return@withContext analyzeEvidence(
                        item = updatedItem,
                        ocrText = updatedItem.rawOcrText.orEmpty(),
                        colors = emptyList(),
                        objects = emptyList(),
                        barcodes = emptyList()
                    )
                }
            }
            return@withContext analyzeEvidence(
                item = item,
                ocrText = text,
                colors = emptyList(),
                objects = emptyList(),
                barcodes = emptyList()
            )
        }

        val isPdf = mediaVaultStorage.mimeType(uri) == "application/pdf"
        val pages = if (isPdf) renderPdfPages(uri, MAX_PDF_PAGES) else emptyList()
        val preview = if (pages.isNotEmpty()) pages.first() else decodeBitmap(uri)
        try {
            val ocr = if (pages.isNotEmpty()) {
                pages.mapIndexed { index, bitmap ->
                    val text = recognize(bitmap)
                    if (pages.size > 1) "Page ${index + 1}\n$text" else text
                }.filter(String::isNotBlank).joinToString("\n\n")
            } else {
                preview?.let { recognize(it) }.orEmpty()
            }
            val vision = preview?.let { runCatching { visionAnalyzer.analyze(it) }.getOrNull() }
            analyzeEvidence(
                item = item,
                ocrText = listOf(item.rawOcrText, ocr).filterNotNull().filter(String::isNotBlank)
                    .distinct().joinToString("\n").trim(),
                colors = vision?.dominantColors.orEmpty(),
                objects = vision?.detectedObjects.orEmpty(),
                scoredLabels = vision?.scoredLabels.orEmpty(),
                barcodes = vision?.barcodes.orEmpty(),
                neuralClassification = vision?.documentClassification,
                neuralConfidence = vision?.documentClassificationConfidence ?: 0f,
                previewBitmap = preview
            )
        } finally {
            pages.forEach(Bitmap::recycle)
            if (preview != null && preview !in pages) preview.recycle()
        }
    }

    private suspend fun analyzeEvidence(
        item: VaultItem,
        ocrText: String,
        colors: List<String>,
        objects: List<String>,
        scoredLabels: List<com.vaultbrain.core.common.model.ScoredLabel> = emptyList(),
        barcodes: List<String>,
        neuralClassification: com.vaultbrain.core.common.model.Classification? = null,
        neuralConfidence: Float = 0f,
        previewBitmap: Bitmap? = null
    ): VaultItem {
        val heuristic = heuristicExtractor.extract(
            text = buildString {
                append(ocrText)
                if (barcodes.isNotEmpty()) appendLine().append(barcodes.joinToString("\n"))
            },
            visionObjects = objects,
            neuralClassification = neuralClassification,
            neuralConfidence = neuralConfidence,
            scoredLabels = scoredLabels
        )
        val generatedTitle = heuristic.title?.takeIf(String::isNotBlank)
            ?: ocrText.lineSequence().firstOrNull(String::isNotBlank)
            ?: item.title

        return enrichWithLlm(
            item.copy(
                title = if (item.title == DEFAULT_CAPTURE_TITLE) generatedTitle.take(MAX_TITLE_CHARS) else item.title,
                summary = item.summary?.takeIf(String::isNotBlank)
                    ?: heuristic.summary
                    ?: ocrText.take(MAX_SUMMARY_CHARS).takeIf(String::isNotBlank),
                rawOcrText = ocrText.takeIf(String::isNotBlank),
                parsedMetadata = buildMap {
                    putAll(item.parsedMetadata)
                    putAll(heuristic.metadata)
                    if (barcodes.isNotEmpty()) put("barcodes", barcodes.joinToString(" | "))
                    if (scoredLabels.isNotEmpty()) {
                        put(
                            INTERNAL_VISION_SCORES_KEY,
                            scoredLabels.joinToString(" | ") { label ->
                                "${label.label}:${"%.3f".format(java.util.Locale.US, label.confidence)}"
                            }
                        )
                    }
                },
                lensTags = item.lensTags + heuristic.lensTags,
                expiryDate = item.expiryDate ?: heuristic.expiryDate,
                secondaryAlertDate = item.secondaryAlertDate ?: heuristic.alertDate,
                aiClassification = heuristic.inferredClassification,
                aiConfidence = maxOf(item.aiConfidence, heuristic.confidence.coerceIn(0f, 1f)),
                dominantColors = colors,
                detectedObjects = objects,
                extractionState = ProcessingState.COMPLETE,
                needsReview = heuristic.confidence < REVIEW_CONFIDENCE,
                updatedAt = System.currentTimeMillis()
            ),
            ocrText = ocrText,
            objects = objects,
            scoredLabels = scoredLabels,
            barcodes = barcodes,
            previewBitmap = previewBitmap
        )
    }

    /**
     * Optional LLM enrichment on top of the deterministic heuristic item. This runs inside a
     * background worker, where foreground-gated Gemini Nano is unavailable — the Gemma Tier 2
     * client has no foreground gate. Failures or timeouts leave the heuristic item untouched.
     */
    private suspend fun enrichWithLlm(
        item: VaultItem,
        ocrText: String,
        objects: List<String>,
        scoredLabels: List<com.vaultbrain.core.common.model.ScoredLabel>,
        barcodes: List<String>,
        previewBitmap: Bitmap? = null
    ): VaultItem = try {
        val llmImage = previewBitmap
            ?.takeIf { captureEnrichmentGenerator.supportsImageInput() }
            ?.let { bitmap ->
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                com.vaultbrain.core.ai.llm.LlmImageInput.fromEncodedBytes(stream.toByteArray())
            }
        withTimeoutOrNull(LLM_ENRICHMENT_TIMEOUT_MS) {
            captureEnrichmentGenerator.generate(
                evidence = CapturePromptEvidence(
                    ocrText = ocrText,
                    labels = objects,
                    scoredLabels = scoredLabels,
                    barcodes = barcodes,
                    classificationHint = item.aiClassification,
                    systemFacetHints = item.lensTags,
                    existingMetadata = item.parsedMetadata
                ),
                image = llmImage
            )?.applyTo(item, replaceHeuristicContent = true)
        } ?: item
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        item
    }

    private suspend fun recognize(bitmap: Bitmap): String = runCatching {
        latinRecognizer.recognizeBestDocument(bitmap).trim()
    }.getOrDefault("")

    private fun decodeBitmap(uri: Uri): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            if (info.size.width > MAX_IMAGE_DIM || info.size.height > MAX_IMAGE_DIM) {
                val scale = MAX_IMAGE_DIM.toFloat() / maxOf(info.size.width, info.size.height)
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1)
                )
            }
        }
    }.getOrNull()

    private fun renderPdfPages(uri: Uri, limit: Int): List<Bitmap> = runCatching {
        val file = File(requireNotNull(uri.path))
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                (0 until minOf(renderer.pageCount, limit)).map { index ->
                    renderer.openPage(index).use { page ->
                        val scale = (MAX_PDF_WIDTH.toFloat() / page.width).coerceAtMost(2f)
                        createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        ).also { bitmap ->
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
    }.getOrDefault(emptyList())

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener(continuation::resume)
            addOnFailureListener(continuation::resumeWithException)
        }

    private companion object {
        const val DEFAULT_CAPTURE_TITLE = "Captured item"
        const val MAX_TITLE_CHARS = 120
        const val MAX_SUMMARY_CHARS = 500
        const val MAX_PDF_PAGES = 5
        const val MAX_PDF_WIDTH = 1_600
        const val MAX_IMAGE_DIM = 1_200
        const val REVIEW_CONFIDENCE = 0.5f
        const val LLM_ENRICHMENT_TIMEOUT_MS = 30_000L
        const val INTERNAL_VISION_SCORES_KEY = "_vision_label_scores"
    }
}

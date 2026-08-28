package com.vaultbrain.feature.capture

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.vaultbrain.core.ai.heuristics.HeuristicExtractor
import com.vaultbrain.core.ai.heuristics.HeuristicExtractionResult
import com.vaultbrain.core.ai.heuristics.experience.ExperienceKeywordLibrary
import com.vaultbrain.core.ai.llm.CapturePromptEvidence
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.ai.llm.gemma.GemmaModelManager
import com.vaultbrain.core.ai.llm.gemma.GemmaModelStatus
import com.vaultbrain.core.ai.vision.VisionAnalyzer
import com.vaultbrain.core.common.UserExperienceFrequency
import com.vaultbrain.core.ai.heuristics.experience.ExperienceParserRegistry
import com.vaultbrain.core.common.model.ExperienceId
import com.vaultbrain.core.common.model.LensId
import com.vaultbrain.core.common.model.SourceType
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.notifications.UnifiedAlertManager
import com.vaultbrain.feature.capture.ui.components.KeywordDisplayItem
import com.vaultbrain.feature.capture.worker.DeferredAnalysisWorker
import com.vaultbrain.feature.capture.worker.LlmEnrichmentWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ViewModel that orchestrates capture, OCR, heuristic / vision analysis and persistence.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val heuristicExtractor: HeuristicExtractor,
    private val experienceRegistry: ExperienceParserRegistry,
    private val visionAnalyzer: VisionAnalyzer,
    private val alertManager: UnifiedAlertManager,
    private val mediaVaultStorage: MediaVaultStorage,
    private val latinRecognizer: TextRecognizer,
    private val experienceFrequency: UserExperienceFrequency,
    private val captureEnrichmentGenerator: CaptureEnrichmentGenerator,
    private val llmClient: LlmClient,
    private val gemmaModelManager: GemmaModelManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** Live model status so the review hint hides the moment a download starts. */
    val gemmaStatus: StateFlow<GemmaModelStatus> = gemmaModelManager.status

    /** Set per analyze() run: no LLM tier could enrich the current capture. */
    private var llmUnavailableAtAnalysis: Boolean = false

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Camera)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val batchDrafts = mutableListOf<VaultItem>()
    private val batchTargetCollectionIds = mutableListOf<String?>()
    private var batchIndex = 0

    /**
     * Start processing a shared or captured input. Call this when the screen receives
     * explicit media/text (e.g. from [ShareActivity]) or when the camera produced a file.
     */
    fun process(input: CaptureInput) {
        viewModelScope.launch {
            _uiState.value = CaptureUiState.Processing()
            llmUnavailableAtAnalysis = !llmClient.isAvailable()
            try {
                val (items, targetIds) = withContext(Dispatchers.IO) {
                    val inputs = if (input.initialUris.isNotEmpty()) {
                        input.initialUris.mapIndexed { index, uri ->
                            val ownedUri = mediaVaultStorage.import(uri)
                            input.copy(
                                initialUris = listOf(ownedUri),
                                initialText = input.initialText.takeIf { index == 0 }
                            )
                        }
                    } else {
                        listOf(input)
                    }
                    inputs.map { analyze(it) } to inputs.map { it.targetCollectionId }
                }
                check(items.isNotEmpty()) { "No supported input was selected" }
                batchDrafts.clear()
                batchTargetCollectionIds.clear()
                batchDrafts.addAll(items)
                batchTargetCollectionIds.addAll(targetIds)
                batchIndex = 0

                // Compute scored suggestions using quickScore with frequency bonus
                val currentDraft = batchDrafts[batchIndex]
                val ocrText = currentDraft.rawOcrText.orEmpty()
                val frequencies = experienceFrequency.allFrequencies()
                val scored = withContext(Dispatchers.Default) {
                    ExperienceKeywordLibrary.quickScore(
                        ocrText = ocrText,
                        frequencyBonus = frequencies,
                        topN = 5
                    )
                }

                // If top suggestion has very high confidence (≥ 6 groups matched), auto-select it
                val topScore = scored.firstOrNull()
                _uiState.value = CaptureUiState.PickExperience(
                    drafts = batchDrafts.toList(),
                    suggestions = scored.map { it.experienceId },
                    currentIndex = 0,
                    scoredSuggestions = scored
                )
                // Keep experience routing internal in the primary capture flow. Strong matches
                // may activate a specialized parser; otherwise continue without asking the user
                // to choose a system category.
                if (topScore != null && topScore.score >= AUTO_SELECT_THRESHOLD) {
                    selectExperience(topScore.experienceId)
                } else {
                    skipExperience()
                }
            } catch (e: Exception) {
                _uiState.value = CaptureUiState.Error(e.message ?: context.getString(R.string.feature_capture_processing))
            }
        }
    }

    /**
     * Update the editable draft while the user is on the review screen.
     */
    fun updateDraft(draft: VaultItem) {
        val current = _uiState.value as? CaptureUiState.Review ?: return
        if (batchIndex in batchDrafts.indices) batchDrafts[batchIndex] = draft
        _uiState.value = current.copy(draft = draft)
    }

    /**
     * Update a specific metadata field and track that it was user-edited.
     */
    fun updateMetadata(key: String, value: String) {
        val current = _uiState.value as? CaptureUiState.Review ?: return
        val updatedMeta = current.draft.parsedMetadata.toMutableMap()
        updatedMeta[key] = value
        val updatedDraft = current.draft.copy(parsedMetadata = updatedMeta)
        if (batchIndex in batchDrafts.indices) batchDrafts[batchIndex] = updatedDraft
        _uiState.value = current.copy(
            draft = updatedDraft,
            editedMetadataKeys = current.editedMetadataKeys + key
        )
    }

    /**
     * Apply an experience-specific parser to the current draft and continue to review.
     *
     * If the user picks the generic fallback or an unknown id, no parser is run and the
     * item proceeds to review as a plain vault item.
     */
    fun selectExperience(experienceId: String) {
        val picker = _uiState.value as? CaptureUiState.PickExperience ?: return
        val currentIndex = picker.currentIndex
        if (currentIndex !in batchDrafts.indices) return

        val experience = ExperienceDefinitions.experienceById(experienceId)
        if (experienceId == ExperienceId.GENERIC || experience == null) {
            // "Just save": no experience, no primary lens — the item stays General.
            batchDrafts[currentIndex] = batchDrafts[currentIndex].copy(
                primaryLensId = null,
                updatedAt = System.currentTimeMillis()
            )
            batchIndex = currentIndex
            showCurrentDraft()
            return
        }

        val draft = batchDrafts[currentIndex]
        val parsed = experienceRegistry.parserFor(experienceId)?.extract(draft) ?: emptyMap()
        val lensId = ExperienceDefinitions.effectiveLensId(experienceId)
        val primaryExp = ExperienceDefinitions.resolvePrimary(experienceId)
        val newLensTags = buildSet {
            addAll(draft.lensTags)
            if (lensId != null) add(lensId)
            if (primaryExp != null) add(primaryExp.id)
            add(experience.id)
        }
        val updatedDraft = draft.copy(
            experienceId = experienceId,
            parsedMetadata = parsed + draft.parsedMetadata,
            lensTags = newLensTags,
            primaryLensId = lensId ?: draft.primaryLensId,
            updatedAt = System.currentTimeMillis()
        )
        batchDrafts[currentIndex] = updatedDraft
        batchIndex = currentIndex
        showCurrentDraft()
    }

    /**
     * Skip the experience picker and save the item without a specific experience.
     */
    fun skipExperience() = selectExperience(ExperienceId.GENERIC)

    fun downloadGemmaModel() {
        gemmaModelManager.downloadModel()
    }

    /**
     * Discard the current draft and return to the camera. Any imported media is left behind;
     * the user can clean it up from the vault later if desired.
     */
    fun discard() {
        batchDrafts.clear()
        batchTargetCollectionIds.clear()
        batchIndex = 0
        _uiState.value = CaptureUiState.Camera
    }

    /**
     * Persist the reviewed item immediately. After save, show post-save suggestions
     * before returning. Optional AI and indexing continue without keeping the user
     * on the save screen.
     */
    fun save(onComplete: () -> Unit) {
        val review = _uiState.value as? CaptureUiState.Review ?: return
        val item = review.draft.copy(
            needsReview = false,
            userEditedAt = System.currentTimeMillis()
        )
        if (batchIndex in batchDrafts.indices) batchDrafts[batchIndex] = item

        viewModelScope.launch {
            _uiState.value = CaptureUiState.Saving
            try {
                withContext(Dispatchers.IO) {
                    repository.save(item)
                    batchTargetCollectionIds.getOrNull(batchIndex)?.let { collectionId ->
                        repository.addItemToCollection(item.id, collectionId)
                    }
                }
                // Drain indexing/deferred analysis for the saved item in the background.
                DeferredAnalysisWorker.enqueue(context)
                if (gemmaModelManager.status.value == GemmaModelStatus.READY) {
                    LlmEnrichmentWorker.enqueue(context)
                }
                // Record experience frequency for learning
                item.experienceId?.let { experienceFrequency.recordSave(it) }

                viewModelScope.launch(Dispatchers.IO) {
                    alertManager.scheduleAlerts(item)
                }

                if (batchIndex < batchDrafts.lastIndex) {
                    batchIndex++
                    val nextDraft = batchDrafts[batchIndex]
                    val ocrText = nextDraft.rawOcrText.orEmpty()
                    val frequencies = experienceFrequency.allFrequencies()
                    val scored = withContext(Dispatchers.Default) {
                        ExperienceKeywordLibrary.quickScore(ocrText, frequencies, 5)
                    }

                    _uiState.value = CaptureUiState.PickExperience(
                        drafts = batchDrafts.toList(),
                        suggestions = scored.map { it.experienceId },
                        currentIndex = batchIndex,
                        scoredSuggestions = scored
                    )
                } else {
                    // Show post-save suggestions
                    val followUps = PostSaveSuggestionEngine.suggestFollowUps(item.experienceId)
                    if (followUps.isNotEmpty()) {
                        _uiState.value = CaptureUiState.Saved(
                            savedExperienceId = item.experienceId,
                            followUps = followUps
                        )
                    } else {
                        batchDrafts.clear()
        batchTargetCollectionIds.clear()
                        onComplete()
                    }
                }
            } catch (e: Exception) {
                _uiState.value = CaptureUiState.Error(e.message ?: "Save failed")
            }
        }
    }

    /** Dismiss post-save suggestions and complete the flow. */
    fun dismissFollowUp(onComplete: () -> Unit) {
        batchDrafts.clear()
        batchTargetCollectionIds.clear()
        batchIndex = 0
        onComplete()
    }

    /** Start a new capture for a follow-up suggestion. */
    fun startFollowUpCapture(experienceId: String) {
        batchDrafts.clear()
        batchTargetCollectionIds.clear()
        batchIndex = 0
        _uiState.value = CaptureUiState.Camera
    }

    private suspend fun analyze(input: CaptureInput): VaultItem {
        val primaryUri = input.initialUris.firstOrNull()

        // PDF pages are rendered once and shared between OCR and the vision preview (page 1).
        // Plain images are decoded once at the 1200px cap and reused the same way.
        val pdfPages = primaryUri?.takeIf(::isPdf)?.let { renderPdfPages(it, MAX_PDF_OCR_PAGES) }.orEmpty()
        val imageBitmap = if (primaryUri != null && pdfPages.isEmpty()) {
            decodeBitmapFromUri(primaryUri)
        } else {
            null
        }
        val previewBitmap = pdfPages.firstOrNull() ?: imageBitmap

        try {
            val ocrText = buildString {
                input.initialText?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
                primaryUri?.let { appendLine(recognizeText(it, pdfPages, imageBitmap)) }
            }.trim()

            // Emit live keywords as OCR completes
            if (ocrText.isNotBlank()) {
                val topScored = ExperienceKeywordLibrary.quickScore(ocrText, topN = 1)
                val topExperienceId = topScored.firstOrNull()?.experienceId
                val detectedKws = if (topExperienceId != null) {
                    ExperienceKeywordLibrary.matchedKeywords(topExperienceId, ocrText)
                        .take(8)
                        .map { KeywordDisplayItem(it.keyword, it.category) }
                } else {
                    emptyList()
                }
                _uiState.value = CaptureUiState.Processing(detectedKeywords = detectedKws)
            }

            // Run vision analysis (colors, labels, barcodes, and neural document classifier).
            data class VisionBundle(
                val colors: List<String>,
                val objects: List<String>,
                val barcodes: List<String>,
                val neuralClassification: com.vaultbrain.core.common.model.Classification?,
                val neuralConfidence: Float
            )

            val visionBundle = if (previewBitmap != null) {
                val result = visionAnalyzer.analyze(previewBitmap)
                VisionBundle(
                    colors = result.dominantColors,
                    objects = result.detectedObjects,
                    barcodes = result.barcodes,
                    neuralClassification = result.documentClassification,
                    neuralConfidence = result.documentClassificationConfidence
                )
            } else {
                VisionBundle(emptyList(), emptyList(), emptyList(), null, 0f)
            }

            val visionColors = visionBundle.colors
            val visionObjects = visionBundle.objects
            val barcodes = visionBundle.barcodes

            var heuristic = heuristicExtractor.extract(
                text = buildString {
                    append(ocrText)
                    if (barcodes.isNotEmpty()) appendLine().append(barcodes.joinToString("\n"))
                },
                visionObjects = visionObjects,
                neuralClassification = visionBundle.neuralClassification,
                neuralConfidence = visionBundle.neuralConfidence
            )

            val preferredTags = input.preferredLensTags.mapNotNull(LensId::canonicalOrNull).toSet()
            if (preferredTags.isNotEmpty()) {
                heuristic = heuristic.copy(lensTags = heuristic.lensTags + preferredTags)
            }

            if (primaryUri != null && OcrImagePreprocessor.qualityScore(ocrText) < MIN_RELIABLE_OCR_SCORE) {
                heuristic = heuristic.copy(confidence = minOf(heuristic.confidence, 0.35f))
            }

            val item = buildVaultItem(
                input.sourceType,
                primaryUri,
                ocrText,
                heuristic,
                visionColors,
                visionObjects,
                barcodes,
                primaryUri?.let(mediaVaultStorage::mimeType)
            ).let {
                if (input.provenanceMetadata.isEmpty()) it
                else it.copy(parsedMetadata = it.parsedMetadata + input.provenanceMetadata)
            }
            return if (input.skipLlmEnrichment) item else enrichWithLlm(
                item,
                ocrText,
                visionObjects,
                barcodes,
                previewBitmap
            )
        } finally {
            pdfPages.forEach(Bitmap::recycle)
            imageBitmap?.recycle()
        }
    }

    /**
     * Optional LLM enrichment on top of the deterministic heuristic item. Never blocks capture:
     * any failure or timeout returns the heuristic item untouched.
     */
    private suspend fun enrichWithLlm(
        item: VaultItem,
        ocrText: String,
        visionObjects: List<String>,
        barcodes: List<String>,
        imageBitmap: Bitmap?
    ): VaultItem = try {
        val llmImage = imageBitmap
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
                    labels = visionObjects,
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

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val maxDim = 1200
                    if (info.size.width > maxDim || info.size.height > maxDim) {
                        val scale = maxDim.toFloat() / maxOf(info.size.width, info.size.height)
                        val targetWidth = (info.size.width * scale).toInt().coerceAtLeast(1)
                        val targetHeight = (info.size.height * scale).toInt().coerceAtLeast(1)
                        decoder.setTargetSize(targetWidth, targetHeight)
                    }
                }
            } else {
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use { 
                    android.graphics.BitmapFactory.decodeStream(it, null, options) 
                }
                var sampleSize = 1
                val maxDim = 1200
                while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }
                val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(uri)?.use { 
                    android.graphics.BitmapFactory.decodeStream(it, null, decodeOptions) 
                }
            }
        }.getOrNull()
    }

    private suspend fun recognizeText(
        uri: Uri,
        pdfPages: List<Bitmap>,
        imageBitmap: Bitmap?
    ): String {
        // Bitmaps are owned (and recycled) by the caller in analyze().
        val pageResults = when {
            pdfPages.isNotEmpty() -> pdfPages.map { bitmap ->
                recognizeBestCandidate(bitmap)
            }
            imageBitmap != null -> {
                listOf(recognizeBestCandidate(imageBitmap))
            }
            else -> {
                // Fallback if the bitmap failed to decode for some reason
                val original = runCatching {
                    latinRecognizer.process(InputImage.fromFilePath(context, uri)).await().text
                }.getOrNull().orEmpty()
                listOf(original)
            }
        }
        return pageResults.mapIndexed { index, latin ->
            buildString {
                if (pageResults.size > 1) appendLine("Page ${index + 1}")
                append(latin.trim())
            }
        }.filter { it.isNotBlank() }.joinToString("\n\n").trim()
    }

    private suspend fun recognizeBestCandidate(bitmap: Bitmap): String {
        return runCatching { latinRecognizer.recognizeBestDocument(bitmap) }.getOrDefault("")
    }



    private fun isPdf(uri: Uri): Boolean =
        mediaVaultStorage.mimeType(uri) == "application/pdf" ||
            uri.lastPathSegment?.endsWith(".pdf", ignoreCase = true) == true

    private fun renderPdfPages(uri: Uri, limit: Int): List<Bitmap> = runCatching {
        val file = java.io.File(requireNotNull(uri.path))
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                (0 until minOf(renderer.pageCount, limit)).map { pageIndex ->
                    renderer.openPage(pageIndex).use { page ->
                        val scale = (MAX_PDF_RENDER_WIDTH.toFloat() / page.width).coerceAtMost(2f)
                        val bitmap = createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }
    }.getOrDefault(emptyList())

    internal fun buildVaultItem(
        sourceType: SourceType,
        primaryUri: Uri?,
        ocrText: String,
        heuristic: HeuristicExtractionResult,
        visionColors: List<String>,
        visionObjects: List<String>,
        barcodes: List<String> = emptyList(),
        mediaMimeType: String? = null
    ): VaultItem {
        val title = heuristic.title?.takeIf { it.isNotBlank() }
            ?: ocrText.takeIf { it.isNotBlank() }?.lineSequence()?.firstOrNull()
            ?: "Captured item"

        return VaultItem(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            summary = heuristic.summary ?: ocrText.take(300),
            rawOcrText = ocrText.takeIf { it.isNotBlank() },
            sourceType = sourceType,
            capturedImageUri = primaryUri?.toString(),

            parsedMetadata = buildMap {
                putAll(heuristic.metadata)
                mediaMimeType?.let { put("media_mime_type", it) }
                if (barcodes.isNotEmpty()) put("barcodes", barcodes.joinToString(" | "))
            },
            lensTags = heuristic.lensTags,
            expiryDate = heuristic.expiryDate,
            secondaryAlertDate = heuristic.alertDate,
            aiConfidence = heuristic.confidence.coerceIn(0f, 1f),
            aiClassification = heuristic.inferredClassification,
            extractionState = com.vaultbrain.core.common.model.ProcessingState.COMPLETE,
            indexingState = com.vaultbrain.core.common.model.ProcessingState.PENDING,
            dominantColors = visionColors,
            detectedObjects = visionObjects,
            needsReview = heuristic.confidence < 0.5f
        )
    }

    private fun buildLensSuggestions(item: VaultItem): List<String> {
        val base = LensId.ALL_LENSES
        val ordered = item.lensTags.toList() + base.filter { it !in item.lensTags }
        return ordered.distinct()
    }

    private fun showCurrentDraft() {
        val item = batchDrafts[batchIndex]
        _uiState.value = CaptureUiState.Review(
            draft = item,
            confidence = item.aiConfidence,
            suggestedLensTags = buildLensSuggestions(item),
            batchPosition = batchIndex + 1,
            batchTotal = batchDrafts.size,
            showOnDeviceAiHint = llmUnavailableAtAnalysis &&
                gemmaModelManager.isSupported() &&
                gemmaModelManager.status.value == GemmaModelStatus.NOT_DOWNLOADED
        )
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it) }
            addOnFailureListener { cont.resumeWithException(it) }
        }

    private companion object {
        const val MAX_PDF_OCR_PAGES = 5
        const val MAX_PDF_RENDER_WIDTH = 1_600
        const val OCR_RETRY_SCORE = 240
        const val MIN_RELIABLE_OCR_SCORE = 20
        const val LLM_ENRICHMENT_TIMEOUT_MS = 30_000L
        /** Score threshold above which the top experience is auto-selected to skip the picker. */
        const val AUTO_SELECT_THRESHOLD = 8f
    }
}

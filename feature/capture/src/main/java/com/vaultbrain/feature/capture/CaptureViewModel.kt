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
import com.vaultbrain.core.ai.llm.ArabicDocumentTranscriber
import com.vaultbrain.core.ai.llm.ArabicScriptCoverage
import com.vaultbrain.core.ai.llm.CapturePromptEvidence
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.ai.llm.LlmImageInput
import com.vaultbrain.core.ai.llm.gemma.GemmaModelManager
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelStatus
import com.vaultbrain.core.ai.vision.VisionAnalyzer
import com.vaultbrain.core.common.UserExperienceFrequency
import com.vaultbrain.core.ai.heuristics.experience.ExperienceParserRegistry
import com.vaultbrain.shared.model.ExperienceId
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.notifications.UnifiedAlertManager
import com.vaultbrain.feature.capture.ui.components.KeywordDisplayItem
import com.vaultbrain.feature.capture.worker.DeferredAnalysisWorker
import com.vaultbrain.feature.capture.worker.LlmEnrichmentWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val gemmaStatus: StateFlow<OnDeviceModelStatus> = gemmaModelManager.status

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
        if (batchIndex !in batchDrafts.indices) return
        if (_uiState.value is CaptureUiState.Saving || batchDrafts[batchIndex].title.isBlank()) return
        val item = batchDrafts[batchIndex].copy(
            needsReview = false,
            userEditedAt = System.currentTimeMillis()
        )
        batchDrafts[batchIndex] = item
        _uiState.value = CaptureUiState.Saving

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
                if (gemmaModelManager.status.value == OnDeviceModelStatus.READY) {
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
                    val expiry = item.expiryDate
                    val needsReminderNudge = item.effectiveClassification in setOf(com.vaultbrain.shared.model.Classification.TICKET, com.vaultbrain.shared.model.Classification.HOTEL) 
                        && expiry != null && expiry > System.currentTimeMillis()

                    if (followUps.isNotEmpty() || needsReminderNudge) {
                        _uiState.value = CaptureUiState.Saved(
                            savedExperienceId = item.experienceId,
                            followUps = followUps,
                            itemNeedsReminder = needsReminderNudge,
                            savedItemId = item.id
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
        val startMs = System.currentTimeMillis()
        val primaryUri = input.initialUris.firstOrNull()

        // PDF pages are rendered once and shared between OCR and the vision preview (page 1).
        // Preserve small document text within a bounded 2400px decode.
        val pdfPages = primaryUri?.takeIf(::isPdf)?.let { renderPdfPages(it, MAX_PDF_OCR_PAGES) }.orEmpty()
        val imageBitmap = if (primaryUri != null && pdfPages.isEmpty()) {
            decodeBitmapFromUri(primaryUri)
        } else {
            null
        }
        val previewBitmap = pdfPages.firstOrNull() ?: imageBitmap
        val setupMs = System.currentTimeMillis()
        android.util.Log.d("CaptureLatency", "Setup took ${setupMs - startMs}ms")

        return try {
            coroutineScope {
            // Vision analysis (colors, labels, barcodes, and neural document classifier) starts
            // immediately and overlaps with OCR; both only read the shared bitmaps, which are
            // recycled in the finally below after every consumer has completed.
            data class VisionBundle(
                val colors: List<String>,
                val objects: List<String>,
                val scoredLabels: List<com.vaultbrain.shared.model.ScoredLabel>,
                val barcodes: List<String>,
                val neuralClassification: com.vaultbrain.shared.model.Classification?,
                val neuralConfidence: Float
            )

            val visionDeferred = async {
                if (previewBitmap != null) {
                    val result = visionAnalyzer.analyze(previewBitmap)
                    VisionBundle(
                        colors = result.dominantColors,
                        objects = result.detectedObjects,
                        scoredLabels = result.scoredLabels,
                        barcodes = result.barcodes,
                        neuralClassification = result.documentClassification,
                        neuralConfidence = result.documentClassificationConfidence
                    )
                } else {
                    VisionBundle(emptyList(), emptyList(), emptyList(), emptyList(), null, 0f)
                }
            }

            val ocrText = buildString {
                input.initialText?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
                primaryUri?.let { appendLine(recognizeText(it, pdfPages, imageBitmap)) }
            }.trim()
            val ocrMs = System.currentTimeMillis()
            android.util.Log.d("CaptureLatency", "OCR took ${ocrMs - setupMs}ms")

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

            val visionBundle = visionDeferred.await()
            val visionMs = System.currentTimeMillis()
            android.util.Log.d("CaptureLatency", "Vision finished ${visionMs - setupMs}ms after setup (overlapped with OCR)")

            val visionColors = visionBundle.colors
            val visionObjects = visionBundle.objects
            val barcodes = visionBundle.barcodes

            val primaryText = buildString {
                append(ocrText)
                if (barcodes.isNotEmpty()) appendLine().append(barcodes.joinToString("\n"))
            }

            llmUnavailableAtAnalysis = !llmClient.isAvailable()
            var heuristic = if (!llmUnavailableAtAnalysis && !input.skipLlmEnrichment) {
                HeuristicExtractionResult()
            } else {
                heuristicExtractor.extract(
                    text = primaryText,
                    visionObjects = visionObjects,
                    neuralClassification = visionBundle.neuralClassification,
                    neuralConfidence = visionBundle.neuralConfidence,
                    scoredLabels = visionBundle.scoredLabels
                )
            }
            val heuristicMs = System.currentTimeMillis()
            android.util.Log.d("CaptureLatency", "Heuristics took ${heuristicMs - visionMs}ms")

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
            
            val llmStart = System.currentTimeMillis()
            val result = if (input.skipLlmEnrichment) item.copy(
                enrichmentState = com.vaultbrain.shared.model.EnrichmentState.SKIPPED_PRIVACY
            ) else {
                val enriched = enrichWithLlm(
                    item,
                    ocrText,
                    visionObjects,
                    visionBundle.scoredLabels,
                    barcodes,
                    previewBitmap
                )
                
                // Fallback: If LLM is entirely unavailable, the heuristic extraction was already
                // run earlier and applied to `item`.
                val settled = if (llmUnavailableAtAnalysis && enriched === item && enriched.aiClassification == com.vaultbrain.shared.model.Classification.UNKNOWN) {
                    android.util.Log.w("CaptureLatency", "LLM unavailable; relying on deterministic heuristics")
                    val fallbackHeuristic = heuristicExtractor.extract(
                        text = primaryText,
                        visionObjects = visionObjects,
                        neuralClassification = visionBundle.neuralClassification,
                        neuralConfidence = visionBundle.neuralConfidence,
                        scoredLabels = visionBundle.scoredLabels
                    )

                    var fallbackItem = buildVaultItem(
                        input.sourceType,
                        primaryUri,
                        ocrText,
                        fallbackHeuristic,
                        visionColors,
                        visionObjects,
                        barcodes,
                        primaryUri?.let(mediaVaultStorage::mimeType)
                    )

                    if (input.provenanceMetadata.isNotEmpty()) {
                        fallbackItem = fallbackItem.copy(parsedMetadata = fallbackItem.parsedMetadata + input.provenanceMetadata)
                    }
                    fallbackItem
                } else {
                    enriched
                }
                // Experimental Arabic VL path; any failure keeps `settled` untouched.
                maybeTranscribeArabicDocument(settled, ocrText, previewBitmap) ?: settled
            }
            val endMs = System.currentTimeMillis()
            android.util.Log.d("CaptureLatency", "LLM took ${endMs - llmStart}ms")
            android.util.Log.d("CaptureLatency", "Total capture latency: ${endMs - startMs}ms")
            result.copy(
                lensTags = result.lensTags + preferredTags,
                parsedMetadata = result.parsedMetadata + input.provenanceMetadata
            )
            }
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
        scoredLabels: List<com.vaultbrain.shared.model.ScoredLabel>,
        barcodes: List<String>,
        imageBitmap: Bitmap?
    ): VaultItem = try {
        val llmImage = imageBitmap
            ?.takeIf { captureEnrichmentGenerator.supportsImageInput() }
            ?.let(::encodeForLlm)

        withTimeoutOrNull(LLM_ENRICHMENT_TIMEOUT_MS) {
            captureEnrichmentGenerator.generate(
                evidence = CapturePromptEvidence(
                    ocrText = ocrText,
                    labels = visionObjects,
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

    /**
     * Experimental Arabic VL path (BuildConfig.ARABIC_VL_EXPERIMENT, default off): when the
     * Latin-only OCR captured too little meaningful text, the image-capable tier transcribes
     * the page and verified facts merge into the draft. Any failure returns null and leaves
     * the deterministic item untouched. Never called when enrichment is skipped for privacy.
     */
    private suspend fun maybeTranscribeArabicDocument(
        item: VaultItem,
        ocrText: String,
        imageBitmap: Bitmap?
    ): VaultItem? {
        if (!com.vaultbrain.core.ai.llm.BuildConfig.ARABIC_VL_EXPERIMENT) return null
        val bitmap = imageBitmap ?: return null
        if (!ArabicScriptCoverage.needsVlTranscription(ocrText)) return null
        return try {
            val transcriber = ArabicDocumentTranscriber(llmClient)
            if (!transcriber.isAvailable()) return null
            val llmImage = encodeForLlm(bitmap) ?: return null
            withTimeoutOrNull(LLM_ENRICHMENT_TIMEOUT_MS) {
                transcriber.transcribe(llmImage)?.applyTo(item)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeForLlm(bitmap: Bitmap): LlmImageInput? {
        val maxLlmDim = 768f
        val scale = (maxLlmDim / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
        val targetBitmap = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } else bitmap

        val stream = java.io.ByteArrayOutputStream()
        targetBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)
        if (targetBitmap !== bitmap) targetBitmap.recycle()
        return LlmImageInput.fromEncodedBytes(stream.toByteArray())
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val maxDim = 2400
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
                val maxDim = 2400
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
                    latinRecognizer.process(InputImage.fromFilePath(context, uri)).await().spatiallyOrderedText()
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
            extractionState = com.vaultbrain.shared.model.ProcessingState.COMPLETE,
            indexingState = com.vaultbrain.shared.model.ProcessingState.PENDING,
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
                gemmaModelManager.status.value == OnDeviceModelStatus.NOT_DOWNLOADED
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
        const val LLM_ENRICHMENT_TIMEOUT_MS = 120_000L
        /** Score threshold above which the top experience is auto-selected to skip the picker. */
        const val AUTO_SELECT_THRESHOLD = 8f
    }
}

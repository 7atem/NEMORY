package com.vaultbrain.feature.capture

import android.content.Context
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.google.mlkit.vision.text.TextRecognizer
import com.vaultbrain.core.ai.heuristics.HeuristicExtractionResult
import com.vaultbrain.core.ai.heuristics.HeuristicExtractor
import com.vaultbrain.core.ai.heuristics.experience.ExperienceParserRegistry
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.ai.llm.gemma.GemmaModelManager
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelStatus
import com.vaultbrain.core.ai.vision.VisionAnalyzer
import com.vaultbrain.core.common.UserExperienceFrequency
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.SourceType
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.notifications.UnifiedAlertManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class CaptureViewModelTest {

    private val repository = mockk<VaultRepository>()
    private val heuristicExtractor = mockk<HeuristicExtractor>()
    private val experienceRegistry = mockk<ExperienceParserRegistry>()
    private val visionAnalyzer = mockk<VisionAnalyzer>()
    private val alertManager = mockk<UnifiedAlertManager>()
    private val mediaVaultStorage = mockk<MediaVaultStorage>()
    private val latinRecognizer = mockk<TextRecognizer>()
    private val experienceFrequency = mockk<UserExperienceFrequency>()
    private val captureEnrichmentGenerator = mockk<CaptureEnrichmentGenerator>()
    private val llmClient = mockk<LlmClient>()
    private val gemmaModelManager = mockk<GemmaModelManager> {
        every { status } returns MutableStateFlow(OnDeviceModelStatus.NOT_DOWNLOADED)
    }
    private val context = mockk<Context>()

    private val viewModel = CaptureViewModel(
        repository = repository,
        heuristicExtractor = heuristicExtractor,
        experienceRegistry = experienceRegistry,
        visionAnalyzer = visionAnalyzer,
        alertManager = alertManager,
        mediaVaultStorage = mediaVaultStorage,
        latinRecognizer = latinRecognizer,
        experienceFrequency = experienceFrequency,
        captureEnrichmentGenerator = captureEnrichmentGenerator,
        llmClient = llmClient,
        gemmaModelManager = gemmaModelManager,
        context = context
    )

    @Test
    fun `buildVaultItem uses heuristic title and metadata`() {
        val imageUri = mockk<Uri>()
        every { imageUri.toString() } returns "content://image/1"
        val heuristic = HeuristicExtractionResult(
            dates = listOf("15/01/2025"),
            amounts = listOf(4850.0),
            currencies = listOf("EGP"),
            inferredClassification = Classification.RECEIPT,
            confidence = 0.8f,
            title = "Carrefour Receipt",
            summary = "Total: EGP 4850",
            metadata = mapOf("total" to "4850.0", "currency" to "EGP"),
            lensTags = setOf("MONEY")
        )

        val item = viewModel.buildVaultItem(
            sourceType = SourceType.CAMERA,
            primaryUri = imageUri,
            ocrText = "Carrefour Hypermarket Total: 4850",
            heuristic = heuristic,
            visionColors = listOf("#FFFFFF"),
            visionObjects = listOf("receipt")
        )

        assertThat(item.title).isEqualTo("Carrefour Receipt")
        assertThat(item.summary).isEqualTo("Total: EGP 4850")
        assertThat(item.parsedMetadata["total"]).isEqualTo("4850.0")
        assertThat(item.lensTags).contains("MONEY")
        assertThat(item.aiClassification).isEqualTo(Classification.RECEIPT)
        assertThat(item.aiConfidence).isEqualTo(0.8f)
        assertThat(item.needsReview).isFalse()
        assertThat(item.dominantColors).contains("#FFFFFF")
    }

    @Test
    fun `buildVaultItem falls back to OCR first line when no title`() {
        val heuristic = HeuristicExtractionResult(
            inferredClassification = Classification.UNKNOWN,
            confidence = 0.3f,
            title = null
        )

        val item = viewModel.buildVaultItem(
            sourceType = SourceType.TEXT_PASTE,
            primaryUri = null,
            ocrText = "Meeting notes\nProject deadline Friday",
            heuristic = heuristic,
            visionColors = emptyList(),
            visionObjects = emptyList()
        )

        assertThat(item.title).isEqualTo("Meeting notes")
        assertThat(item.needsReview).isTrue()
    }

    @Test
    fun `buildVaultItem defaults when ocr is blank`() {
        val heuristic = HeuristicExtractionResult(
            inferredClassification = Classification.UNKNOWN,
            confidence = 0.5f,
            title = null
        )

        val item = viewModel.buildVaultItem(
            sourceType = SourceType.MANUAL,
            primaryUri = null,
            ocrText = "",
            heuristic = heuristic,
            visionColors = emptyList(),
            visionObjects = emptyList()
        )

        assertThat(item.title).isEqualTo("Captured item")
        assertThat(item.rawOcrText).isNull()
    }
}

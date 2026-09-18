package com.vaultbrain.feature.capture

import com.vaultbrain.core.ai.llm.CaptureEnrichmentPrompt
import com.vaultbrain.core.ai.llm.CaptureEnrichmentResult
import com.vaultbrain.core.ai.llm.CaptureEnrichmentResultParser
import com.vaultbrain.core.ai.llm.CapturePromptEvidence
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.ai.llm.LlmImageInput
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Builds truthful multimodal/text prompts and owns the OCR-only retry contract. */
class CaptureEnrichmentGenerator @Inject constructor(
    private val llmClient: LlmClient
) {
    fun supportsImageInput(): Boolean = llmClient.supportsImageInput()

    suspend fun warmup(): Boolean = llmClient.warmup()

    suspend fun generate(
        evidence: CapturePromptEvidence,
        image: LlmImageInput?
    ): CaptureEnrichmentResult? {
        val multimodal = image
            ?.takeIf { llmClient.supportsImageInput() }
            ?.let { generateAndParse(evidence.copy(hasImageInput = true), it) }
        val first = multimodal
            ?: generateAndParse(evidence.copy(hasImageInput = false), image = null)
            ?: return null
        return first
    }

    private suspend fun generateAndParse(
        evidence: CapturePromptEvidence,
        image: LlmImageInput?
    ): CaptureEnrichmentResult? = try {
        val prompt = CaptureEnrichmentPrompt.build(evidence)
        val raw = if (image == null) {
            llmClient.generateForTask(prompt, com.vaultbrain.core.ai.llm.ReasoningBudget.FAST)
        } else {
            llmClient.generateForTask(prompt, image, com.vaultbrain.core.ai.llm.ReasoningBudget.FAST)
        }
        CaptureEnrichmentResultParser.parse(raw)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

package com.vaultbrain.feature.capture

import com.vaultbrain.core.ai.heuristics.experience.ExperienceKeywordLibrary
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.feature.capture.ui.components.KeywordDisplayItem

/**
 * UI states for the capture feature.
 */
sealed interface CaptureUiState {
    data object Camera : CaptureUiState

    /** Processing state with live keyword updates. */
    data class Processing(
        val detectedKeywords: List<KeywordDisplayItem> = emptyList()
    ) : CaptureUiState

    data class Review(
        val draft: VaultItem,
        val confidence: Float,
        val suggestedLensTags: List<String>,
        val batchPosition: Int = 1,
        val batchTotal: Int = 1,
        val editedMetadataKeys: Set<String> = emptySet(),
        /** Show the on-device-model hint: this capture got no LLM enrichment and the device is eligible. */
        val showOnDeviceAiHint: Boolean = false
    ) : CaptureUiState

    data class PickExperience(
        val drafts: List<VaultItem>,
        val suggestions: List<String>,
        val currentIndex: Int = 0,
        /** Top scored experiences with matched keyword details from quickScore. */
        val scoredSuggestions: List<ExperienceKeywordLibrary.ScoredExperience> = emptyList()
    ) : CaptureUiState

    data object Saving : CaptureUiState

    /** Post-save state showing follow-up suggestions before returning. */
    data class Saved(
        val savedExperienceId: String?,
        val followUps: List<PostSaveSuggestionEngine.FollowUpSuggestion> = emptyList(),
        val itemNeedsReminder: Boolean = false,
        val savedItemId: String? = null
    ) : CaptureUiState

    data class Error(val message: String) : CaptureUiState
}

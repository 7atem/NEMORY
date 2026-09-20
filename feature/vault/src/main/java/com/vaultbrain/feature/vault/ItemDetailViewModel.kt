package com.vaultbrain.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.core.ai.llm.AiPrivacyMode
import com.vaultbrain.core.ai.llm.AiResponseOrigin
import com.vaultbrain.core.ai.llm.CloudAiOperation
import com.vaultbrain.core.ai.llm.CloudConsentDisclosure
import com.vaultbrain.core.ai.llm.CloudConsentStore
import com.vaultbrain.core.ai.llm.HybridAiCoordinator
import com.vaultbrain.core.ai.llm.HybridAiRequest
import com.vaultbrain.core.ai.llm.HybridAiResult
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.shared.model.PersonalCollection
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.notifications.UnifiedAlertManager
import com.vaultbrain.core.integrations.action.ActionExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the item detail screen.
 */
data class ItemDetailUiState(
    val relatedItems: List<VaultItem> = emptyList(),
    val sourceFacts: List<com.vaultbrain.shared.database.entity.DerivedFactEntity> = emptyList(),
    val item: VaultItem? = null,
    val possibleDuplicateTitle: String? = null,
    val isLoading: Boolean = true,
    val isTranslating: Boolean = false,
    val translation: String? = null,
    val translationOrigin: AiResponseOrigin? = null,
    val cloudConsent: CloudConsentDisclosure? = null,
    val translationTargetLanguage: String? = null,
    val translationMessage: TranslationMessage? = null,
    val activeCollections: List<PersonalCollection> = emptyList(),
    val itemCollections: List<PersonalCollection> = emptyList(),
    val suggestedCollections: List<com.vaultbrain.shared.model.PersonalCollectionSuggestion> = emptyList(),
    val isGeneratingAiSummary: Boolean = false,
    val aiSummary: String? = null,
    val aiSummaryOrigin: AiResponseOrigin? = null,
    val proactiveActions: List<com.vaultbrain.shared.model.ProactiveAction> = emptyList()
)

enum class TranslationMessage {
    LOCAL_MODEL_UNAVAILABLE,
    CLOUD_NOT_CONFIGURED,
    GENERATION_FAILED,
    NO_TEXT
}

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val alertManager: UnifiedAlertManager,
    private val hybridAiCoordinator: HybridAiCoordinator,
    private val cloudConsentStore: CloudConsentStore,
    private val vaultReminderManager: com.vaultbrain.core.notifications.VaultReminderManager,
    private val actionExecutor: ActionExecutor,
    private val knowledge: com.vaultbrain.core.database.repository.KnowledgeRepository? = null
) : ViewModel() {

    fun executeAction(action: com.vaultbrain.shared.model.ProactiveAction) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            actionExecutor.execute(action, item)
        }
    }

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()
    private var collectionsJob: Job? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            com.vaultbrain.core.common.security.DecoySessionState.isDecoy.collect { decoy ->
                if (decoy) {
                    loadJob?.cancel()
                    collectionsJob?.cancel()
                    _uiState.value = ItemDetailUiState(isLoading = false)
                }
            }
        }
    }

    fun loadItem(itemId: String) {
        loadJob?.cancel()
        collectionsJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = ItemDetailUiState(isLoading = true)
            val item = repository.getById(itemId)
            val duplicateTitle = item?.possibleDuplicateOfItemId
                ?.let { repository.getById(it) }
                ?.title
            _uiState.value = ItemDetailUiState(
                relatedItems = knowledge?.related(itemId).orEmpty(),
                sourceFacts = knowledge?.facts(itemId).orEmpty(),
                item = item,
                possibleDuplicateTitle = duplicateTitle,
                isLoading = false,
                proactiveActions = item?.let { com.vaultbrain.shared.model.ProactiveActionResolver.resolve(it) } ?: emptyList()
            )
            if (item != null) {
                collectionsJob = viewModelScope.launch {
                    combine(
                        repository.observeActiveCollections(),
                        repository.observeCollectionsForItem(itemId),
                        repository.observeSuggestionsForItem(itemId)
                    ) { active, memberships, suggestions -> 
                        Triple(active, memberships, suggestions)
                    }.collect { (active, memberships, suggestions) ->
                        _uiState.value = _uiState.value.copy(
                            sourceFacts = if (com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value)
                                emptyList() else _uiState.value.sourceFacts,
                            relatedItems = if (com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value)
                                emptyList() else _uiState.value.relatedItems,
                            activeCollections = active,
                            itemCollections = memberships,
                            suggestedCollections = suggestions
                        )
                    }
                }
            }
        }
    }

    fun setCollectionMembership(collectionId: String, selected: Boolean) {
        val itemId = _uiState.value.item?.id ?: return
        viewModelScope.launch {
            if (selected) {
                repository.addItemToCollection(itemId, collectionId)
            } else {
                repository.removeItemFromCollection(itemId, collectionId)
            }
        }
    }

    fun acceptSuggestion(collectionId: String) {
        val itemId = _uiState.value.item?.id ?: return
        viewModelScope.launch {
            repository.acceptSuggestion(itemId, collectionId)
        }
    }

    fun rejectSuggestion(collectionId: String) {
        val itemId = _uiState.value.item?.id ?: return
        viewModelScope.launch {
            repository.rejectSuggestion(itemId, collectionId)
        }
    }

    fun deleteItem(onDeleted: () -> Unit) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            repository.delete(item)
            onDeleted()
        }
    }

    fun toggleArchive() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            repository.setArchived(item.id, !item.isArchived)
            _uiState.value = _uiState.value.copy(item = item.copy(isArchived = !item.isArchived))
        }
    }

    fun togglePinned() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            repository.setPinned(item.id, !item.isPinned)
            _uiState.value = _uiState.value.copy(item = item.copy(isPinned = !item.isPinned))
        }
    }

    fun createReminder(title: String, dueAt: Long, itemId: String) {
        viewModelScope.launch {
            vaultReminderManager.create(
                com.vaultbrain.shared.model.VaultReminder(
                    id = java.util.UUID.nameUUIDFromBytes("$itemId:$dueAt".toByteArray()).toString(),
                    title = title,
                    dueAt = dueAt,
                    status = com.vaultbrain.shared.model.VaultReminderStatus.SCHEDULED,
                    createdAt = System.currentTimeMillis(),
                    vaultItemId = itemId
                )
            )
        }
    }

    fun updateItem(edited: VaultItem) {
        if (_uiState.value.item?.id != edited.id || edited.title.isBlank() || com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value) return
        viewModelScope.launch {
            val updated = edited.copy(
                title = edited.title.trim(),
                summary = edited.summary?.trim()?.takeIf { it.isNotEmpty() },
                userNotes = edited.userNotes?.trim()?.takeIf { it.isNotEmpty() },
                needsReview = false,
                possibleDuplicateOfItemId = null,
                duplicateSimilarity = null,
                userEditedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.save(updated)
            alertManager.scheduleAlerts(updated)
            _uiState.value = _uiState.value.copy(
                item = updated, 
                possibleDuplicateTitle = null,
                proactiveActions = com.vaultbrain.shared.model.ProactiveActionResolver.resolve(updated)
            )
        }
    }

    fun translate(
        targetLanguage: String,
        explicitCloudConsent: Boolean = false,
        rememberForCategory: Boolean = false
    ) {
        val item = _uiState.value.item ?: return
        val sharedText = translationSource(item)
        if (sharedText.isBlank()) {
            _uiState.value = _uiState.value.copy(translationMessage = TranslationMessage.NO_TEXT)
            return
        }
        if (_uiState.value.isTranslating) return
        if (explicitCloudConsent && rememberForCategory) {
            _uiState.value.cloudConsent
                ?.takeIf(CloudConsentDisclosure::canRememberForCategory)
                ?.rememberableCategories
                ?.let(cloudConsentStore::remember)
        }

        _uiState.value = _uiState.value.copy(
            isTranslating = true,
            cloudConsent = null,
            translationMessage = null,
            translationTargetLanguage = targetLanguage
        )
        viewModelScope.launch {
            val result = hybridAiCoordinator.generate(
                HybridAiRequest(
                    item = item,
                    operation = CloudAiOperation.TRANSLATE_DOCUMENT,
                    prompt = buildTranslationPrompt(sharedText, targetLanguage),
                    sharedTextPreview = sharedText,
                    privacyMode = AiPrivacyMode.SMART_HYBRID,
                    explicitConsent = explicitCloudConsent
                )
            )
            _uiState.value = when (result) {
                is HybridAiResult.Success -> _uiState.value.copy(
                    isTranslating = false,
                    translation = result.text,
                    translationOrigin = result.origin,
                    cloudConsent = null,
                    translationMessage = null
                )
                is HybridAiResult.ConsentRequired -> _uiState.value.copy(
                    isTranslating = false,
                    cloudConsent = result.disclosure
                )
                HybridAiResult.LocalOnlyUnavailable -> _uiState.value.copy(
                    isTranslating = false,
                    translationMessage = TranslationMessage.LOCAL_MODEL_UNAVAILABLE
                )
                HybridAiResult.CloudNotConfigured -> _uiState.value.copy(
                    isTranslating = false,
                    translationMessage = TranslationMessage.CLOUD_NOT_CONFIGURED
                )
                HybridAiResult.CloudGenerationFailed -> _uiState.value.copy(
                    isTranslating = false,
                    translationMessage = TranslationMessage.GENERATION_FAILED
                )
            }
        }
    }

    fun dismissCloudConsent() {
        _uiState.value = _uiState.value.copy(cloudConsent = null)
    }

    fun clearTranslationMessage() {
        _uiState.value = _uiState.value.copy(translationMessage = null)
    }

    fun generateAiSummary() {
        val item = _uiState.value.item ?: return
        val sharedText = translationSource(item)
        if (sharedText.isBlank() || _uiState.value.isGeneratingAiSummary) return

        _uiState.value = _uiState.value.copy(isGeneratingAiSummary = true)
        viewModelScope.launch {
            val prompt = if (java.util.Locale.getDefault().language.startsWith("ar")) {
                "اكتب ملخصاً تنفيذاً مركزاً وموجزاً (2-3 جمل) باللغة العربية لهذا المستند الخزني:\n\n$sharedText"
            } else {
                "Provide a clear, concise executive summary (2-3 sentences) for this vault document:\n\n$sharedText"
            }
            val result = hybridAiCoordinator.generate(
                HybridAiRequest(
                    item = item,
                    operation = CloudAiOperation.EXPLAIN_RETRIEVED_EVIDENCE,
                    prompt = prompt,
                    sharedTextPreview = sharedText,
                    privacyMode = AiPrivacyMode.SMART_HYBRID
                )
            )
            _uiState.value = when (result) {
                is HybridAiResult.Success -> _uiState.value.copy(
                    isGeneratingAiSummary = false,
                    aiSummary = result.text,
                    aiSummaryOrigin = result.origin
                )
                else -> _uiState.value.copy(
                    isGeneratingAiSummary = false,
                    aiSummary = null
                )
            }
        }
    }

    private fun translationSource(item: VaultItem): String = listOfNotNull(
        item.title.takeIf(String::isNotBlank)?.let { "Title: $it" },
        item.summary?.takeIf(String::isNotBlank)?.let { "Summary: $it" },
        item.rawOcrText?.takeIf(String::isNotBlank)?.let { "Text:\n$it" }
    )
        .distinct()
        .joinToString("\n\n")
        .take(HybridAiRequest.MAX_SHARED_PREVIEW_CHARS)

    private fun buildTranslationPrompt(text: String, targetLanguage: String): String = """
        Translate the saved text below into $targetLanguage.
        Preserve names, dates, amounts, identifiers, and line breaks exactly where possible.
        Return only the translation. Do not add advice or facts.

        $text
    """.trimIndent()
}

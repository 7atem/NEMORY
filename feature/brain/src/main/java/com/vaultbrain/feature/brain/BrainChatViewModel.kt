package com.vaultbrain.feature.brain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.core.ai.llm.CloudConsentDisclosure
import com.vaultbrain.core.ai.llm.CloudConsentStore
import com.vaultbrain.core.ai.llm.AiCapabilityManager
import com.vaultbrain.core.ai.rag.RagEngine
import com.vaultbrain.core.ai.rag.RagEvidence
import com.vaultbrain.core.ai.rag.RagEvidenceKind
import com.vaultbrain.core.ai.rag.RagCloudFailure
import com.vaultbrain.core.ai.rag.RagResponse
import com.vaultbrain.core.ai.rag.SearchFilters
import com.vaultbrain.shared.model.PersonalCollection
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.shared.model.VaultReminder
import com.vaultbrain.core.database.repository.BrainConversationRepository
import com.vaultbrain.core.database.repository.StoredBrainMessage
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.notifications.VaultReminderManager
import com.vaultbrain.feature.brain.model.ChatMessage
import com.vaultbrain.feature.brain.model.SuggestedAction
import dagger.hilt.android.lifecycle.HiltViewModel
import com.vaultbrain.core.integrations.action.ActionExecutor
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class BrainCloudConsent(
    val disclosure: CloudConsentDisclosure,
    internal val query: String,
    internal val conversationContext: String?,
    internal val assistantMessageId: String
)

enum class BrainCloudMessage {
    NOT_CONFIGURED,
    GENERATION_FAILED
}

data class BrainPendingWrite(
    val preview: BrainWritePreview,
    internal val assistantMessageId: String,
    val proposalId: String = java.util.UUID.randomUUID().toString()
)

data class BrainCollectionCreationPreview(
    val name: String,
    val items: List<com.vaultbrain.shared.model.VaultItem>,
    val originalQuery: String,
    val assistantMessageId: String
)

/** Local, persistent Brain conversation orchestrator for single-turn RAG requests. */
@HiltViewModel
class BrainChatViewModel @Inject constructor(
    private val ragEngine: RagEngine,
    private val conversationRepository: BrainConversationRepository,
    private val memoryBuilder: ConversationMemoryBuilder,
    private val vaultRepository: VaultRepository,
    private val suggestionProvider: BrainSuggestionProvider,
    private val cloudConsentStore: CloudConsentStore,
    private val writeActionPlanner: BrainWriteActionPlanner,
    private val capabilityManager: AiCapabilityManager,
    private val vaultReminderManager: VaultReminderManager,
    private val actionExecutor: ActionExecutor,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle(),
    private val agentProposalExecutor: AgentProposalExecutor? = null
) : ViewModel() {

    private val _isAiCoreEnabled = MutableStateFlow(false)
    val isAiCoreEnabled: StateFlow<Boolean> = _isAiCoreEnabled.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingStatus = MutableStateFlow<String?>(null)
    val loadingStatus: StateFlow<String?> = _loadingStatus.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isDeepResearch = MutableStateFlow(false)
    val isDeepResearch: StateFlow<Boolean> = _isDeepResearch.asStateFlow()

    fun toggleDeepResearch() {
        _isDeepResearch.value = !_isDeepResearch.value
    }

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _suggestions = MutableStateFlow<List<BrainSuggestion>>(emptyList())
    val suggestions: StateFlow<List<BrainSuggestion>> = _suggestions.asStateFlow()

    private val _cloudConsent = MutableStateFlow<BrainCloudConsent?>(null)
    val cloudConsent: StateFlow<BrainCloudConsent?> = _cloudConsent.asStateFlow()

    private val _cloudMessage = MutableStateFlow<BrainCloudMessage?>(null)
    val cloudMessage: StateFlow<BrainCloudMessage?> = _cloudMessage.asStateFlow()

    private val _pendingWrite = MutableStateFlow<BrainPendingWrite?>(null)
    val pendingWrite: StateFlow<BrainPendingWrite?> = _pendingWrite.asStateFlow()
    private val _agentProposals = MutableStateFlow<List<PendingAgentProposal>>(emptyList())
    val agentProposals: StateFlow<List<PendingAgentProposal>> = _agentProposals.asStateFlow()
    private val handledAgentProposals = mutableSetOf<Pair<String, com.vaultbrain.core.ai.rag.LocalAgent.ActionProposal>>()

    fun dismissAgentProposal(id: String) {
        _agentProposals.value.firstOrNull { it.id == id }?.let { handledAgentProposals += it.messageId to it.proposal }
        _agentProposals.update { list -> list.filterNot { it.id == id } }
    }

    fun confirmAgentProposal(id: String) {
        if (_isLoading.value || com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value) return
        val pending = _agentProposals.value.firstOrNull { it.id == id } ?: return
        dismissAgentProposal(id)
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val success = agentProposalExecutor?.execute(pending) == true
                val editor = pending.proposal is com.vaultbrain.core.ai.rag.LocalAgent.ActionProposal.CalendarEvent
                postActionNote(if (success) {
                    if (editor) { if (arabicUi()) "تم فتح محرر التقويم. راجع الحدث واحفظه هناك." else "Calendar editor opened. Review and save the event there." }
                    else { if (arabicUi()) "تم حفظ التغيير الذي أكدته." else "Your confirmed change was saved." }
                } else { if (arabicUi()) "تعذر تنفيذ الاقتراح. ربما تغير المصدر أو التاريخ." else "Could not apply this proposal. Its source or date may have changed." })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { postActionNote(if (arabicUi()) "تعذر تنفيذ الاقتراح." else "Could not apply this proposal.") }
            finally { _isLoading.value = false }
        }
    }

    private val _pendingCollectionCreation = MutableStateFlow<BrainCollectionCreationPreview?>(null)
    val pendingCollectionCreation: StateFlow<BrainCollectionCreationPreview?> = _pendingCollectionCreation.asStateFlow()

    private val executedProposals = mutableSetOf<String>()

    private val _pendingCollectionTarget = MutableStateFlow<VaultItem?>(null)
    val pendingCollectionTarget: StateFlow<VaultItem?> = _pendingCollectionTarget.asStateFlow()

    val activeCollections: StateFlow<List<PersonalCollection>> =
        vaultRepository.observeActiveCollections()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSuggestedAction(action: SuggestedAction) {
        val itemId = action.payload["itemId"] ?: return
        when (action.actionId) {
            "REMIND_EXPIRY" -> scheduleExpiryReminder(itemId)
            "ADD_COLLECTION" -> viewModelScope.launch {
                _pendingCollectionTarget.value = vaultRepository.getById(itemId)
            }
            else -> {
                val proactiveAction = com.vaultbrain.shared.model.ProactiveAction.fromCode(action.actionId)
                if (proactiveAction != null) {
                    viewModelScope.launch {
                        val item = vaultRepository.getById(itemId)
                        if (item != null) {
                            actionExecutor.execute(proactiveAction, item)
                            postActionNote(if (arabicUi()) "تم تنفيذ الإجراء." else "Action initiated.")
                        }
                    }
                }
            }
        }
    }

    private fun scheduleExpiryReminder(itemId: String) {
        viewModelScope.launch {
            val item = vaultRepository.getById(itemId) ?: return@launch
            val expiry = item.expiryDate
            if (expiry == null) {
                postActionNote(
                    if (arabicUi()) "لا يوجد تاريخ انتهاء معروف لهذا المستند."
                    else "No known expiry date for this document."
                )
                return@launch
            }
            val now = System.currentTimeMillis()
            val dueAt = when {
                expiry - TimeUnit.DAYS.toMillis(30) > now -> expiry - TimeUnit.DAYS.toMillis(30)
                expiry - TimeUnit.DAYS.toMillis(1) > now -> expiry - TimeUnit.DAYS.toMillis(1)
                else -> {
                    postActionNote(
                        if (arabicUi()) "تاريخ الانتهاء قريب جدًا أو قد مضى بالفعل."
                        else "The expiry date is too close or has already passed."
                    )
                    return@launch
                }
            }
            val created = vaultReminderManager.create(
                VaultReminder(
                    id = UUID.nameUUIDFromBytes("$itemId:$dueAt".toByteArray()).toString(),
                    title = item.title,
                    dueAt = dueAt,
                    vaultItemId = itemId
                )
            )
            postActionNote(
                if (created) {
                    if (arabicUi()) "تم ضبط التذكير لـ «${item.title}»."
                    else "Reminder set for '${item.title}'."
                } else {
                    if (arabicUi()) "تعذر ضبط التذكير."
                    else "Could not set the reminder."
                }
            )
        }
    }

    fun confirmAddToCollection(collectionId: String) {
        val item = _pendingCollectionTarget.value ?: return
        _pendingCollectionTarget.value = null
        viewModelScope.launch {
            val added = runCatching { vaultRepository.addItemToCollection(item.id, collectionId) }
                .getOrDefault(false)
            postActionNote(
                if (added) {
                    if (arabicUi()) "تمت إضافة «${item.title}» إلى المجموعة."
                    else "Added '${item.title}' to the collection."
                } else {
                    if (arabicUi()) "تعذرت الإضافة إلى المجموعة."
                    else "Could not add to the collection."
                }
            )
        }
    }

    fun dismissCollectionTarget() {
        _pendingCollectionTarget.value = null
    }

    private fun postActionNote(text: String) {
        _messages.update { it + ChatMessage.Assistant(id = UUID.randomUUID().toString(), text = text) }
    }

    private fun arabicUi(): Boolean = java.util.Locale.getDefault().language.startsWith("ar")

    init {
        viewModelScope.launch {
            com.vaultbrain.core.common.security.DecoySessionState.isDecoy.collect { decoy ->
                if (decoy) { _agentProposals.value = emptyList(); handledAgentProposals.clear() }
            }
        }
        // Optional prefill from the Today composer ("brain?query=..."). Prefill only —
        // the user always confirms before a query is sent.
        savedStateHandle.get<String>("query")?.takeIf { it.isNotBlank() }?.let { _input.value = it }
        viewModelScope.launch {
            conversationRepository.observeAll().collect { stored ->
                // A streaming turn is committed once complete, so database refreshes cannot
                // replace its in-memory placeholder or partial answer.
                if (!_isLoading.value) _messages.value = stored.mapNotNull(::toUiMessage)
            }
        }
        viewModelScope.launch {
            vaultRepository.observeActive().collect { items ->
                _suggestions.value = suggestionProvider.forItems(items)
            }
        }
        viewModelScope.launch {
            capabilityManager.capabilities.collect {
                _isAiCoreEnabled.value = it.localPromptAvailable
            }
        }
    }

    fun onInputChanged(text: String) {
        _input.value = text
    }

    fun clearConversation() {
        if (_isLoading.value) return
        _messages.value = emptyList()
        _input.value = ""
        _cloudConsent.value = null
        _cloudMessage.value = null
        _pendingWrite.value = null
        _pendingCollectionCreation.value = null
        _agentProposals.value = emptyList()
        handledAgentProposals.clear()
        viewModelScope.launch { conversationRepository.clear() }
    }

    fun retryQuery(query: String) {
        if (_isLoading.value || _pendingWrite.value != null || _pendingCollectionCreation.value != null) return
        val current = _messages.value
        val removeIds = buildList {
            val last = current.lastOrNull()
            if (last is ChatMessage.Assistant && last.isError) add(last.id)
            val beforeError = if (isNotEmpty()) current.dropLast(1) else current
            val user = beforeError.lastOrNull()
            if (user is ChatMessage.User && user.text == query) add(user.id)
        }
        _messages.update { messages -> messages.filterNot { it.id in removeIds } }
        viewModelScope.launch {
            conversationRepository.deleteByIds(removeIds)
            sendQuery(query)
        }
    }

    fun sendQuery(query: String) {
        val trimmed = query.trim()

        if (trimmed.isEmpty()) return
        if (_isLoading.value || _pendingWrite.value != null || _pendingCollectionCreation.value != null) return

        val priorContext = memoryBuilder.build(_messages.value)
        val userMessage = ChatMessage.User(
            id = generateId(),
            text = trimmed,
            imageUri = null,
            createdAt = System.currentTimeMillis()
        )
        _messages.update { it + userMessage }
        _input.value = ""
        _isLoading.value = true
        _loadingStatus.value = null
        _cloudConsent.value = null
        _cloudMessage.value = null

        viewModelScope.launch {
            conversationRepository.upsert(userMessage.toStored())
            when (val writePlan = writeActionPlanner.plan(trimmed)) {
                is BrainWritePlanResult.Ready -> {
                    val assistant = ChatMessage.Assistant(
                        id = generateId(),
                        text = writePreviewMessage(writePlan.preview),
                        sources = listOf(writePlan.preview.item),
                        confidence = 1f,
                        originalQuery = trimmed,
                        createdAt = maxOf(System.currentTimeMillis(), userMessage.createdAt + 1)
                    )
                    _messages.update { it + assistant }
                    conversationRepository.upsert(assistant.toStored())
                    _pendingWrite.value = BrainPendingWrite(writePlan.preview, assistant.id)
                    _isLoading.value = false
                _loadingStatus.value = null
                    return@launch
                }
                is BrainWritePlanResult.Rejected -> {
                    val assistant = ChatMessage.Assistant(
                        id = generateId(),
                        text = writeIssueMessage(writePlan.issue, trimmed),
                        originalQuery = trimmed,
                        createdAt = maxOf(System.currentTimeMillis(), userMessage.createdAt + 1)
                    )
                    _messages.update { it + assistant }
                    conversationRepository.upsert(assistant.toStored())
                    _isLoading.value = false
                _loadingStatus.value = null
                    return@launch
                }
                BrainWritePlanResult.NotWriteAction -> Unit
            }
            val assistantMessageId = generateId()
            val assistantCreatedAt = maxOf(System.currentTimeMillis(), userMessage.createdAt + 1)
            _loadingStatus.value = "Searching your vault..."
            var receivedResponse = false

            try {
                _isStreaming.value = true
                val budget = if (_isDeepResearch.value) com.vaultbrain.core.ai.llm.ReasoningBudget.DEEP else com.vaultbrain.core.ai.llm.ReasoningBudget.NORMAL
                ragEngine.queryStream(trimmed, SearchFilters(), priorContext, budget = budget).collect { response ->
                    receivedResponse = true
                    applyResponse(assistantMessageId, trimmed, priorContext, response)
                }
                if (!receivedResponse) {
                    updateAssistantError(assistantMessageId, trimmed, NO_RESULTS_MESSAGE)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                updateAssistantError(
                    assistantMessageId,
                    trimmed,
                    error.localizedMessage ?: ERROR_MESSAGE
                )
            } finally {
                _isStreaming.value = false
                val completed = _messages.value
                    .firstOrNull { it.id == assistantMessageId } as? ChatMessage.Assistant
                completed?.let { conversationRepository.upsert(it.toStored()) }
                _isLoading.value = false
                _loadingStatus.value = null
            }
        }
    }

    fun confirmWriteAction() {
        val pending = _pendingWrite.value ?: return
        if (_isLoading.value) return
        if (executedProposals.contains(pending.proposalId)) {
            _pendingWrite.value = null
            return
        }
        executedProposals.add(pending.proposalId)
        
        _pendingWrite.value = null
        _isLoading.value = true
        _loadingStatus.value = null
        viewModelScope.launch {
            try {
                when (val result = writeActionPlanner.execute(pending.preview)) {
                    is BrainWriteExecutionResult.Success -> updateWriteAssistant(
                        pending.assistantMessageId,
                        writeSuccessMessage(pending.preview, result.updatedItem),
                        listOf(result.updatedItem),
                        listOf(
                            com.vaultbrain.feature.brain.model.SuggestedAction(
                                label = if (containsArabic(pending.preview.originalQuery)) "فتح المستند" else "Open document",
                                actionId = "OPEN_ITEM",
                                payload = mapOf("itemId" to result.updatedItem.id)
                            )
                        )
                    )
                    is BrainWriteExecutionResult.Failed -> updateWriteAssistant(
                        pending.assistantMessageId,
                        writeIssueMessage(result.issue, pending.preview.originalQuery),
                        listOf(pending.preview.item),
                        listOf(
                            com.vaultbrain.feature.brain.model.SuggestedAction(
                                label = if (containsArabic(pending.preview.originalQuery)) "فتح المستند" else "Open document",
                                actionId = "OPEN_ITEM",
                                payload = mapOf("itemId" to pending.preview.item.id)
                            )
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateWriteAssistant(
                    pending.assistantMessageId,
                    writeIssueMessage(BrainWriteIssue.EXECUTION_FAILED, pending.preview.originalQuery),
                    listOf(pending.preview.item)
                )
            } finally {
                _isLoading.value = false
                _loadingStatus.value = null
            }
        }
    }

    fun dismissWriteAction(issue: BrainWriteIssue? = null) {
        val pending = _pendingWrite.value ?: return
        _pendingWrite.value = null
        val text = issue?.let { writeIssueMessage(it, pending.preview.originalQuery) }
            ?: if (containsArabic(pending.preview.originalQuery)) "لم يتم إجراء أي تغييرات."
            else "No changes were made."
        updateWriteAssistant(pending.assistantMessageId, text, listOf(pending.preview.item))
    }

    fun confirmCollectionCreation() {
        val pending = _pendingCollectionCreation.value ?: return
        if (_isLoading.value) return
        _pendingCollectionCreation.value = null
        _isLoading.value = true
        _loadingStatus.value = null
        viewModelScope.launch {
            try {
                val collection = vaultRepository.createCollection(
                    name = pending.name
                )
                pending.items.forEach { item ->
                    vaultRepository.addItemToCollection(item.id, collection.id)
                }
                updateWriteAssistant(
                    pending.assistantMessageId,
                    if (containsArabic(pending.originalQuery)) "تم إنشاء المجموعة «${pending.name}» بنجاح." else "Created collection '${pending.name}' successfully.",
                    pending.items
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateWriteAssistant(
                    pending.assistantMessageId,
                    if (containsArabic(pending.originalQuery)) "حدث خطأ أثناء إنشاء المجموعة." else "Failed to create collection.",
                    pending.items
                )
            } finally {
                _isLoading.value = false
                _loadingStatus.value = null
            }
        }
    }

    fun dismissCollectionCreation() {
        val pending = _pendingCollectionCreation.value ?: return
        _pendingCollectionCreation.value = null
        val actions = pending.items.firstOrNull()?.let { topItem ->
            listOf(
                com.vaultbrain.feature.brain.model.SuggestedAction(
                    label = if (containsArabic(pending.originalQuery)) "عرض المستند" else "View document",
                    actionId = "OPEN_ITEM",
                    payload = mapOf("itemId" to topItem.id)
                )
            )
        } ?: emptyList()
        updateWriteAssistant(
            pending.assistantMessageId,
            if (containsArabic(pending.originalQuery)) "لم يتم إنشاء المجموعة." else "Collection creation cancelled.",
            pending.items,
            actions
        )
    }

    private fun updateWriteAssistant(
        messageId: String, 
        text: String, 
        items: List<com.vaultbrain.shared.model.VaultItem>,
        actions: List<com.vaultbrain.feature.brain.model.SuggestedAction> = emptyList()
    ) {
        var updatedMessage: ChatMessage.Assistant? = null
        _messages.update { current ->
            current.map { message ->
                if (message.id == messageId && message is ChatMessage.Assistant) {
                    message.copy(
                        text = text, 
                        sources = items, 
                        confidence = 1f,
                        suggestedActions = actions
                    ).also { updatedMessage = it }
                } else {
                    message
                }
            }
        }
        updatedMessage?.let { message ->
            viewModelScope.launch { conversationRepository.upsert(message.toStored()) }
        }
    }

    private fun writePreviewMessage(preview: BrainWritePreview): String =
        if (containsArabic(preview.originalQuery)) {
            when (val action = preview.action) {
                BrainWriteAction.Pin -> "جاهز لتثبيت «${preview.item.title}». راجع التغيير وأكّده."
                BrainWriteAction.Unpin -> "جاهز لإلغاء تثبيت «${preview.item.title}». راجع التغيير وأكّده."
                BrainWriteAction.Archive -> "جاهز لأرشفة «${preview.item.title}». راجع التغيير وأكّده."
                BrainWriteAction.Restore -> "جاهز لاسترجاع «${preview.item.title}» من الأرشيف. راجع التغيير وأكّده."
                is BrainWriteAction.SetReminder ->
                    "جاهز لتعيين تذكير لـ «${preview.item.title}» في ${formatWriteDate(action.triggerAt)}. راجع التغيير وأكّده."
                BrainWriteAction.ClearReminder ->
                    "جاهز لإلغاء تذكير «${preview.item.title}». راجع التغيير وأكّده."
                is BrainWriteAction.MarkMediaCompleted -> when (action.kind) {
                    MediaCompletionKind.WATCHED -> "جاهز لوضع علامة «تمت المشاهدة» على «${preview.item.title}». راجع التغيير وأكّده."
                    MediaCompletionKind.FINISHED -> "جاهز لوضع علامة «تمت القراءة» على «${preview.item.title}». راجع التغيير وأكّده."
                }
            }
        } else {
            when (val action = preview.action) {
                BrainWriteAction.Pin -> "Ready to pin “${preview.item.title}”. Review and confirm the change."
                BrainWriteAction.Unpin -> "Ready to unpin “${preview.item.title}”. Review and confirm the change."
                BrainWriteAction.Archive -> "Ready to archive “${preview.item.title}”. Review and confirm the change."
                BrainWriteAction.Restore -> "Ready to restore “${preview.item.title}” from the archive. Review and confirm the change."
                is BrainWriteAction.SetReminder ->
                    "Ready to remind you about “${preview.item.title}” at ${formatWriteDate(action.triggerAt)}. Review and confirm the change."
                BrainWriteAction.ClearReminder ->
                    "Ready to clear the reminder for “${preview.item.title}”. Review and confirm the change."
                is BrainWriteAction.MarkMediaCompleted -> when (action.kind) {
                    MediaCompletionKind.WATCHED -> "Ready to mark “${preview.item.title}” as watched. Review and confirm the change."
                    MediaCompletionKind.FINISHED -> "Ready to mark “${preview.item.title}” as read. Review and confirm the change."
                }
            }
        }

    private fun writeSuccessMessage(
        preview: BrainWritePreview,
        item: com.vaultbrain.shared.model.VaultItem
    ): String = if (containsArabic(preview.originalQuery)) {
        when (val action = preview.action) {
            BrainWriteAction.Pin -> "تم تثبيت «${item.title}»."
            BrainWriteAction.Unpin -> "تم إلغاء تثبيت «${item.title}»."
            BrainWriteAction.Archive -> "تمت أرشفة «${item.title}»."
            BrainWriteAction.Restore -> "تم استرجاع «${item.title}» من الأرشيف."
            is BrainWriteAction.SetReminder ->
                "تم تعيين تذكير لـ «${item.title}» في ${formatWriteDate(action.triggerAt)}."
            BrainWriteAction.ClearReminder -> "تم إلغاء تذكير «${item.title}»."
            is BrainWriteAction.MarkMediaCompleted -> when (action.kind) {
                MediaCompletionKind.WATCHED -> "تم وضع علامة «تمت المشاهدة» على «${item.title}»."
                MediaCompletionKind.FINISHED -> "تم وضع علامة «تمت القراءة» على «${item.title}»."
            }
        }
    } else {
        when (val action = preview.action) {
            BrainWriteAction.Pin -> "Pinned “${item.title}”."
            BrainWriteAction.Unpin -> "Unpinned “${item.title}”."
            BrainWriteAction.Archive -> "Archived “${item.title}”."
            BrainWriteAction.Restore -> "Restored “${item.title}” from the archive."
            is BrainWriteAction.SetReminder ->
                "Reminder set for “${item.title}” at ${formatWriteDate(action.triggerAt)}."
            BrainWriteAction.ClearReminder -> "Cleared the reminder for “${item.title}”."
            is BrainWriteAction.MarkMediaCompleted -> when (action.kind) {
                MediaCompletionKind.WATCHED -> "Marked “${item.title}” as watched."
                MediaCompletionKind.FINISHED -> "Marked “${item.title}” as read."
            }
        }
    }

    private fun writeIssueMessage(issue: BrainWriteIssue, query: String): String {
        val arabic = containsArabic(query)
        return when (issue) {
            BrainWriteIssue.INVALID_COMMAND -> if (arabic) "لم يتم إجراء أي تغيير. استخدم عنوانًا وتاريخًا واضحين." else "No change was made. Use an exact title and an explicit date."
            BrainWriteIssue.INVALID_DATE -> if (arabic) "لم يتم إجراء أي تغيير. استخدم «غدًا» أو تاريخًا بصيغة YYYY-MM-DD." else "No change was made. Use “tomorrow” or a YYYY-MM-DD date."
            BrainWriteIssue.DATE_TOO_SOON -> if (arabic) "لم يتم إجراء أي تغيير. يجب أن يكون التذكير بعد خمس دقائق على الأقل." else "No change was made. The reminder must be at least five minutes from now."
            BrainWriteIssue.DATE_TOO_FAR -> if (arabic) "لم يتم إجراء أي تغيير. لا يمكن أن يتجاوز التذكير سنة واحدة." else "No change was made. Reminders cannot be more than one year away."
            BrainWriteIssue.NO_MATCH -> if (arabic) "لم يتم إجراء أي تغيير. لم أجد عنصرًا نشطًا بهذا العنوان." else "No change was made. I couldn't find an active item with that title."
            BrainWriteIssue.AMBIGUOUS_MATCH -> if (arabic) "لم يتم إجراء أي تغيير. يطابق العنوان أكثر من عنصر؛ استخدم العنوان الكامل." else "No change was made. More than one item matches; use the full title."
            BrainWriteIssue.CATEGORY_MISMATCH -> if (arabic) "لم يتم إجراء أي تغيير. استخدم «شاهدت» لفيلم أو مسلسل، و«قرأت» لكتاب." else "No change was made. Use watched for a movie or series, and read for a book."
            BrainWriteIssue.ALREADY_APPLIED -> if (arabic) "لا يلزم تغيير؛ هذا الإجراء مطبّق بالفعل." else "No change is needed; that action is already applied."
            BrainWriteIssue.ITEM_CHANGED -> if (arabic) "لم يتم إجراء أي تغيير لأن العنصر تغيّر. حاول الطلب مرة أخرى." else "No change was made because the item changed. Try the request again."
            BrainWriteIssue.EXECUTION_FAILED -> if (arabic) "تعذّر إكمال التغيير بأمان. راجع العنصر قبل المحاولة مرة أخرى." else "The change could not be completed safely. Check the item before trying again."
            BrainWriteIssue.NOTIFICATION_PERMISSION_DENIED -> if (arabic) "لم يتم تعيين التذكير لأن إذن الإشعارات لم يُمنح." else "The reminder was not set because notification permission was not granted."
        }
    }

    private fun formatWriteDate(timestamp: Long): String = java.time.format.DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm")
        .format(java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()))

    private fun containsArabic(text: String): Boolean = Regex("[\\u0600-\\u06FF]").containsMatchIn(text)

    fun confirmCloudAssist(rememberForCategories: Boolean = false) {
        val pending = _cloudConsent.value ?: return
        if (_isLoading.value) return
        if (rememberForCategories && pending.disclosure.canRememberForCategory) {
            cloudConsentStore.remember(pending.disclosure.rememberableCategories)
        }
        _cloudConsent.value = null
        _cloudMessage.value = null
        _isLoading.value = true
        _loadingStatus.value = null

        viewModelScope.launch {
            try {
                _isStreaming.value = true
                val budget = if (_isDeepResearch.value) com.vaultbrain.core.ai.llm.ReasoningBudget.DEEP else com.vaultbrain.core.ai.llm.ReasoningBudget.NORMAL
                ragEngine.queryStream(
                    pending.query,
                    SearchFilters(),
                    pending.conversationContext,
                    explicitCloudConsent = true,
                    budget = budget
                ).collect { response ->
                    applyResponse(
                        pending.assistantMessageId,
                        pending.query,
                        pending.conversationContext,
                        response
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _cloudMessage.value = BrainCloudMessage.GENERATION_FAILED
            } finally {
                _isStreaming.value = false
                val completed = _messages.value
                    .firstOrNull { it.id == pending.assistantMessageId } as? ChatMessage.Assistant
                completed?.let { conversationRepository.upsert(it.toStored()) }
                _isLoading.value = false
                _loadingStatus.value = null
            }
        }
    }

    fun dismissCloudConsent() {
        _cloudConsent.value = null
    }

    fun dismissCloudMessage() {
        _cloudMessage.value = null
    }

    private fun applyResponse(
        assistantMessageId: String,
        query: String,
        conversationContext: String?,
        response: RagResponse
    ) {
        if (com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value) return
        val proposals = response.proposals.filter { proposal ->
            assistantMessageId to proposal !in handledAgentProposals && _agentProposals.value.none {
                it.messageId == assistantMessageId && it.proposal == proposal }
        }.map { proposal ->
            val itemId = when (proposal) {
                is com.vaultbrain.core.ai.rag.LocalAgent.ActionProposal.Reminder -> proposal.itemId
                is com.vaultbrain.core.ai.rag.LocalAgent.ActionProposal.MetadataUpdate -> proposal.itemId
                is com.vaultbrain.core.ai.rag.LocalAgent.ActionProposal.CollectionMembership -> proposal.itemId
                else -> null
            }
            PendingAgentProposal(messageId = assistantMessageId, proposal = proposal, source = response.sources.firstOrNull { it.id == itemId })
        }
        if (proposals.isNotEmpty()) _agentProposals.update { (it + proposals).takeLast(5) }
        if (response.status != null) {
            _loadingStatus.value = response.status
            return
        }
        _loadingStatus.value = null
        if (_messages.value.none { it.id == assistantMessageId }) {
            _messages.update {
                it + ChatMessage.Assistant(
                    id = assistantMessageId,
                    text = "",
                    originalQuery = query,
                    createdAt = maxOf(System.currentTimeMillis(), it.lastOrNull()?.createdAt ?: 0)
                )
            }
        }
        
        response.cloudConsent?.let { disclosure ->
            _cloudConsent.value = BrainCloudConsent(
                disclosure = disclosure,
                query = query,
                conversationContext = conversationContext,
                assistantMessageId = assistantMessageId
            )
        }
        _cloudMessage.value = when (response.cloudFailure) {
            RagCloudFailure.NOT_CONFIGURED -> BrainCloudMessage.NOT_CONFIGURED
            RagCloudFailure.GENERATION_FAILED -> BrainCloudMessage.GENERATION_FAILED
            null -> _cloudMessage.value
        }
        _messages.update { currentMessages ->
            currentMessages.map { message ->
                if (message.id == assistantMessageId && message is ChatMessage.Assistant) {
                    var parsedAnswer = response.answer.orEmpty()

                    val createCollectionRegex = Regex("<create_collection name=\"([^\"]+)\">(.*?)</create_collection>")
                    val match = createCollectionRegex.find(parsedAnswer)
                    if (match != null) {
                        val collectionName = match.groupValues[1]
                        val indicesStr = match.groupValues[2]
                        val indexRegex = Regex("\\[(\\d+)\\]")
                        val indices = indexRegex.findAll(indicesStr).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()

                        val selectedItems = response.sources.filterIndexed { index, _ -> (index + 1) in indices }

                        if (selectedItems.isNotEmpty()) {
                            _pendingCollectionCreation.value = BrainCollectionCreationPreview(
                                name = collectionName,
                                items = selectedItems,
                                originalQuery = query,
                                assistantMessageId = assistantMessageId
                            )
                        }
                        parsedAnswer = parsedAnswer.replace(match.value, "").trim()
                    }

                    val actions = mutableListOf<com.vaultbrain.feature.brain.model.SuggestedAction>()
                    if (response.sources.isNotEmpty()) {
                        val topItem = response.sources.first()
                        actions.add(com.vaultbrain.feature.brain.model.SuggestedAction("Open document", "OPEN_ITEM", mapOf("itemId" to topItem.id)))

                        if (topItem.parsedMetadata.containsKey("expiry_date") || topItem.parsedMetadata.containsKey("due_date")) {
                            actions.add(com.vaultbrain.feature.brain.model.SuggestedAction("Remind me 30 days before", "REMIND_EXPIRY", mapOf("itemId" to topItem.id)))
                        }
                        actions.add(com.vaultbrain.feature.brain.model.SuggestedAction("Add to collection", "ADD_COLLECTION", mapOf("itemId" to topItem.id)))
                    }

                    message.copy(
                        text = parsedAnswer.ifBlank {
                            if (response.sources.isEmpty() && response.externalSources.isEmpty()) NO_RESULTS_MESSAGE else message.text
                        },
                        sources = response.sources,
                        externalSources = response.externalSources,
                        suggestedActions = actions,
                        confidence = response.confidence,
                        evidence = response.evidence,
                        responseOrigin = response.responseOrigin ?: message.responseOrigin,
                        isError = false
                    )
                } else {
                    message
                }
            }
        }
    }

    private fun updateAssistantError(messageId: String, query: String, message: String) {
        _messages.update { currentMessages ->
            val exists = currentMessages.any { it.id == messageId }
            if (exists) {
                currentMessages.map { current ->
                    if (current.id == messageId && current is ChatMessage.Assistant) {
                        current.copy(text = message, isError = true, originalQuery = query)
                    } else {
                        current
                    }
                }
            } else {
                currentMessages + ChatMessage.Assistant(
                    id = messageId,
                    text = message,
                    isError = true,
                    originalQuery = query,
                    createdAt = System.currentTimeMillis()
                )
            }
        }
    }

    private fun toUiMessage(message: StoredBrainMessage): ChatMessage? = when (message.role) {
        BrainConversationRepository.ROLE_USER -> ChatMessage.User(
            id = message.id,
            text = message.text,
            createdAt = message.createdAt
        )
        BrainConversationRepository.ROLE_ASSISTANT -> ChatMessage.Assistant(
            id = message.id,
            text = message.text,
            sources = message.sources,
            confidence = message.confidence,
            evidence = message.evidenceHeadline?.let { headline ->
                message.evidenceKind
                    ?.let { kind -> RagEvidenceKind.entries.firstOrNull { it.name == kind } }
                    ?.let { kind -> RagEvidence(kind, headline, message.evidenceFacts) }
            },
            responseOrigin = message.responseOrigin?.let { storedOrigin ->
                com.vaultbrain.core.ai.llm.AiResponseOrigin.entries
                    .firstOrNull { it.name == storedOrigin }
            },
            isError = message.isError,
            originalQuery = message.originalQuery,
            createdAt = message.createdAt
        )
        else -> null
    }

    private fun ChatMessage.toStored(): StoredBrainMessage = when (this) {
        is ChatMessage.User -> StoredBrainMessage(
            id = id,
            role = BrainConversationRepository.ROLE_USER,
            text = text,
            createdAt = createdAt
        )
        is ChatMessage.Assistant -> StoredBrainMessage(
            id = id,
            role = BrainConversationRepository.ROLE_ASSISTANT,
            text = text,
            sources = sources,
            confidence = confidence,
            evidenceKind = evidence?.kind?.name,
            evidenceHeadline = evidence?.headline,
            evidenceFacts = evidence?.supportingFacts.orEmpty(),
            responseOrigin = responseOrigin?.name,
            isError = isError,
            originalQuery = originalQuery,
            createdAt = createdAt
        )
    }

    private fun generateId(): String = UUID.randomUUID().toString()

    private companion object {
        const val NO_RESULTS_MESSAGE = "I couldn't find a confident match in your vault. Try using a title, date, amount, or category."
        const val ERROR_MESSAGE = "Something went wrong while searching your vault. Please try again."
    }
}

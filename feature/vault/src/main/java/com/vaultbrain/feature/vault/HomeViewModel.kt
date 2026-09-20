package com.vaultbrain.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.core.ai.llm.gemma.GemmaDownloadEligibility
import com.vaultbrain.core.ai.llm.gemma.GemmaModelManager
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelStatus
import com.vaultbrain.shared.model.PersonalCollection
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.shared.model.VaultReminder
import com.vaultbrain.shared.model.external.ExternalSource
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.integrations.model.ExternalRecord
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import com.vaultbrain.core.integrations.action.ActionExecutor
import com.vaultbrain.shared.model.ProactiveAction
import com.vaultbrain.core.notifications.BackupNudgeStore
import com.vaultbrain.core.notifications.GemmaBannerStore
import com.vaultbrain.core.notifications.VaultReminderManager
import com.vaultbrain.feature.vault.home.AttentionItem
import com.vaultbrain.feature.vault.home.ExpiringDocument
import com.vaultbrain.feature.vault.home.buildAttentionItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

/**
 * State of the dismissible on-device-model banner on the Today tab.
 * [progress] is non-null while the model download runs.
 */
data class GemmaBanner(
    val progress: Int? = null,
    val queued: Boolean = false
)

/**
 * UI state for the home dashboard.
 */
data class HomeUiState(
    val recentItems: List<VaultItem> = emptyList(),
    val activeItems: List<VaultItem> = emptyList(),
    val collections: List<PersonalCollection> = emptyList(),
    val needsReviewCount: Int = 0,
    val expiringSoonCount: Int = 0,
    val quickCaptureSuggestion: CaptureSuggestion? = null,
    val collectionSuggestion: CollectionSuggestion? = null,
    val todayEvents: List<ExternalRecord> = emptyList(),
    val upcomingEvents: List<ExternalRecord> = emptyList(),
    val todayGmail: List<ExternalRecord> = emptyList(),
    val todayReminders: List<VaultReminder> = emptyList(),
    val upcomingReminders: List<VaultReminder> = emptyList(),
    val attentionItems: List<AttentionItem> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val patternDetector: CapturePatternDetector,
    private val gemmaDownloadEligibility: GemmaDownloadEligibility,
    private val gemmaModelManager: GemmaModelManager,
    private val gemmaBannerStore: GemmaBannerStore,
    private val externalContextRepository: ExternalContextRepository,
    private val vaultReminderManager: VaultReminderManager,
    private val backupNudgeStore: BackupNudgeStore,
    private val actionExecutor: ActionExecutor,
    private val dailyIntelligence: com.vaultbrain.core.ai.rag.DailyIntelligence? = null
) : ViewModel() {
    private val _dailyInsights = MutableStateFlow<List<com.vaultbrain.core.ai.rag.DailyInsight>>(emptyList())
    val dailyInsights: StateFlow<List<com.vaultbrain.core.ai.rag.DailyInsight>> = _dailyInsights
    fun dismissDailyInsight(insight: com.vaultbrain.core.ai.rag.DailyInsight) {
        _dailyInsights.update { it.filterNot { item -> item == insight } }
        viewModelScope.launch { dailyIntelligence?.dismiss(insight) }
    }
    private var insightJob: kotlinx.coroutines.Job? = null
    private var lastInsightRefresh = 0L
    private var insightInput: InsightInput? = null

    private data class InsightInput(
        val items: List<VaultItem>,
        val events: List<ExternalRecord>,
        val reminders: List<VaultReminder>,
        val downloadedModelReady: Boolean
    )

    fun refreshDailyInsights(items: List<VaultItem>, events: List<ExternalRecord>, reminders: List<VaultReminder>) {
        if (com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value) {
            insightJob?.cancel()
            _dailyInsights.value = emptyList()
            lastInsightRefresh = 0L
            insightInput = null
            return
        }
        val now = System.currentTimeMillis()
        val input = InsightInput(items.toList(), events.toList(), reminders.toList(),
            gemmaModelManager.status.value == OnDeviceModelStatus.READY)
        if (input == insightInput &&
            (insightJob?.isActive == true || now - lastInsightRefresh < 6 * 60 * 60 * 1000)) return
        insightJob?.cancel()
        insightInput = input
        // Do not show conclusions about records that have changed or been removed.
        _dailyInsights.value = emptyList()
        insightJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1_000) // Coalesce imports and connector sync bursts.
            val selected = dailyIntelligence?.select(input.items, input.events, input.reminders).orEmpty()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (!com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value && insightInput == input) {
                _dailyInsights.value = selected
                lastInsightRefresh = System.currentTimeMillis()
            }
        }
    }

    private val gemmaEligible = MutableStateFlow(false)
    private val bannerDismissTick = MutableStateFlow(0)

    private val _backupNudgeVisible = MutableStateFlow(true)
    val backupNudgeVisible: StateFlow<Boolean> = _backupNudgeVisible

    init {
        _backupNudgeVisible.value = runCatching { backupNudgeStore.shouldShow() }.getOrDefault(true)
        viewModelScope.launch {
            gemmaEligible.value = runCatching { gemmaDownloadEligibility.isPromptEligible() }
                .getOrDefault(false)
        }
    }

    fun dismissBackupNudge() {
        backupNudgeStore.recordDismiss()
        _backupNudgeVisible.value = false
    }

    /**
     * Non-null when the Today-tab model banner should be visible: while the download runs
     * (progress), or when the device is eligible and the banner store still allows showing.
     */
    val gemmaBanner: StateFlow<GemmaBanner?> = combine(
        gemmaModelManager.status,
        gemmaEligible,
        bannerDismissTick
    ) { status, eligible, _ ->
        when {
            status is OnDeviceModelStatus.DOWNLOADING -> GemmaBanner(progress = status.progress)
            eligible && status == OnDeviceModelStatus.NOT_DOWNLOADED && gemmaBannerStore.shouldShow() ->
                GemmaBanner()
            else -> null
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    fun downloadGemmaModel() {
        gemmaModelManager.downloadModel()
    }

    fun dismissGemmaBanner() {
        gemmaBannerStore.recordDismiss()
        bannerDismissTick.value += 1
    }

    private val temporalContext = combine(
        externalContextRepository.observeRecordsBySource(ExternalSource.CALENDAR),
        externalContextRepository.observeRecordsBySource(ExternalSource.GMAIL),
        vaultReminderManager.observeActive()
    ) { events, gmailRecords, reminders ->
        val zone = java.time.ZoneId.systemDefault()
        val todayStart = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowStart = java.time.LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val upcomingEnd = tomorrowStart + 14L * 24 * 60 * 60 * 1000
        TemporalContext(
            todayEvents = events.filter {
                val start = it.startAt ?: return@filter false
                val end = it.endAt ?: start
                start < tomorrowStart && end >= todayStart
            }.sortedBy { it.startAt },
            upcomingEvents = events.filter {
                val start = it.startAt ?: return@filter false
                start in tomorrowStart..upcomingEnd
            },
            todayReminders = reminders.filter { it.dueAt < tomorrowStart }.sortedBy { it.dueAt },
            upcomingReminders = reminders.filter { it.dueAt in tomorrowStart..upcomingEnd }.sortedBy { it.dueAt },
            todayGmail = gmailRecords.filter { !it.isResolved &&
                (it.expiresAt?.let { expiry -> expiry > System.currentTimeMillis() } ?: true) }
                .sortedByDescending { it.updatedAt }.take(12)
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeRecent(50), // Increased to feed pattern detector
        repository.observeActive(),
        repository.observeActiveCollections(),
        temporalContext
    ) { recent, active, collections, temporal ->
        val expiryThreshold = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
        val suggestion = patternDetector.detectPattern(recent)
        HomeUiState(
            recentItems = recent.take(10), // Only first 10 for UI
            activeItems = active,
            collections = collections,
            needsReviewCount = active.count { it.needsReview },
            expiringSoonCount = active.count { item ->
                listOfNotNull(item.expiryDate, item.secondaryAlertDate)
                    .any { it in System.currentTimeMillis()..expiryThreshold }
            },
            quickCaptureSuggestion = suggestion,
            collectionSuggestion = patternDetector.detectCollectionPattern(recent),
            todayEvents = temporal.todayEvents,
            upcomingEvents = temporal.upcomingEvents,
            todayReminders = temporal.todayReminders,
            upcomingReminders = temporal.upcomingReminders,
            todayGmail = temporal.todayGmail,
            attentionItems = buildAttentionItems(
                todayReminders = temporal.todayReminders,
                todayEvents = temporal.todayEvents,
                todayGmail = temporal.todayGmail,
                expiringDocuments = active.mapNotNull { item ->
                    listOfNotNull(item.expiryDate, item.secondaryAlertDate)
                        .filter { it in System.currentTimeMillis()..expiryThreshold }
                        .minOrNull()
                        ?.let { due -> ExpiringDocument(item.id, item.title, due) }
                },
                recentDocuments = recent.take(10), // Supply recent documents for proactive AI insights
                needsReviewCount = active.count { it.needsReview }
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    init {
        viewModelScope.launch {
            combine(repository.observeActive(), externalContextRepository.observeRecords(),
                vaultReminderManager.observeActive(), gemmaModelManager.status,
                com.vaultbrain.core.common.security.DecoySessionState.isDecoy) { items, records, reminders, _, _ ->
                Triple(items, records, reminders)
            }.collect { (items, records, reminders) -> refreshDailyInsights(items, records, reminders) }
        }
    }

    fun archiveItem(item: VaultItem) {
        viewModelScope.launch {
            repository.setArchived(item.id, true)
        }
    }

    fun restoreItem(item: VaultItem) {
        viewModelScope.launch {
            repository.setArchived(item.id, false)
        }
    }

    fun createReminder(title: String, dueAt: Long) {
        viewModelScope.launch {
            vaultReminderManager.create(VaultReminder(title = title, dueAt = dueAt))
        }
    }

    fun editReminderDue(id: String, dueAt: Long) {
        viewModelScope.launch { vaultReminderManager.editDueTime(id, dueAt) }
    }

    fun snoozeReminder(id: String) {
        viewModelScope.launch {
            vaultReminderManager.snooze(id, System.currentTimeMillis() + 15L * 60 * 1000)
        }
    }

    fun completeReminder(id: String) {
        viewModelScope.launch { vaultReminderManager.complete(id) }
    }

    fun executeAction(action: ProactiveAction, item: VaultItem) {
        viewModelScope.launch {
            actionExecutor.execute(action, item)
        }
    }
}

private data class TemporalContext(
    val todayEvents: List<ExternalRecord>,
    val upcomingEvents: List<ExternalRecord>,
    val todayReminders: List<VaultReminder>,
    val upcomingReminders: List<VaultReminder>,
    val todayGmail: List<ExternalRecord>
)

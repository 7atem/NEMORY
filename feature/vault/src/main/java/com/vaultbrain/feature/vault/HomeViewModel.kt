package com.vaultbrain.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.core.ai.llm.gemma.GemmaDownloadEligibility
import com.vaultbrain.core.ai.llm.gemma.GemmaModelManager
import com.vaultbrain.core.ai.llm.gemma.GemmaModelStatus
import com.vaultbrain.core.billing.FeatureGate
import com.vaultbrain.core.common.model.PersonalCollection
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.common.model.VaultReminder
import com.vaultbrain.core.common.model.external.ExternalSource
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.integrations.model.ExternalRecord
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import com.vaultbrain.core.notifications.GemmaBannerStore
import com.vaultbrain.core.notifications.VaultReminderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val showAds: Boolean = false,
    val quickCaptureSuggestion: CaptureSuggestion? = null,
    val todayEvents: List<ExternalRecord> = emptyList(),
    val upcomingEvents: List<ExternalRecord> = emptyList(),
    val recentEmailContext: List<ExternalRecord> = emptyList(),
    val todayReminders: List<VaultReminder> = emptyList(),
    val upcomingReminders: List<VaultReminder> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val featureGate: FeatureGate,
    private val patternDetector: CapturePatternDetector,
    private val gemmaDownloadEligibility: GemmaDownloadEligibility,
    private val gemmaModelManager: GemmaModelManager,
    private val gemmaBannerStore: GemmaBannerStore,
    private val externalContextRepository: ExternalContextRepository,
    private val vaultReminderManager: VaultReminderManager
) : ViewModel() {

    private val gemmaEligible = MutableStateFlow(false)
    private val bannerDismissTick = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            gemmaEligible.value = runCatching { gemmaDownloadEligibility.isPromptEligible() }
                .getOrDefault(false)
        }
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
            status == GemmaModelStatus.QUEUED -> GemmaBanner(queued = true)
            status is GemmaModelStatus.DOWNLOADING -> GemmaBanner(progress = status.progress)
            eligible && status == GemmaModelStatus.NOT_DOWNLOADED && gemmaBannerStore.shouldShow() ->
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
    ) { events, emails, reminders ->
        val now = System.currentTimeMillis()
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
            }.sortedBy { it.startAt },
            recentEmailContext = emails.filter { email ->
                !email.isResolved && email.createdAt >= now - 7L * 24 * 60 * 60 * 1000 &&
                    email.payload.keys.any { it in ACTIONABLE_EMAIL_FIELDS }
            }.sortedByDescending { it.createdAt }.take(5),
            todayReminders = reminders.filter { it.dueAt < tomorrowStart }.sortedBy { it.dueAt },
            upcomingReminders = reminders.filter { it.dueAt in tomorrowStart..upcomingEnd }.sortedBy { it.dueAt }
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeRecent(50), // Increased to feed pattern detector
        repository.observeActive(),
        repository.observeActiveCollections(),
        featureGate.entitlement,
        temporalContext
    ) { recent, active, collections, entitlement, temporal ->
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
            showAds = false,
            quickCaptureSuggestion = suggestion,
            todayEvents = temporal.todayEvents,
            upcomingEvents = temporal.upcomingEvents,
            recentEmailContext = temporal.recentEmailContext,
            todayReminders = temporal.todayReminders,
            upcomingReminders = temporal.upcomingReminders
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

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
}

private data class TemporalContext(
    val todayEvents: List<ExternalRecord>,
    val upcomingEvents: List<ExternalRecord>,
    val recentEmailContext: List<ExternalRecord>,
    val todayReminders: List<VaultReminder>,
    val upcomingReminders: List<VaultReminder>
)

private val ACTIONABLE_EMAIL_FIELDS = setOf("order_number", "tracking_number", "total")

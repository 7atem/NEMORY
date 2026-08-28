package com.vaultbrain.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.core.ai.llm.gemma.GemmaDownloadEligibility
import com.vaultbrain.core.ai.llm.gemma.GemmaModelManager
import com.vaultbrain.core.ai.llm.gemma.GemmaModelStatus
import com.vaultbrain.core.common.model.PersonalCollection
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.notifications.GemmaBannerStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.vaultbrain.core.billing.FeatureGate

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
    val quickCaptureSuggestion: CaptureSuggestion? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val featureGate: FeatureGate,
    private val patternDetector: CapturePatternDetector,
    private val gemmaDownloadEligibility: GemmaDownloadEligibility,
    private val gemmaModelManager: GemmaModelManager,
    private val gemmaBannerStore: GemmaBannerStore
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

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeRecent(50), // Increased to feed pattern detector
        repository.observeActive(),
        repository.observeActiveCollections(),
        featureGate.entitlement
    ) { recent, active, collections, entitlement ->
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
            quickCaptureSuggestion = suggestion
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

}

package com.vaultbrain.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.notifications.UnifiedAlertManager
import com.vaultbrain.feature.vault.components.lensTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID
import com.vaultbrain.shared.model.SourceType

/**
 * UI state for the lens detail screen.
 */
data class LensDetailUiState(
    val lensId: String = "",
    val title: String = "",
    val items: List<VaultItem> = emptyList(),
    val totalCount: Int = 0,
    val expiringCount: Int = 0,
    val showStealth: Boolean = false,
    val moneySaved: Double = 0.0
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class LensDetailViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val alertManager: UnifiedAlertManager
) : ViewModel() {

    private val lensIdFlow = MutableStateFlow<String?>(null)
    private val showStealthFlow = MutableStateFlow(false)

    val uiState: StateFlow<LensDetailUiState> = combine(
        lensIdFlow.filterNotNull(),
        showStealthFlow
    ) { lensId, showStealth -> lensId to showStealth }
        .flatMapLatest { (lensId, showStealth) ->
            val flow = if (showStealth) {
                repository.observeByLensWithStealth(lensId)
            } else {
                repository.observeByLens(lensId)
            }
            flow.map { items ->
                val now = System.currentTimeMillis()
                var totalSaved = 0.0
                if (lensId == LensId.MONEY) {
                    items.forEach { item ->
                        val savedStr = item.parsedMetadata["saved_amount"] ?: item.customFields["saved_amount"] ?: item.parsedMetadata["value"] ?: item.customFields["value"]
                        savedStr?.toDoubleOrNull()?.let { totalSaved += it }
                    }
                }
                LensDetailUiState(
                    lensId = lensId,
                    title = lensTitle(lensId),
                    items = items,
                    totalCount = items.size,
                    expiringCount = items.count { it.expiryDate?.let { date -> date > now } == true },
                    showStealth = showStealth,
                    moneySaved = totalSaved
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LensDetailUiState()
        )

    fun loadLens(lensId: String) {
        if (lensIdFlow.value != lensId) {
            lensIdFlow.value = lensId
            showStealthFlow.value = false
        }
    }

    fun toggleStealth() {
        showStealthFlow.value = !showStealthFlow.value
    }

    fun remindMediaItem(item: VaultItem, daysFromNow: Int = 7) {
        viewModelScope.launch {
            val triggerAt = System.currentTimeMillis() + daysFromNow * 24L * 60L * 60L * 1_000L
            val updated = item.copy(secondaryAlertDate = triggerAt)
            repository.save(updated)
            alertManager.scheduleAlerts(updated)
        }
    }

    fun completeMediaItem(item: VaultItem) {
        viewModelScope.launch {
            val type = item.customFields["media_type"] ?: item.parsedMetadata["media_type"]
            val status = if (type == "book") "finished" else "watched"
            val updated = item.copy(
                parsedMetadata = item.parsedMetadata + ("media_status" to status),
                customFields = item.customFields + ("media_status" to status),
                secondaryAlertDate = null
            )
            repository.save(updated)
            alertManager.cancelForItem(item.id)
        }
    }

    fun saveScamScan(url: String, riskLevel: String, reason: String) {
        viewModelScope.launch {
            repository.save(
                VaultItem(
                    id = UUID.randomUUID().toString(),
                    title = "Link scan: ${url.take(48)}",
                    summary = reason,
                    rawOcrText = url,
                    sourceType = SourceType.TEXT_PASTE,
                    parsedMetadata = mapOf("url" to url, "risk_level" to riskLevel),
                    customFields = mapOf("scanned_url" to url, "risk_level" to riskLevel),
                    lensTags = setOf(LensId.BUREAUCRACY),
                    needsReview = riskLevel == "UNKNOWN"
                )
            )
        }
    }
}

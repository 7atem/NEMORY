package com.vaultbrain.feature.settings.viewmodel

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.android.billingclient.api.BillingClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vaultbrain.core.database.dao.AuditLogDao
import com.vaultbrain.core.database.entity.AuditLogEntity
import com.vaultbrain.core.security.AuthManager
import com.vaultbrain.sync.drive.DriveBackupManager
import com.vaultbrain.core.ai.llm.AiCapabilityManager
import com.vaultbrain.core.ai.llm.OnDeviceModelStatus as PrivateAiStatus
import com.vaultbrain.core.ai.llm.CloudConsentStore
import com.vaultbrain.core.ai.llm.gemma.GemmaModelManager
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelStatus as GemmaModelStatus
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.billing.AdEntitlement
import com.vaultbrain.core.billing.BillingManager
import com.vaultbrain.core.billing.FeatureGate
import com.vaultbrain.feature.capture.LocalAiAdoptionManager
import com.vaultbrain.feature.capture.worker.LlmEnrichmentWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the settings screen.
 */
import com.vaultbrain.core.common.preferences.ThemeMode
import com.vaultbrain.core.common.preferences.AppearancePreferences

data class SettingsUiState(
    val toastMessage: String? = null,
    val biometricEnabled: Boolean = false,
    val pinSet: Boolean = false,
    val autoLockMinutes: Int = 5,
    val decoyPinSet: Boolean = false,
    val driveBackupEnabled: Boolean = false,
    val gmailIntegrationEnabled: Boolean = false,
    val affiliateLinksEnabled: Boolean = true,
    val showAuditLog: Boolean = false,
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val isPinSetupVisible: Boolean = false,
    val isDecoyPinSetup: Boolean = false,
    val lensAccess: Map<String, Boolean> = emptyMap(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val privateAiStatus: PrivateAiStatus = PrivateAiStatus.UNKNOWN,
    val cloudOptInCategories: Set<Classification> = emptySet(),
    val adEntitlement: AdEntitlement = AdEntitlement.AD_SUPPORTED,
    val removeAdsPrice: String? = null,
    val billingInProgress: Boolean = false,
    val billingNotice: BillingNotice? = null,
    val localAiWorkRunning: Boolean = false,
    val localAiProcessed: Int = 0,
    val localAiTotal: Int = 0,
    val localAiLastImproved: Int? = null,
    val backupInProgress: Boolean = false
)

enum class BillingNotice {
    PURCHASE_UNAVAILABLE,
    ALREADY_OWNED
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val auditLogDao: AuditLogDao,
    private val driveBackupManager: DriveBackupManager,
    private val featureGate: FeatureGate,
    private val billingManager: BillingManager,
    private val aiCapabilityManager: AiCapabilityManager,
    private val cloudConsentStore: CloudConsentStore,
    private val gemmaModelManager: GemmaModelManager,
    private val localAiAdoptionManager: LocalAiAdoptionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val gemmaStatus: StateFlow<GemmaModelStatus> = gemmaModelManager.status

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            biometricEnabled = authManager.isBiometricEnabled,
            pinSet = authManager.isPinSet,
            autoLockMinutes = authManager.autoLockMinutes,
            decoyPinSet = authManager.isDecoyPinSet,
            driveBackupEnabled = false,
            lensAccess = buildMap {
                com.vaultbrain.shared.domain.LensId.FREE_LENSES.forEach { put(it, featureGate.canAccessLens(it)) }
                com.vaultbrain.shared.domain.LensId.PRO_LENSES.forEach { put(it, featureGate.canAccessLens(it)) }
            },
            themeMode = com.vaultbrain.core.common.preferences.AppearancePreferences.themeMode.value,
            dynamicColor = com.vaultbrain.core.common.preferences.AppearancePreferences.dynamicColor.value
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            auditLogDao.observeRecent(50).collect { logs ->
                _uiState.value = _uiState.value.copy(auditLogs = logs)
            }
        }
        viewModelScope.launch {
            com.vaultbrain.core.common.preferences.AppearancePreferences.themeMode.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
        viewModelScope.launch {
            com.vaultbrain.core.common.preferences.AppearancePreferences.dynamicColor.collect { dc ->
                _uiState.value = _uiState.value.copy(dynamicColor = dc)
            }
        }
        viewModelScope.launch {
            val status = runCatching { aiCapabilityManager.refresh().modelStatus }
                .getOrElse { PrivateAiStatus.ERROR }
            _uiState.value = _uiState.value.copy(privateAiStatus = status)
        }
        viewModelScope.launch {
            cloudConsentStore.optedInCategories.collect { categories ->
                _uiState.value = _uiState.value.copy(cloudOptInCategories = categories)
            }
        }
        viewModelScope.launch {
            featureGate.entitlement.collect { entitlement ->
                _uiState.value = _uiState.value.copy(adEntitlement = entitlement)
            }
        }
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(LlmEnrichmentWorker.WORK_NAME)
                .collect { workInfos ->
                    val current = workInfos.lastOrNull { !it.state.isFinished }
                        ?: workInfos.lastOrNull()
                    val running = current?.state in setOf(
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.BLOCKED,
                        WorkInfo.State.RUNNING
                    )
                    _uiState.value = _uiState.value.copy(
                        localAiWorkRunning = running,
                        localAiProcessed = current?.progress
                            ?.getInt(LlmEnrichmentWorker.KEY_PROCESSED, 0) ?: 0,
                        localAiTotal = current?.progress
                            ?.getInt(LlmEnrichmentWorker.KEY_TOTAL, 0) ?: 0,
                        localAiLastImproved = if (current?.state == WorkInfo.State.SUCCEEDED) {
                            current.outputData.getInt(LlmEnrichmentWorker.KEY_IMPROVED, 0)
                        } else {
                            _uiState.value.localAiLastImproved
                        }
                    )
                }
        }
        refreshRemoveAdsOffer()
    }

    fun refreshSecurityState() {
        _uiState.value = _uiState.value.copy(
            biometricEnabled = authManager.isBiometricEnabled,
            pinSet = authManager.isPinSet,
            autoLockMinutes = authManager.autoLockMinutes,
            decoyPinSet = authManager.isDecoyPinSet
        )
    }

    fun setBiometricEnabled(enabled: Boolean) {
        authManager.enableBiometric(enabled)
        _uiState.value = _uiState.value.copy(biometricEnabled = enabled)
    }

    fun setAutoLockMinutes(minutes: Int) {
        authManager.setAutoLockMinutes(minutes)
        _uiState.value = _uiState.value.copy(autoLockMinutes = minutes)
    }

    fun setDecoyPin(pin: String) {
        authManager.setDecoyPin(pin)
        _uiState.value = _uiState.value.copy(decoyPinSet = true)
    }

    fun setPin(pin: String) {
        authManager.setPin(pin)
        _uiState.value = _uiState.value.copy(pinSet = true)
    }

    fun toggleAuditLog() {
        _uiState.value = _uiState.value.copy(showAuditLog = !_uiState.value.showAuditLog)
    }

    fun showPinSetup(isDecoy: Boolean = false) {
        _uiState.value = _uiState.value.copy(isPinSetupVisible = true, isDecoyPinSetup = isDecoy)
    }

    fun dismissPinSetup() {
        _uiState.value = _uiState.value.copy(isPinSetupVisible = false, isDecoyPinSetup = false)
    }

    fun setDriveBackupEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            driveBackupEnabled = false,
            toastMessage = "Google Drive backup is not configured in this build"
        )
    }

    fun setGmailIntegrationEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            gmailIntegrationEnabled = false,
            toastMessage = null
        )
    }

    fun setAffiliateLinksEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(affiliateLinksEnabled = enabled)
    }

    fun setThemeMode(mode: ThemeMode) {
        com.vaultbrain.core.common.preferences.AppearancePreferences.setThemeMode(mode)
    }

    
    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    fun setDynamicColor(enabled: Boolean) {
        com.vaultbrain.core.common.preferences.AppearancePreferences.setDynamicColor(enabled)
    }

    fun exportPortableBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(backupInProgress = true)
            val result = driveBackupManager.exportToUri(uri, passphrase.toCharArray())
            _uiState.value = _uiState.value.copy(
                backupInProgress = false,
                toastMessage = result.fold(
                    onSuccess = { "Encrypted backup saved" },
                    onFailure = { it.message ?: "Backup failed" }
                )
            )
        }
    }

    fun importPortableBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(backupInProgress = true)
            val result = driveBackupManager.importFromUri(uri, passphrase.toCharArray())
            _uiState.value = _uiState.value.copy(
                backupInProgress = false,
                toastMessage = result.fold(
                    onSuccess = { "Backup restored. Close and reopen Nemory now." },
                    onFailure = { it.message ?: "Restore failed" }
                )
            )
        }
    }

    fun preparePrivateAi() {
        if (_uiState.value.privateAiStatus !in setOf(
                PrivateAiStatus.DOWNLOADABLE,
                PrivateAiStatus.ERROR,
                PrivateAiStatus.UNKNOWN
            )
        ) return
        viewModelScope.launch {
            aiCapabilityManager.downloadModel().collect { capabilities ->
                _uiState.value = _uiState.value.copy(privateAiStatus = capabilities.modelStatus)
            }
        }
    }

    fun revokeCloudCategory(category: Classification) {
        cloudConsentStore.revoke(category)
    }

    fun downloadGemmaModel() {
        gemmaModelManager.downloadModel()
    }

    fun deleteGemmaModel() {
        gemmaModelManager.deleteModel()
    }

    fun reprocessAllWithGemma() {
        if (gemmaModelManager.status.value != GemmaModelStatus.READY) return
        viewModelScope.launch {
            localAiAdoptionManager.reprocessAllItems()
            LlmEnrichmentWorker.enqueue(context, replaceCurrent = true)
        }
    }

    fun clearCloudApprovals() {
        cloudConsentStore.clear()
    }

    fun refreshRemoveAdsOffer() {
        if (_uiState.value.adEntitlement == AdEntitlement.AD_FREE) return
        viewModelScope.launch {
            val details = runCatching { billingManager.refreshProductDetails() }.getOrNull()
            _uiState.value = _uiState.value.copy(
                removeAdsPrice = details?.oneTimePurchaseOfferDetails?.formattedPrice
            )
        }
    }

    fun purchaseRemoveAds(activity: Activity) {
        if (_uiState.value.billingInProgress) return
        if (_uiState.value.adEntitlement == AdEntitlement.AD_FREE) {
            _uiState.value = _uiState.value.copy(billingNotice = BillingNotice.ALREADY_OWNED)
            return
        }
        _uiState.value = _uiState.value.copy(billingInProgress = true, billingNotice = null)
        viewModelScope.launch {
            val result = runCatching { billingManager.purchaseRemoveAds(activity) }.getOrNull()
            val notice = when (result?.responseCode) {
                BillingClient.BillingResponseCode.OK,
                BillingClient.BillingResponseCode.USER_CANCELED -> null
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    featureGate.syncEntitlementWithBilling()
                    BillingNotice.ALREADY_OWNED
                }
                else -> BillingNotice.PURCHASE_UNAVAILABLE
            }
            _uiState.value = _uiState.value.copy(
                billingInProgress = false,
                billingNotice = notice
            )
        }
    }

    fun clearBillingNotice() {
        _uiState.value = _uiState.value.copy(billingNotice = null)
    }
}

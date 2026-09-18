package com.vaultbrain.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.core.ai.llm.gemma.GemmaDownloadEligibility
import com.vaultbrain.core.ai.llm.gemma.GemmaModelManager
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the optional on-device-model onboarding step. The step is offered only on devices
 * where [GemmaDownloadEligibility] says the download makes sense; the model status flow
 * drives the in-page download progress and success states.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val gemmaDownloadEligibility: GemmaDownloadEligibility,
    private val gemmaModelManager: GemmaModelManager
) : ViewModel() {

    private val _offerModelDownload = MutableStateFlow(false)
    val offerModelDownload: StateFlow<Boolean> = _offerModelDownload.asStateFlow()

    val gemmaStatus: StateFlow<OnDeviceModelStatus> = gemmaModelManager.status

    init {
        viewModelScope.launch {
            _offerModelDownload.value = runCatching { gemmaDownloadEligibility.isPromptEligible() }
                .getOrDefault(false)
        }
    }

    fun downloadModel() {
        gemmaModelManager.downloadModel()
    }
}

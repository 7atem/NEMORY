package com.vaultbrain.core.ai.llm.gemma

import com.vaultbrain.core.ai.llm.AiCapabilityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single place that decides whether Nemory should offer the Gemma Tier 2 model download.
 *
 * Eligible means: the device supports the model ([GemmaModelManager.isSupported]), the model
 * file is not on the device yet (status NOT_DOWNLOADED after a fresh check), and Gemini
 * Nano/AICore is not READY. Used by the app-start notification prompt, the onboarding step,
 * and the Today-tab banner so the RAM/AICore checks are never duplicated.
 */
@Singleton
class GemmaDownloadEligibility @Inject constructor(
    private val aiCapabilityManager: AiCapabilityManager,
    private val gemmaModelManager: GemmaModelManager
) {
    suspend fun isPromptEligible(): Boolean {
        if (!gemmaModelManager.isSupported()) return false
        val nanoStatus = runCatching { aiCapabilityManager.refresh().modelStatus }
            .getOrElse { OnDeviceModelStatus.ERROR }
        if (nanoStatus == OnDeviceModelStatus.READY) return false
        withContext(Dispatchers.IO) { gemmaModelManager.refresh() }
        return gemmaModelManager.status.value == OnDeviceModelStatus.NOT_DOWNLOADED
    }
}

package com.vaultbrain.core.ai.llm

import com.vaultbrain.core.ai.llm.di.Cloud
import com.vaultbrain.shared.model.VaultItem
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

data class HybridAiRequest(
    val item: VaultItem,
    /** Every additional vault record whose content is included in this request. */
    val additionalItems: List<VaultItem> = emptyList(),
    val operation: CloudAiOperation,
    /** Prompt used only by the on-device client. */
    val prompt: String,
    /** Exact prompt sent across the cloud boundary after authorization. */
    val cloudPrompt: String = prompt,
    val sharedTextPreview: String,
    val privacyMode: AiPrivacyMode = AiPrivacyMode.ASK_BEFORE_CLOUD,
    val explicitConsent: Boolean = false
) {
    init {
        require(prompt.isNotBlank()) { "Prompt must not be blank" }
        require(cloudPrompt.isNotBlank()) { "Cloud prompt must not be blank" }
        require(sharedTextPreview.isNotBlank()) { "Shared text preview must not be blank" }
        require(sharedTextPreview.length <= MAX_SHARED_PREVIEW_CHARS) {
            "Shared text preview is too large"
        }
    }

    companion object {
        const val MAX_SHARED_PREVIEW_CHARS = 12_000
    }
}

data class CloudConsentDisclosure(
    val operation: CloudAiOperation,
    val provider: String,
    val exactText: String,
    val canRememberForCategory: Boolean,
    val rememberableCategories: Set<com.vaultbrain.shared.model.Classification> = emptySet()
)

sealed interface HybridAiResult {
    data class Success(
        val text: String,
        val origin: AiResponseOrigin
    ) : HybridAiResult

    /** Local generation failed and policy requires a contextual user decision. */
    data class ConsentRequired(val disclosure: CloudConsentDisclosure) : HybridAiResult

    /** Policy forbids cloud use, including for sensitive and unresolved categories. */
    data object LocalOnlyUnavailable : HybridAiResult

    /** The user authorized an eligible request, but the optional runtime is not installed. */
    data object CloudNotConfigured : HybridAiResult

    /** A cloud request was attempted after consent but did not produce usable text. */
    data object CloudGenerationFailed : HybridAiResult
}

/**
 * Local-first orchestration with a fail-closed cloud boundary.
 *
 * This class deliberately avoids provider fallback modes: only [CloudAiRuntime.generate] can
 * cross the network boundary, and that call is reached only after [PrivacyEngine] authorizes it.
 */
@Singleton
class HybridAiCoordinator @Inject constructor(
    private val localClient: LlmClient,
    private val privacyEngine: PrivacyEngine,
    private val consentStore: CloudConsentStore,
    @param:Cloud private val cloudRuntime: CloudAiRuntime
) {
    suspend fun generate(request: HybridAiRequest): HybridAiResult {
        if (localClient.isAvailable()) {
            generateSafely { localClient.generate(request.prompt) }
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { return HybridAiResult.Success(it, AiResponseOrigin.ON_DEVICE_AI) }
        }

        val items = (listOf(request.item) + request.additionalItems).distinctBy(VaultItem::id)
        val decisions = items.map { item ->
            privacyEngine.decide(
                item = item,
                mode = request.privacyMode,
                explicitConsent = request.explicitConsent,
                categoryOptIn = consentStore.isOptedIn(item.effectiveClassification)
            )
        }

        return when {
            CloudDecision.LOCAL_ONLY in decisions -> HybridAiResult.LocalOnlyUnavailable
            CloudDecision.REQUIRES_CONSENT in decisions -> {
                if (cloudRuntime.status != CloudRuntimeStatus.READY) {
                    HybridAiResult.LocalOnlyUnavailable
                } else {
                    HybridAiResult.ConsentRequired(
                        CloudConsentDisclosure(
                            operation = request.operation,
                            provider = CLOUD_PROVIDER_LABEL,
                            exactText = request.sharedTextPreview,
                            canRememberForCategory = items.all {
                                privacyEngine.ruleFor(it) == CloudProcessingRule.ALLOWED_IF_OPTED_IN
                            },
                            rememberableCategories = items
                                .mapNotNull(VaultItem::effectiveClassification)
                                .filter(privacyEngine::isRememberableCategory)
                                .toSet()
                        )
                    )
                }
            }
            else -> {
                if (cloudRuntime.status != CloudRuntimeStatus.READY) {
                    HybridAiResult.CloudNotConfigured
                } else {
                    generateSafely {
                        cloudRuntime.generate(
                            CloudAiRequest(
                                operation = request.operation,
                                prompt = request.cloudPrompt
                            )
                        )
                    }
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?.let { HybridAiResult.Success(it, AiResponseOrigin.CLOUD_AI) }
                        ?: HybridAiResult.CloudGenerationFailed
                }
            }
        }
    }

    suspend fun generateStream(request: HybridAiRequest): kotlinx.coroutines.flow.Flow<HybridAiResult> = kotlinx.coroutines.flow.flow {
        if (localClient.isAvailable()) {
            var accumulatedText = ""
            var emittedAnyChunk = false
            try {
                localClient.generateStream(request.prompt).collect { chunk ->
                    accumulatedText += chunk
                    emittedAnyChunk = true
                    emit(HybridAiResult.Success(accumulatedText, AiResponseOrigin.ON_DEVICE_AI))
                }
                if (emittedAnyChunk && accumulatedText.isNotBlank()) {
                    return@flow
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                android.util.Log.w("HybridAiCoordinator", "Local streaming generation failed, falling back", t)
            }
        }
        emit(generate(request))
    }

    private suspend fun generateSafely(block: suspend () -> String?): String? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val CLOUD_PROVIDER_LABEL = "Google Gemini via Firebase AI Logic"
    }
}

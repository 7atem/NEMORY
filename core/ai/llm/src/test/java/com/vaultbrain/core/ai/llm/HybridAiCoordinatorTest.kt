package com.vaultbrain.core.ai.llm

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.core.common.model.VaultItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HybridAiCoordinatorTest {

    @Test
    fun `successful local response never contacts cloud`() = runTest {
        val cloud = FakeCloudRuntime(CloudRuntimeStatus.READY, "cloud")
        val coordinator = coordinator(localResponse = " local ", cloud = cloud)

        val result = coordinator.generate(request(item(Classification.BOOK)))

        assertThat(result).isEqualTo(
            HybridAiResult.Success("local", AiResponseOrigin.ON_DEVICE_AI)
        )
        assertThat(cloud.requestCount).isEqualTo(0)
    }

    @Test
    fun `eligible media asks for contextual consent and exposes exact text`() = runTest {
        val cloud = FakeCloudRuntime(CloudRuntimeStatus.READY, "cloud")
        val result = coordinator(cloud = cloud).generate(
            request(item(Classification.MOVIE), preview = "A movie review")
        )

        assertThat(result).isInstanceOf(HybridAiResult.ConsentRequired::class.java)
        val disclosure = (result as HybridAiResult.ConsentRequired).disclosure
        assertThat(disclosure.exactText).isEqualTo("A movie review")
        assertThat(disclosure.canRememberForCategory).isTrue()
        assertThat(disclosure.rememberableCategories).containsExactly(Classification.MOVIE)
        assertThat(cloud.requestCount).isEqualTo(0)
    }

    @Test
    fun `remembered media category can skip repeat consent only in smart hybrid mode`() = runTest {
        val cloud = FakeCloudRuntime(CloudRuntimeStatus.READY, "remembered answer")
        val coordinator = coordinator(
            cloud = cloud,
            optedInCategories = setOf(Classification.BOOK)
        )

        val result = coordinator.generate(
            request(
                item = item(Classification.BOOK),
                privacyMode = AiPrivacyMode.SMART_HYBRID
            )
        )

        assertThat(result).isEqualTo(
            HybridAiResult.Success("remembered answer", AiResponseOrigin.CLOUD_AI)
        )
        assertThat(cloud.requestCount).isEqualTo(1)
    }

    @Test
    fun `explicit consent sends eligible request once`() = runTest {
        val cloud = FakeCloudRuntime(CloudRuntimeStatus.READY, " translated ")
        val result = coordinator(cloud = cloud).generate(
            request(item(Classification.BOOK), explicitConsent = true)
        )

        assertThat(result).isEqualTo(
            HybridAiResult.Success("translated", AiResponseOrigin.CLOUD_AI)
        )
        assertThat(cloud.requestCount).isEqualTo(1)
    }

    @Test
    fun `cloud receives its disclosed bounded prompt instead of local prompt`() = runTest {
        val cloud = FakeCloudRuntime(CloudRuntimeStatus.READY, "answer")
        val coordinator = coordinator(cloud = cloud)

        coordinator.generate(
            request(item(Classification.BOOK), explicitConsent = true).copy(
                prompt = "Local-only system prompt",
                cloudPrompt = "Exact disclosed cloud prompt",
                sharedTextPreview = "Exact disclosed cloud prompt"
            )
        )

        assertThat(cloud.lastRequest?.prompt).isEqualTo("Exact disclosed cloud prompt")
    }

    @Test
    fun `one sensitive supporting item vetoes a multi-record cloud request`() = runTest {
        val cloud = FakeCloudRuntime(CloudRuntimeStatus.READY, "must not be used")
        val result = coordinator(cloud = cloud).generate(
            request(item(Classification.BOOK), explicitConsent = true).copy(
                additionalItems = listOf(item(Classification.PASSPORT).copy(id = "passport-2"))
            )
        )

        assertThat(result).isEqualTo(HybridAiResult.LocalOnlyUnavailable)
        assertThat(cloud.requestCount).isEqualTo(0)
    }

    @Test
    fun `sensitive item stays local even after explicit consent`() = runTest {
        val cloud = FakeCloudRuntime(CloudRuntimeStatus.READY, "must not be used")
        val result = coordinator(cloud = cloud).generate(
            request(item(Classification.PASSPORT), explicitConsent = true)
        )

        assertThat(result).isEqualTo(HybridAiResult.LocalOnlyUnavailable)
        assertThat(cloud.requestCount).isEqualTo(0)
    }

    @Test
    fun `unknown item stays local even after explicit consent`() = runTest {
        val cloud = FakeCloudRuntime(CloudRuntimeStatus.READY, "must not be used")
        val result = coordinator(cloud = cloud).generate(
            request(item(Classification.UNKNOWN), explicitConsent = true)
        )

        assertThat(result).isEqualTo(HybridAiResult.LocalOnlyUnavailable)
        assertThat(cloud.requestCount).isEqualTo(0)
    }

    @Test
    fun `private mode never contacts cloud`() = runTest {
        val cloud = FakeCloudRuntime(CloudRuntimeStatus.READY, "must not be used")
        val result = coordinator(cloud = cloud).generate(
            request(
                item = item(Classification.BOOK),
                explicitConsent = true,
                privacyMode = AiPrivacyMode.PRIVATE
            )
        )

        assertThat(result).isEqualTo(HybridAiResult.LocalOnlyUnavailable)
        assertThat(cloud.requestCount).isEqualTo(0)
    }

    @Test
    fun `authorized request reports missing cloud configuration`() = runTest {
        val cloud = FakeCloudRuntime(CloudRuntimeStatus.NOT_CONFIGURED, "unused")
        val result = coordinator(cloud = cloud).generate(
            request(item(Classification.BOOK), explicitConsent = true)
        )

        assertThat(result).isEqualTo(HybridAiResult.CloudNotConfigured)
        assertThat(cloud.requestCount).isEqualTo(0)
    }

    @Test
    fun `unconfigured runtime does not show a dead consent action`() = runTest {
        val cloud = FakeCloudRuntime(CloudRuntimeStatus.NOT_CONFIGURED, "unused")
        val result = coordinator(cloud = cloud).generate(request(item(Classification.BOOK)))

        assertThat(result).isEqualTo(HybridAiResult.LocalOnlyUnavailable)
        assertThat(cloud.requestCount).isEqualTo(0)
    }

    @Test
    fun `failed authorized cloud request still records that cloud was contacted`() = runTest {
        val cloud = FakeCloudRuntime(CloudRuntimeStatus.READY, null)

        val result = coordinator(cloud = cloud).generate(
            request(item(Classification.BOOK), explicitConsent = true)
        )

        assertThat(result).isEqualTo(HybridAiResult.CloudGenerationFailed)
        assertThat(cloud.requestCount).isEqualTo(1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank disclosure text is rejected`() {
        request(item(Classification.BOOK), preview = " ")
    }

    private fun coordinator(
        localResponse: String? = null,
        cloud: FakeCloudRuntime,
        optedInCategories: Set<Classification> = emptySet()
    ) = HybridAiCoordinator(
        localClient = FakeLlmClient(localResponse),
        privacyEngine = PrivacyEngine(),
        consentStore = FakeCloudConsentStore(optedInCategories),
        cloudRuntime = cloud
    )

    private fun request(
        item: VaultItem,
        preview: String = "Text visible to the user",
        explicitConsent: Boolean = false,
        privacyMode: AiPrivacyMode = AiPrivacyMode.ASK_BEFORE_CLOUD
    ) = HybridAiRequest(
        item = item,
        operation = CloudAiOperation.TRANSLATE_DOCUMENT,
        prompt = "Translate: $preview",
        sharedTextPreview = preview,
        privacyMode = privacyMode,
        explicitConsent = explicitConsent
    )

    private fun item(classification: Classification) = VaultItem(
        id = "item-1",
        title = "Saved item",
        aiClassification = classification,
        aiConfidence = 0.9f,
        lensTags = if (classification in setOf(
                Classification.MOVIE,
                Classification.TV_SERIES,
                Classification.BOOK
            )) setOf(LensId.MEDIA) else emptySet()
    )
}

private class FakeCloudConsentStore(initial: Set<Classification>) : CloudConsentStore {
    private val categories = MutableStateFlow(initial)
    override val optedInCategories: StateFlow<Set<Classification>> = categories

    override fun isOptedIn(classification: Classification?): Boolean =
        classification != null && classification in categories.value

    override fun remember(categories: Set<Classification>) {
        this.categories.value += categories
    }

    override fun revoke(category: Classification) {
        categories.value -= category
    }

    override fun clear() {
        categories.value = emptySet()
    }
}

private class FakeLlmClient(private val response: String?) : LlmClient {
    override suspend fun generate(prompt: String, creative: Boolean): String? = response
    override fun generateStream(prompt: String, creative: Boolean): Flow<String> = emptyFlow()
    override fun isAvailable(): Boolean = response != null
}

private class FakeCloudRuntime(
    override val status: CloudRuntimeStatus,
    private val response: String?
) : CloudAiRuntime {
    var requestCount: Int = 0
        private set
    var lastRequest: CloudAiRequest? = null
        private set

    override suspend fun generate(request: CloudAiRequest): String? {
        requestCount += 1
        lastRequest = request
        return response
    }
}

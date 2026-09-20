package com.vaultbrain.feature.brain

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.rag.RagEngine
import com.vaultbrain.core.ai.rag.RagEvidence
import com.vaultbrain.core.ai.rag.RagEvidenceKind
import com.vaultbrain.core.ai.rag.RagResponse
import com.vaultbrain.core.ai.rag.SearchFilters
import com.vaultbrain.core.ai.rag.RagCloudFailure
import com.vaultbrain.core.ai.llm.AiResponseOrigin
import com.vaultbrain.core.ai.llm.CloudAiOperation
import com.vaultbrain.core.ai.llm.CloudConsentDisclosure
import com.vaultbrain.core.ai.llm.CloudConsentStore
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.ai.llm.AiCapabilityManager
import com.vaultbrain.core.ai.llm.TokenBudget
import com.vaultbrain.core.database.repository.BrainConversationRepository
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.feature.brain.model.ChatMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BrainChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val ragEngine = mockk<RagEngine>()
    private val conversationRepository = mockk<BrainConversationRepository>()
    private val memoryBuilder = ConversationMemoryBuilder(TokenBudget())
    private val vaultRepository = mockk<VaultRepository>()
    private val suggestionProvider = BrainSuggestionProvider()
    private val cloudConsentStore = mockk<CloudConsentStore>(relaxed = true)
    private val writeActionPlanner = mockk<BrainWriteActionPlanner>()
    private val capabilityManager = mockk<AiCapabilityManager>(relaxed = true)
    private val vaultReminderManager = mockk<com.vaultbrain.core.notifications.VaultReminderManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { conversationRepository.observeAll() } returns flowOf(emptyList())
        every { vaultRepository.observeActive() } returns flowOf(emptyList())
        every { vaultRepository.observeActiveCollections() } returns flowOf(emptyList())
        every { capabilityManager.capabilities } returns kotlinx.coroutines.flow.MutableStateFlow(com.vaultbrain.core.ai.llm.AiDeviceCapabilities())
        coEvery { conversationRepository.upsert(any()) } just Runs
        coEvery { conversationRepository.clear() } just Runs
        coEvery { conversationRepository.deleteByIds(any()) } just Runs
        coEvery { writeActionPlanner.plan(any(), any(), any()) } returns
            BrainWritePlanResult.NotWriteAction
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty result gives useful guidance instead of blank answer`() = runTest(dispatcher) {
        coEvery { ragEngine.queryStream("passport", SearchFilters(), null, false) } returns flowOf(
            RagResponse(answer = null, sources = emptyList(), confidence = 0f)
        )
        val viewModel = viewModel()

        viewModel.sendQuery("passport")
        advanceUntilIdle()

        assertThat(viewModel.messages.value).hasSize(2)
        val answer = viewModel.messages.value.last() as ChatMessage.Assistant
        assertThat(answer.text).contains("couldn't find")
        assertThat(answer.isError).isFalse()
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `search failure is retryable and keeps original query`() = runTest(dispatcher) {
        coEvery { ragEngine.queryStream("car warranty", SearchFilters(), null, false) } returns flow {
            throw IllegalStateException("Search unavailable")
        }
        val viewModel = viewModel()

        viewModel.sendQuery("car warranty")
        advanceUntilIdle()

        val answer = viewModel.messages.value.last() as ChatMessage.Assistant
        assertThat(answer.isError).isTrue()
        assertThat(answer.originalQuery).isEqualTo("car warranty")
        assertThat(answer.text).contains("Search unavailable")
        assertThat(viewModel.isLoading.value).isFalse()
    }

    @Test
    fun `concurrent queries are ignored while loading`() = runTest(dispatcher) {
        val flow1 = kotlinx.coroutines.flow.MutableStateFlow(RagResponse(answer = "First"))
        val flow2 = kotlinx.coroutines.flow.MutableStateFlow(RagResponse(answer = "Second"))

        var callCount = 0
        coEvery { ragEngine.queryStream(any(), any(), any(), any()) } answers {
            if (callCount++ == 0) flow1 else flow2
        }

        val viewModel = viewModel()
        viewModel.sendQuery("query 1")

        // At this point _isLoading is true, so sendQuery("query 2") should be ignored
        viewModel.sendQuery("query 2")
        advanceUntilIdle()

        val messages = viewModel.messages.value.filterIsInstance<ChatMessage.Assistant>()
        assertThat(messages.last().text).isEqualTo("First")
    }

    @Test
    fun `clear conversation removes messages and draft`() = runTest(dispatcher) {
        coEvery { ragEngine.queryStream("receipt", SearchFilters(), null, false) } returns flowOf(
            RagResponse(answer = "Found it", sources = emptyList(), confidence = 0.7f)
        )
        val viewModel = viewModel()
        viewModel.onInputChanged("draft")
        viewModel.sendQuery("receipt")
        advanceUntilIdle()

        viewModel.clearConversation()
        advanceUntilIdle()

        assertThat(viewModel.messages.value).isEmpty()
        assertThat(viewModel.input.value).isEmpty()
        coVerify { conversationRepository.clear() }
    }

    @Test
    fun `structured evidence is shown and persisted with the assistant turn`() = runTest(dispatcher) {
        coEvery { ragEngine.queryStream("How much did I spend?", SearchFilters(), null, false) } returns flowOf(
            RagResponse(
                answer = "Calculated locally.",
                evidence = RagEvidence(RagEvidenceKind.TOTAL, "EGP 200", listOf("2 items"))
            )
        )
        val viewModel = viewModel()

        viewModel.sendQuery("How much did I spend?")
        advanceUntilIdle()

        val answer = viewModel.messages.value.last() as ChatMessage.Assistant
        assertThat(answer.evidence?.headline).isEqualTo("EGP 200")
        coVerify {
            conversationRepository.upsert(match {
                it.role == BrainConversationRepository.ROLE_ASSISTANT &&
                    it.evidenceKind == RagEvidenceKind.TOTAL.name &&
                    it.evidenceHeadline == "EGP 200" &&
                    it.evidenceFacts == listOf("2 items")
            })
        }
    }

    @Test
    fun `cloud consent retries the same assistant turn once without duplicating user message`() =
        runTest(dispatcher) {
            val disclosure = CloudConsentDisclosure(
                operation = CloudAiOperation.EXPLAIN_RETRIEVED_EVIDENCE,
                provider = "Cloud provider",
                exactText = "Exact request",
                canRememberForCategory = true,
                rememberableCategories = setOf(Classification.BOOK)
            )
            coEvery { ragEngine.queryStream("Explain this item", SearchFilters(), null, false) } returns flowOf(
                RagResponse(answer = "Local fallback", cloudConsent = disclosure)
            )
            coEvery { ragEngine.queryStream("Explain this item", SearchFilters(), null, true) } returns flowOf(
                RagResponse(
                    answer = "Approved cloud answer",
                    responseOrigin = AiResponseOrigin.CLOUD_AI
                )
            )
            val viewModel = viewModel()

            viewModel.sendQuery("Explain this item")
            advanceUntilIdle()

            assertThat(viewModel.cloudConsent.value?.disclosure).isEqualTo(disclosure)
            assertThat(viewModel.messages.value).hasSize(2)

            viewModel.confirmCloudAssist(rememberForCategories = true)
            advanceUntilIdle()

            assertThat(viewModel.messages.value).hasSize(2)
            val answer = viewModel.messages.value.last() as ChatMessage.Assistant
            assertThat(answer.text).isEqualTo("Approved cloud answer")
            assertThat(answer.responseOrigin).isEqualTo(AiResponseOrigin.CLOUD_AI)
            assertThat(viewModel.cloudConsent.value).isNull()
            coVerify(exactly = 1) { ragEngine.queryStream("Explain this item", SearchFilters(), null, true) }
            coVerify(exactly = 1) {
                cloudConsentStore.remember(setOf(Classification.BOOK))
            }
            coVerify {
                conversationRepository.upsert(match {
                    it.role == BrainConversationRepository.ROLE_ASSISTANT &&
                        it.responseOrigin == AiResponseOrigin.CLOUD_AI.name
                })
            }
        }

    @Test
    fun `failed approved cloud attempt keeps local result and shows calm status`() =
        runTest(dispatcher) {
            coEvery { ragEngine.queryStream("Explain this item", SearchFilters(), null, false) } returns flowOf(
                RagResponse(
                    answer = "Local fallback",
                    cloudFailure = RagCloudFailure.NOT_CONFIGURED
                )
            )
            val viewModel = viewModel()

            viewModel.sendQuery("Explain this item")
            advanceUntilIdle()

            val answer = viewModel.messages.value.last() as ChatMessage.Assistant
            assertThat(answer.text).isEqualTo("Local fallback")
            assertThat(viewModel.cloudMessage.value).isEqualTo(BrainCloudMessage.NOT_CONFIGURED)
        }

    @Test
    fun `write preview never executes until user confirms`() = runTest(dispatcher) {
        val item = VaultItem(id = "dune", title = "Dune", updatedAt = 100L)
        val preview = BrainWritePreview(
            item = item,
            expectedUpdatedAt = item.updatedAt,
            action = BrainWriteAction.Pin,
            originalQuery = "Pin Dune"
        )
        coEvery { writeActionPlanner.plan("Pin Dune", any(), any()) } returns
            BrainWritePlanResult.Ready(preview)
        coEvery { writeActionPlanner.execute(preview, any()) } returns
            BrainWriteExecutionResult.Success(item.copy(isPinned = true, updatedAt = 200L))
        val viewModel = viewModel()

        viewModel.sendQuery("Pin Dune")
        advanceUntilIdle()

        assertThat(viewModel.pendingWrite.value?.preview).isEqualTo(preview)
        assertThat(viewModel.messages.value).hasSize(2)
        coVerify(exactly = 0) { writeActionPlanner.execute(any(), any()) }

        viewModel.confirmWriteAction()
        advanceUntilIdle()

        assertThat(viewModel.pendingWrite.value).isNull()
        assertThat(viewModel.messages.value).hasSize(2)
        val answer = viewModel.messages.value.last() as ChatMessage.Assistant
        assertThat(answer.text).contains("Pinned")
        coVerify(exactly = 1) { writeActionPlanner.execute(preview, any()) }
    }

    @Test
    fun `dismissing write preview makes no change`() = runTest(dispatcher) {
        val item = VaultItem(id = "dune", title = "Dune", updatedAt = 100L)
        val preview = BrainWritePreview(
            item = item,
            expectedUpdatedAt = item.updatedAt,
            action = BrainWriteAction.Pin,
            originalQuery = "Pin Dune"
        )
        coEvery { writeActionPlanner.plan("Pin Dune", any(), any()) } returns
            BrainWritePlanResult.Ready(preview)
        val viewModel = viewModel()

        viewModel.sendQuery("Pin Dune")
        advanceUntilIdle()
        viewModel.dismissWriteAction()
        advanceUntilIdle()

        assertThat(viewModel.pendingWrite.value).isNull()
        val answer = viewModel.messages.value.last() as ChatMessage.Assistant
        assertThat(answer.text).isEqualTo("No changes were made.")
        coVerify(exactly = 0) { writeActionPlanner.execute(any(), any()) }
    }

    @Test
    fun `collection creation executes successfully when confirmed`() = runTest(dispatcher) {
        val collection = com.vaultbrain.shared.model.PersonalCollection(
            id = "collection-id",
            name = "Test Collection",
            createdAt = 100L,
            updatedAt = 100L,
            source = com.vaultbrain.shared.model.PersonalCollectionSource.USER
        )
        val items = listOf(VaultItem(id = "item1", title = "Item 1"))

        coEvery { vaultRepository.createCollection("Test Collection", any()) } returns collection
        coEvery { vaultRepository.addItemToCollection("item1", "collection-id") } returns true

        val viewModel = viewModel()

        val field = BrainChatViewModel::class.java.getDeclaredField("_pendingCollectionCreation")
        field.isAccessible = true
        val flow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<BrainCollectionCreationPreview?>
        flow.value = BrainCollectionCreationPreview(
            name = "Test Collection",
            items = items,
            originalQuery = "Make a collection",
            assistantMessageId = "msg-id"
        )

        viewModel.confirmCollectionCreation()
        advanceUntilIdle()

        assertThat(viewModel.pendingCollectionCreation.value).isNull()
        coVerify(exactly = 1) { vaultRepository.createCollection("Test Collection", any()) }
        coVerify(exactly = 1) { vaultRepository.addItemToCollection("item1", "collection-id") }
    }

    private val actionExecutor = io.mockk.mockk<com.vaultbrain.core.integrations.action.ActionExecutor>(relaxed = true)

    private fun viewModel() = BrainChatViewModel(
        ragEngine = ragEngine,
        conversationRepository = conversationRepository,
        memoryBuilder = memoryBuilder,
        vaultRepository = vaultRepository,
        suggestionProvider = suggestionProvider,
        cloudConsentStore = cloudConsentStore,
        writeActionPlanner = writeActionPlanner,
        capabilityManager = capabilityManager,
        vaultReminderManager = vaultReminderManager,
        actionExecutor = actionExecutor
    )
}


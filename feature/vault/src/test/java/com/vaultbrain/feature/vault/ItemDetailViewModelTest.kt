package com.vaultbrain.feature.vault

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.llm.AiResponseOrigin
import com.vaultbrain.core.ai.llm.CloudConsentStore
import com.vaultbrain.core.ai.llm.HybridAiCoordinator
import com.vaultbrain.core.ai.llm.HybridAiResult
import com.vaultbrain.shared.model.PersonalCollection
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.notifications.UnifiedAlertManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
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
class ItemDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<VaultRepository>()
    private val alertManager = mockk<UnifiedAlertManager>()
    private val coordinator = mockk<HybridAiCoordinator>()
    private val consentStore = mockk<CloudConsentStore>(relaxed = true)
    private val vaultReminderManager = mockk<com.vaultbrain.core.notifications.VaultReminderManager>(relaxed = true)
    private val actionExecutor = mockk<com.vaultbrain.core.integrations.action.ActionExecutor>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.observeSuggestionsForItem(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `failed cloud attempt never relabels retained local translation`() = runTest(dispatcher) {
        val item = VaultItem(id = "book", title = "The Little Prince")
        coEvery { repository.getById(item.id) } returns item
        every { repository.observeActiveCollections() } returns flowOf(emptyList())
        every { repository.observeCollectionsForItem(item.id) } returns flowOf(emptyList())
        coEvery { coordinator.generate(any()) } returnsMany listOf(
            HybridAiResult.Success("الأمير الصغير", AiResponseOrigin.ON_DEVICE_AI),
            HybridAiResult.CloudGenerationFailed
        )
        val viewModel = ItemDetailViewModel(repository, alertManager, coordinator, consentStore, vaultReminderManager, actionExecutor)
        viewModel.loadItem(item.id)
        advanceUntilIdle()

        viewModel.translate("Arabic")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.translationOrigin)
            .isEqualTo(AiResponseOrigin.ON_DEVICE_AI)

        viewModel.translate("Arabic", explicitCloudConsent = true)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.translation).isEqualTo("الأمير الصغير")
        assertThat(viewModel.uiState.value.translationOrigin)
            .isEqualTo(AiResponseOrigin.ON_DEVICE_AI)
        assertThat(viewModel.uiState.value.translationMessage)
            .isEqualTo(TranslationMessage.GENERATION_FAILED)
    }

    @Test
    fun `item detail surfaces the item's personal collections`() = runTest(dispatcher) {
        val item = VaultItem(id = "receipt", title = "Printer receipt")
        val member = PersonalCollection(id = "c1", name = "Bambu H2D", createdAt = 1L, updatedAt = 1L)
        val other = PersonalCollection(id = "c2", name = "Things to Buy", createdAt = 1L, updatedAt = 1L)
        coEvery { repository.getById(item.id) } returns item
        every { repository.observeActiveCollections() } returns flowOf(listOf(member, other))
        every { repository.observeCollectionsForItem(item.id) } returns flowOf(listOf(member))
        val viewModel = ItemDetailViewModel(repository, alertManager, coordinator, consentStore, vaultReminderManager, actionExecutor)
        viewModel.loadItem(item.id)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.itemCollections.map { it.name })
            .containsExactly("Bambu H2D")
        assertThat(viewModel.uiState.value.activeCollections.map { it.name })
            .containsExactly("Bambu H2D", "Things to Buy")
    }

    @Test
    fun `add and remove collection membership delegate to repository`() = runTest(dispatcher) {
        val item = VaultItem(id = "receipt", title = "Printer receipt")
        coEvery { repository.getById(item.id) } returns item
        every { repository.observeActiveCollections() } returns flowOf(emptyList())
        every { repository.observeCollectionsForItem(item.id) } returns flowOf(emptyList())
        coEvery { repository.addItemToCollection(item.id, "c1") } returns true
        coEvery { repository.removeItemFromCollection(item.id, "c2") } returns true
        val viewModel = ItemDetailViewModel(repository, alertManager, coordinator, consentStore, vaultReminderManager, actionExecutor)
        viewModel.loadItem(item.id)
        advanceUntilIdle()

        viewModel.setCollectionMembership("c1", selected = true)
        viewModel.setCollectionMembership("c2", selected = false)
        advanceUntilIdle()

        coVerify { repository.addItemToCollection(item.id, "c1") }
        coVerify { repository.removeItemFromCollection(item.id, "c2") }
    }
}

package com.vaultbrain.feature.vault

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.llm.gemma.GemmaDownloadEligibility
import com.vaultbrain.core.ai.llm.gemma.GemmaModelManager
import com.vaultbrain.core.ai.llm.gemma.GemmaModelStatus
import com.vaultbrain.core.billing.AdEntitlement
import com.vaultbrain.core.billing.FeatureGate
import com.vaultbrain.core.common.model.PersonalCollection
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.notifications.GemmaBannerStore
import com.vaultbrain.core.notifications.VaultReminderManager
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<VaultRepository>()
    private val featureGate = mockk<FeatureGate>()
    private val patternDetector = CapturePatternDetector()
    private val gemmaDownloadEligibility = mockk<GemmaDownloadEligibility>()
    private val gemmaModelManager = mockk<GemmaModelManager>()
    private val gemmaBannerStore = mockk<GemmaBannerStore>(relaxed = true)
    private val externalContextRepository = mockk<ExternalContextRepository>()
    private val vaultReminderManager = mockk<VaultReminderManager>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.observeRecent(any()) } returns flowOf(emptyList())
        every { repository.observeActive() } returns flowOf(emptyList())
        every { featureGate.entitlement } returns MutableStateFlow(AdEntitlement.AD_SUPPORTED)
        coEvery { gemmaDownloadEligibility.isPromptEligible() } returns false
        every { gemmaModelManager.status } returns MutableStateFlow(GemmaModelStatus.NOT_DOWNLOADED)
        every { externalContextRepository.observeRecordsBySource(any()) } returns flowOf(emptyList())
        every { vaultReminderManager.observeActive() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun collection(id: String, name: String) = PersonalCollection(
        id = id, name = name, createdAt = 1L, updatedAt = 1L
    )

    @Test
    fun `home works with zero collections`() = runTest(dispatcher) {
        every { repository.observeActiveCollections() } returns flowOf(emptyList())
        val viewModel = HomeViewModel(
            repository, featureGate, patternDetector,
            gemmaDownloadEligibility, gemmaModelManager, gemmaBannerStore,
            externalContextRepository, vaultReminderManager
        )
        val states = mutableListOf<HomeUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        assertThat(states.last().collections).isEmpty()
        assertThat(states.last().recentItems).isEmpty()
        job.cancel()
    }

    @Test
    fun `home exposes multiple personal collections`() = runTest(dispatcher) {
        val collections = listOf(
            collection("c1", "Bambu H2D"),
            collection("c2", "Europe Trip"),
            collection("c3", "PhD")
        )
        every { repository.observeActiveCollections() } returns flowOf(collections)
        every { repository.observeRecent(any()) } returns flowOf(
            listOf(VaultItem(id = "i1", title = "Receipt"))
        )
        val viewModel = HomeViewModel(
            repository, featureGate, patternDetector,
            gemmaDownloadEligibility, gemmaModelManager, gemmaBannerStore,
            externalContextRepository, vaultReminderManager
        )
        val states = mutableListOf<HomeUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        assertThat(states.last().collections.map { it.name })
            .containsExactly("Bambu H2D", "Europe Trip", "PhD")
        assertThat(states.last().recentItems).hasSize(1)
        job.cancel()
    }
}

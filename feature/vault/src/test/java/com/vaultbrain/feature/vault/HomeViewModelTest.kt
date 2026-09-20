package com.vaultbrain.feature.vault

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.llm.gemma.GemmaDownloadEligibility
import com.vaultbrain.core.ai.llm.gemma.GemmaModelManager
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelStatus
import com.vaultbrain.shared.model.PersonalCollection
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.notifications.GemmaBannerStore
import com.vaultbrain.core.notifications.VaultReminderManager
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
    private val patternDetector = CapturePatternDetector()
    private val gemmaDownloadEligibility = mockk<GemmaDownloadEligibility>()
    private val gemmaModelManager = mockk<GemmaModelManager>()
    private val gemmaBannerStore = mockk<GemmaBannerStore>(relaxed = true)
    private val externalContextRepository = mockk<ExternalContextRepository>()
    private val vaultReminderManager = mockk<VaultReminderManager>()
    private val backupNudgeStore = mockk<com.vaultbrain.core.notifications.BackupNudgeStore>(relaxed = true)
    private val actionExecutor = mockk<com.vaultbrain.core.integrations.action.ActionExecutor>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.observeRecent(any()) } returns flowOf(emptyList())
        every { repository.observeActive() } returns flowOf(emptyList())
        coEvery { gemmaDownloadEligibility.isPromptEligible() } returns false
        every { gemmaModelManager.status } returns MutableStateFlow(OnDeviceModelStatus.NOT_DOWNLOADED)
        every { externalContextRepository.observeRecordsBySource(any()) } returns flowOf(emptyList())
        every { externalContextRepository.observeRecords() } returns flowOf(emptyList())
        every { vaultReminderManager.observeActive() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        com.vaultbrain.core.common.security.DecoySessionState.setDecoyMode(false)
        Dispatchers.resetMain()
    }

    @Test
    fun `insights follow changed data and model readiness without waiting six hours`() = runTest(dispatcher) {
        every { repository.observeActiveCollections() } returns flowOf(emptyList())
        val items = MutableStateFlow(listOf(VaultItem(id = "1", title = "Old title")))
        every { repository.observeActive() } returns items
        val status = MutableStateFlow<OnDeviceModelStatus>(OnDeviceModelStatus.NOT_DOWNLOADED)
        every { gemmaModelManager.status } returns status
        val intelligence = mockk<com.vaultbrain.core.ai.rag.DailyIntelligence>()
        coEvery { intelligence.select(any(), any(), any()) } answers {
            firstArg<List<VaultItem>>().map {
                com.vaultbrain.core.ai.rag.DailyInsight(it.id, it.title, it.title, it.updatedAt)
            }
        }
        val viewModel = HomeViewModel(repository, patternDetector, gemmaDownloadEligibility,
            gemmaModelManager, gemmaBannerStore, externalContextRepository, vaultReminderManager,
            backupNudgeStore, actionExecutor, intelligence)
        advanceUntilIdle()
        assertThat(viewModel.dailyInsights.value.single().title).isEqualTo("Old title")
        items.value = listOf(items.value.single().copy(title = "Changed title"))
        advanceUntilIdle()
        assertThat(viewModel.dailyInsights.value.single().title).isEqualTo("Changed title")
        status.value = OnDeviceModelStatus.READY
        advanceUntilIdle()
        coVerify(exactly = 3) { intelligence.select(any(), any(), any()) }
        com.vaultbrain.core.common.security.DecoySessionState.setDecoyMode(true)
        advanceUntilIdle()
        assertThat(viewModel.dailyInsights.value).isEmpty()
        coVerify(exactly = 3) { intelligence.select(any(), any(), any()) }
    }

    private fun collection(id: String, name: String) = PersonalCollection(
        id = id, name = name, createdAt = 1L, updatedAt = 1L
    )

    @Test
    fun `home works with zero collections`() = runTest(dispatcher) {
        every { repository.observeActiveCollections() } returns flowOf(emptyList())
        val viewModel = HomeViewModel(
            repository, patternDetector,
            gemmaDownloadEligibility, gemmaModelManager, gemmaBannerStore,
            externalContextRepository, vaultReminderManager, backupNudgeStore, actionExecutor
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
            repository, patternDetector,
            gemmaDownloadEligibility, gemmaModelManager, gemmaBannerStore,
            externalContextRepository, vaultReminderManager, backupNudgeStore, actionExecutor
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

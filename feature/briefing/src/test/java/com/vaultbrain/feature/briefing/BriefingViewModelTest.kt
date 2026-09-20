package com.vaultbrain.feature.briefing

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import com.vaultbrain.core.notifications.VaultReminderManager
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
class BriefingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<VaultRepository>()
    private val externalContextRepository = mockk<ExternalContextRepository>()
    private val vaultReminderManager = mockk<VaultReminderManager>()
    private val explanationGenerator = mockk<BriefingExplanationGenerator>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `publishes deterministic cards without performing vault writes`() = runTest(dispatcher) {
        val now = System.currentTimeMillis()
        every { repository.observeActive() } returns flowOf(
            listOf(
                VaultItem(
                    id = "passport",
                    title = "Passport",
                    sourceType = SourceType.MANUAL,
                    aiClassification = Classification.PASSPORT,
                    expiryDate = now + 5L * 24 * 60 * 60 * 1000
                )
            )
        )
        every { externalContextRepository.observeRecords() } returns flowOf(emptyList())
        every { vaultReminderManager.observeActive() } returns flowOf(emptyList())
        coEvery { explanationGenerator.explain(any(), any()) } returns null
        coEvery { explanationGenerator.generateDailySummary(any(), any(), any(), any()) } returns null

        val viewModel = BriefingViewModel(
            repository = repository,
            externalContextRepository = externalContextRepository,
            vaultReminderManager = vaultReminderManager,
            deterministicInsightEngine = DeterministicInsightEngine(),
            digestEngine = DigestEngine(DeterministicInsightEngine(), com.vaultbrain.feature.brain.worker.RadarEngine()),
            explanationGenerator = explanationGenerator
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.insights).hasSize(1)
        assertThat(viewModel.uiState.value.insights.single())
            .isInstanceOf(TodayInsight.Expiring::class.java)
        coVerify(exactly = 0) { repository.save(any()) }
        coVerify(exactly = 0) { repository.delete(any()) }
    }
}

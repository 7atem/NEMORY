package com.vaultbrain.feature.vault

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.rag.QueryIntentParser
import com.vaultbrain.shared.model.PersonalCollection
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
class VaultBrowserViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<VaultRepository>()
    private val intentParser = QueryIntentParser()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun collection(id: String, name: String, itemCount: Int = 0) = PersonalCollection(
        id = id, name = name, createdAt = 1L, updatedAt = 1L, itemCount = itemCount
    )

    @Test
    fun `browser filters expose no fixed lens taxonomy`() {
        assertThat(BrowserFilter.entries.map { it.name })
            .containsExactly("ALL", "PINNED", "EXPIRING", "NEEDS_REVIEW")
    }

    @Test
    fun `browser works with zero collections`() = runTest(dispatcher) {
        every { repository.observeActive() } returns flowOf(listOf(VaultItem(id = "i1", title = "Note")))
        every { repository.observeActiveCollections() } returns flowOf(emptyList())
        val viewModel = VaultBrowserViewModel(repository, intentParser)
        val states = mutableListOf<VaultBrowserUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        assertThat(states.last().collections).isEmpty()
        assertThat(states.last().items.map { it.id }).containsExactly("i1")
        job.cancel()
    }

    @Test
    fun `selecting a collection narrows items to its members`() = runTest(dispatcher) {
        val trip = collection("c1", "Europe Trip", itemCount = 1)
        every { repository.observeActive() } returns flowOf(
            listOf(VaultItem(id = "i1", title = "Receipt"), VaultItem(id = "i2", title = "Boarding pass"))
        )
        every { repository.observeActiveCollections() } returns flowOf(listOf(trip))
        every { repository.observeItemsForCollection("c1") } returns flowOf(
            listOf(VaultItem(id = "i2", title = "Boarding pass"))
        )
        val viewModel = VaultBrowserViewModel(repository, intentParser)
        val states = mutableListOf<VaultBrowserUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        assertThat(states.last().collections.map { it.name }).containsExactly("Europe Trip")
        assertThat(states.last().items).hasSize(2)

        viewModel.onCollectionSelected("c1")
        advanceUntilIdle()

        assertThat(states.last().activeCollectionId).isEqualTo("c1")
        assertThat(states.last().activeFilter).isEqualTo(BrowserFilter.ALL)
        assertThat(states.last().items.map { it.id }).containsExactly("i2")

        viewModel.onCollectionSelected(null)
        advanceUntilIdle()

        assertThat(states.last().activeCollectionId).isNull()
        assertThat(states.last().items).hasSize(2)
        job.cancel()
    }

    @Test
    fun `selecting a state filter clears the collection filter`() = runTest(dispatcher) {
        every { repository.observeActive() } returns flowOf(
            listOf(VaultItem(id = "i1", title = "Note", isPinned = true))
        )
        every { repository.observeActiveCollections() } returns flowOf(listOf(collection("c1", "House")))
        every { repository.observeItemsForCollection("c1") } returns flowOf(emptyList())
        val viewModel = VaultBrowserViewModel(repository, intentParser)
        val states = mutableListOf<VaultBrowserUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        viewModel.onCollectionSelected("c1")
        advanceUntilIdle()
        assertThat(states.last().activeCollectionId).isEqualTo("c1")

        viewModel.onFilterSelected(BrowserFilter.PINNED)
        advanceUntilIdle()

        assertThat(states.last().activeCollectionId).isNull()
        assertThat(states.last().activeFilter).isEqualTo(BrowserFilter.PINNED)
        assertThat(states.last().items.map { it.id }).containsExactly("i1")
        job.cancel()
    }
}

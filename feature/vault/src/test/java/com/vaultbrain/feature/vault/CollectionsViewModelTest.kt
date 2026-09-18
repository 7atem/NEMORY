package com.vaultbrain.feature.vault

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.common.model.PersonalCollection
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
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
class CollectionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<VaultRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun collection(
        id: String,
        name: String,
        archivedAt: Long? = null,
        isPinned: Boolean = false,
        itemCount: Int = 0
    ) = PersonalCollection(
        id = id, name = name, createdAt = 1L, updatedAt = 1L,
        archivedAt = archivedAt, isPinned = isPinned, itemCount = itemCount
    )

    @Test
    fun `active and archived collections are exposed separately`() = runTest(dispatcher) {
        every { repository.observeActiveCollections() } returns flowOf(
            listOf(collection("c1", "Europe Trip"))
        )
        every { repository.observeArchivedCollections() } returns flowOf(
            listOf(collection("c2", "Old Stuff", archivedAt = 2L))
        )
        val viewModel = CollectionsViewModel(repository)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.active.map { it.name }).containsExactly("Europe Trip")
        assertThat(viewModel.uiState.value.archived.map { it.name }).containsExactly("Old Stuff")
    }

    @Test
    fun `create delegates to repository`() = runTest(dispatcher) {
        every { repository.observeActiveCollections() } returns flowOf(emptyList())
        every { repository.observeArchivedCollections() } returns flowOf(emptyList())
        coEvery { repository.createCollection("PhD", any()) } returns collection("c3", "PhD")
        val viewModel = CollectionsViewModel(repository)
        advanceUntilIdle()

        viewModel.create("PhD")
        advanceUntilIdle()

        coVerify { repository.createCollection("PhD", any()) }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CollectionDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<VaultRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun collection(
        id: String = "c1",
        name: String = "Europe Trip",
        archivedAt: Long? = null,
        isPinned: Boolean = false
    ) = PersonalCollection(
        id = id, name = name, createdAt = 1L, updatedAt = 1L,
        archivedAt = archivedAt, isPinned = isPinned
    )

    private fun loadedViewModel(
        collection: PersonalCollection = collection(),
        members: List<VaultItem> = listOf(VaultItem(id = "i1", title = "Boarding pass")),
        activeItems: List<VaultItem> = listOf(
            VaultItem(id = "i1", title = "Boarding pass"),
            VaultItem(id = "i2", title = "Hotel invoice")
        )
    ): CollectionDetailViewModel {
        every { repository.observeCollection(collection.id) } returns flowOf(collection)
        every { repository.observeItemsForCollection(collection.id) } returns flowOf(members)
        every { repository.observeActive() } returns flowOf(activeItems)
        return CollectionDetailViewModel(repository).also { it.load(collection.id) }
    }

    @Test
    fun `detail state splits member and available items`() = runTest(dispatcher) {
        val viewModel = loadedViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.collection?.name).isEqualTo("Europe Trip")
        assertThat(state.items.map { it.id }).containsExactly("i1")
        assertThat(state.otherItems.map { it.id }).containsExactly("i2")
    }

    @Test
    fun `rename delegates to repository`() = runTest(dispatcher) {
        coEvery { repository.renameCollection("c1", "Italy 2026") } returns true
        val viewModel = loadedViewModel()
        advanceUntilIdle()

        viewModel.rename("Italy 2026")
        advanceUntilIdle()

        coVerify { repository.renameCollection("c1", "Italy 2026") }
    }

    @Test
    fun `pin toggle delegates to repository`() = runTest(dispatcher) {
        coEvery { repository.setCollectionPinned("c1", true) } returns true
        val viewModel = loadedViewModel()
        advanceUntilIdle()

        viewModel.togglePinned()
        advanceUntilIdle()

        coVerify { repository.setCollectionPinned("c1", true) }
    }

    @Test
    fun `archive toggle archives active collection`() = runTest(dispatcher) {
        coEvery { repository.archiveCollection("c1") } returns true
        val viewModel = loadedViewModel()
        advanceUntilIdle()

        viewModel.toggleArchived()
        advanceUntilIdle()

        coVerify { repository.archiveCollection("c1") }
    }

    @Test
    fun `archive toggle restores archived collection`() = runTest(dispatcher) {
        coEvery { repository.restoreCollection("c1") } returns true
        val viewModel = loadedViewModel(collection = collection(archivedAt = 5L))
        advanceUntilIdle()

        viewModel.toggleArchived()
        advanceUntilIdle()

        coVerify { repository.restoreCollection("c1") }
    }

    @Test
    fun `add and remove item delegate to repository`() = runTest(dispatcher) {
        coEvery { repository.addItemToCollection("i2", "c1") } returns true
        coEvery { repository.removeItemFromCollection("i1", "c1") } returns true
        val viewModel = loadedViewModel()
        advanceUntilIdle()

        viewModel.addItem("i2")
        viewModel.removeItem("i1")
        advanceUntilIdle()

        coVerify { repository.addItemToCollection("i2", "c1") }
        coVerify { repository.removeItemFromCollection("i1", "c1") }
    }
}

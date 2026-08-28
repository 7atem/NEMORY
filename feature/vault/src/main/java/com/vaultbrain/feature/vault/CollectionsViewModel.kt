package com.vaultbrain.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.core.common.model.PersonalCollection
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionsUiState(
    val active: List<PersonalCollection> = emptyList(),
    val archived: List<PersonalCollection> = emptyList()
)

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeActiveCollections(),
                repository.observeArchivedCollections()
            ) { active, archived -> CollectionsUiState(active, archived) }
                .collect { _uiState.value = it }
        }
    }

    fun create(name: String) {
        viewModelScope.launch { repository.createCollection(name) }
    }
}

data class CollectionDetailUiState(
    val collection: PersonalCollection? = null,
    val items: List<VaultItem> = emptyList(),
    val availableItems: List<VaultItem> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CollectionDetailUiState())
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()
    private var collectionJob: Job? = null

    fun load(collectionId: String) {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            combine(
                repository.observeCollection(collectionId),
                repository.observeItemsForCollection(collectionId),
                repository.observeActive()
            ) { collection, items, activeItems ->
                CollectionDetailUiState(
                    collection = collection,
                    items = items,
                    availableItems = activeItems.filterNot { candidate -> items.any { it.id == candidate.id } },
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    fun rename(name: String) = withCollection { repository.renameCollection(it.id, name) }

    fun togglePinned() = withCollection { repository.setCollectionPinned(it.id, !it.isPinned) }

    fun toggleArchived() = withCollection {
        if (it.isArchived) repository.restoreCollection(it.id) else repository.archiveCollection(it.id)
    }

    fun addItem(itemId: String) = withCollection { repository.addItemToCollection(itemId, it.id) }

    fun removeItem(itemId: String) = withCollection { repository.removeItemFromCollection(itemId, it.id) }

    private fun withCollection(block: suspend (PersonalCollection) -> Unit) {
        val collection = _uiState.value.collection ?: return
        viewModelScope.launch { block(collection) }
    }
}

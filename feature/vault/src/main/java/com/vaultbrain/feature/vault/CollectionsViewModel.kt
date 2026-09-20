package com.vaultbrain.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.shared.model.PersonalCollection
import com.vaultbrain.shared.model.VaultItem
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
    val suggestedItems: List<VaultItem> = emptyList(),
    val otherItems: List<VaultItem> = emptyList(),
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
                if (collection == null) return@combine CollectionDetailUiState(isLoading = false)

                val available = activeItems.filterNot { candidate -> items.any { it.id == candidate.id } }
                val collectionNameTokens = collection.name.lowercase().split(Regex("\\s+|-|_"))
                val targetDomains = setOf("REAL_ESTATE", "LEGAL_DOCUMENT", "MEDICAL_RECORD", "PRESCRIPTION")

                val suggested = available.filter { candidate ->
                    val classification = candidate.effectiveClassification?.name
                    val matchesDomain = classification in targetDomains
                    val matchesName = collectionNameTokens.any { token ->
                        token.length > 3 && candidate.title.lowercase().contains(token)
                    }
                    matchesDomain || matchesName
                }.sortedByDescending { it.createdAt }.take(10)

                val others = (available - suggested.toSet()).sortedByDescending { it.createdAt }

                CollectionDetailUiState(
                    collection = collection,
                    items = items,
                    suggestedItems = suggested,
                    otherItems = others,
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

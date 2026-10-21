package com.vaultbrain.shared.ui.home

import com.vaultbrain.shared.data.CollectionSummary
import com.vaultbrain.shared.data.VaultItemDetail
import com.vaultbrain.shared.data.VaultItemSummary
import com.vaultbrain.shared.data.VaultRepository
import com.vaultbrain.shared.database.getSharedVaultDatabase
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Vault bridge for the SwiftUI shell: live item list plus add/delete,
 * backed by the shared Room database. All callbacks arrive on the main thread.
 */
object IosVaultBridge {
    private val database by lazy { getSharedVaultDatabase() }
    private val repository by lazy { VaultRepository(database) }
    private val scope = MainScope()

    private var observing = false

    /** Starts pushing item-list updates to [onChange]. Subsequent calls are no-ops. */
    fun startObservingItems(onChange: (List<VaultItemSummary>) -> Unit) {
        if (observing) return
        observing = true
        scope.launch {
            repository.observeItems().collect { summaries ->
                onChange(summaries)
            }
        }
    }

    fun addSampleItem(onDone: () -> Unit) {
        scope.launch {
            repository.addSampleItem()
            onDone()
        }
    }

    fun deleteItem(id: String, onDone: () -> Unit) {
        scope.launch {
            repository.deleteItem(id)
            onDone()
        }
    }

    // region Collections

    private var observingCollections = false

    /** Starts pushing collection-list updates to [onChange]. Subsequent calls are no-ops. */
    fun startObservingCollections(onChange: (List<CollectionSummary>) -> Unit) {
        if (observingCollections) return
        observingCollections = true
        scope.launch {
            repository.observeCollections().collect { summaries ->
                onChange(summaries)
            }
        }
    }

    fun createCollection(name: String, onDone: () -> Unit) {
        scope.launch {
            repository.createCollection(name)
            onDone()
        }
    }

    // endregion

    // region Item detail

    /** Loads detail for one item; [onResult] receives null if it was deleted. */
    fun loadItemDetail(id: String, onResult: (VaultItemDetail?) -> Unit) {
        scope.launch {
            onResult(repository.getItemDetail(id))
        }
    }

    // endregion
}

package com.vaultbrain.shared.ui.home

import com.vaultbrain.shared.data.BrainMessage
import com.vaultbrain.shared.data.BriefingSnapshot
import com.vaultbrain.shared.data.CollectionSummary
import com.vaultbrain.shared.data.VaultItemDetail
import com.vaultbrain.shared.data.VaultItemSummary
import com.vaultbrain.shared.data.VaultRepository
import com.vaultbrain.shared.database.getSharedVaultDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.posix.memcpy

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

    // region Search, archive, notes

    private val searchObservers = mutableMapOf<String, Job>()
    private var archivedObserver: Job? = null

    /** Starts pushing search results for [query] to [onChange]. Blank query = active list.
     * Only one search observer runs at a time; previous ones are cancelled. */
    fun startObservingSearch(query: String, onChange: (List<VaultItemSummary>) -> Unit) {
        searchObservers.values.forEach { it.cancel() }
        searchObservers.clear()
        searchObservers[query] = scope.launch {
            repository.observeSearch(query).collect { summaries ->
                onChange(summaries)
            }
        }
    }

    fun startObservingArchived(onChange: (List<VaultItemSummary>) -> Unit) {
        if (archivedObserver != null) return
        archivedObserver = scope.launch {
            repository.observeArchivedItems().collect { summaries ->
                onChange(summaries)
            }
        }
    }

    fun archiveItem(id: String, onDone: () -> Unit) {
        scope.launch {
            repository.archiveItem(id)
            onDone()
        }
    }

    fun unarchiveItem(id: String, onDone: () -> Unit) {
        scope.launch {
            repository.unarchiveItem(id)
            onDone()
        }
    }

    fun setItemPinned(id: String, pinned: Boolean, onDone: () -> Unit) {
        scope.launch {
            repository.setItemPinned(id, pinned)
            onDone()
        }
    }

    fun updateItemNotes(id: String, notes: String, onDone: () -> Unit) {
        scope.launch {
            repository.updateItemNotes(id, notes)
            onDone()
        }
    }

    /** Sets or clears dates; pass 0 millis to clear a date. */
    fun setItemDates(id: String, expiryMillis: Long, alertMillis: Long, onDone: () -> Unit) {
        scope.launch {
            repository.setItemDates(id, expiryMillis, alertMillis)
            onDone()
        }
    }

    /** Updates title and summary; a blank summary clears it. */
    fun updateItemCore(id: String, title: String, summary: String, onDone: () -> Unit) {
        scope.launch {
            repository.updateItemCore(id, title, summary)
            onDone()
        }
    }

    /** Attaches capture coordinates to an item. */
    fun setItemLocation(id: String, latitude: Double, longitude: Double, locationName: String, onDone: () -> Unit) {
        scope.launch {
            repository.setItemLocation(id, latitude, longitude, locationName)
            onDone()
        }
    }

    // endregion

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

    fun renameCollection(id: String, name: String, onDone: () -> Unit) {
        scope.launch {
            repository.renameCollection(id, name)
            onDone()
        }
    }

    fun deleteCollection(id: String, onDone: () -> Unit) {
        scope.launch {
            repository.deleteCollection(id)
            onDone()
        }
    }

    fun setCollectionPinned(id: String, pinned: Boolean, onDone: () -> Unit) {
        scope.launch {
            repository.setCollectionPinned(id, pinned)
            onDone()
        }
    }

    // endregion

    // region Membership

    private val itemCollectionObservers = mutableMapOf<String, Job>()
    private val collectionItemObservers = mutableMapOf<String, Job>()

    /** Starts pushing the collections containing [itemId] to [onChange]. One observer per item id. */
    fun startObservingCollectionsForItem(itemId: String, onChange: (List<CollectionSummary>) -> Unit) {
        if (itemCollectionObservers.containsKey(itemId)) return
        itemCollectionObservers[itemId] = scope.launch {
            repository.observeCollectionsForItem(itemId).collect { summaries ->
                onChange(summaries)
            }
        }
    }

    /** Starts pushing the items inside [collectionId] to [onChange]. One observer per collection id. */
    fun startObservingCollectionItems(collectionId: String, onChange: (List<VaultItemSummary>) -> Unit) {
        if (collectionItemObservers.containsKey(collectionId)) return
        collectionItemObservers[collectionId] = scope.launch {
            repository.observeCollectionItems(collectionId).collect { summaries ->
                onChange(summaries)
            }
        }
    }

    /** Adds or removes [itemId] from [collectionId]. */
    fun setItemInCollection(itemId: String, collectionId: String, member: Boolean, onDone: () -> Unit) {
        scope.launch {
            repository.setItemInCollection(itemId = itemId, collectionId = collectionId, member = member)
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

    // region Brain + Briefing

    private var observingBrain = false
    private val foundationModelsClient by lazy { com.vaultbrain.shared.intelligence.AppleFoundationModelsClient() }

    /** Starts pushing persisted brain turns to [onChange]. Subsequent calls are no-ops. */
    fun startObservingBrainMessages(onChange: (List<BrainMessage>) -> Unit) {
        if (observingBrain) return
        observingBrain = true
        scope.launch {
            repository.observeBrainMessages().collect { messages ->
                onChange(messages)
            }
        }
    }

    /** Asks the local brain: stores the user turn, computes the reply off the main thread.
     * Uses grounded vault retrieval first, enhanced by Apple Foundation Models when available. */
    fun askBrain(query: String, onResult: (String?) -> Unit) {
        scope.launch {
            try {
                repository.addBrainMessage(role = "user", text = query)
                val (groundedSummary, _) = withContext(Dispatchers.Default) {
                    repository.brainReply(query)
                }

                val finalAnswer = withContext(Dispatchers.Default) {
                    if (foundationModelsClient.isAvailable() && !com.vaultbrain.shared.security.DecoySessionState.isDecoy) {
                        val prompt = "You are Nemory Brain, an on-device private assistant. Answer using ONLY these vault facts:\n$groundedSummary\n\nQuestion: $query"
                        val aiResult = foundationModelsClient.generateText(prompt, maxTokens = 256)
                        if (aiResult.isSuccess && !aiResult.getOrNull().isNullOrBlank()) {
                            aiResult.getOrNull()!!
                        } else {
                            groundedSummary
                        }
                    } else {
                        groundedSummary
                    }
                }

                repository.addBrainMessage(role = "assistant", text = finalAnswer)
                onResult(finalAnswer)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun clearBrain(onDone: () -> Unit) {
        scope.launch {
            repository.clearBrainMessages()
            onDone()
        }
    }

    /** One-shot briefing digest for the briefing screen. */
    fun loadBriefing(onResult: (BriefingSnapshot?) -> Unit) {
        scope.launch {
            try {
                val snapshot = withContext(Dispatchers.Default) {
                    repository.loadBriefing()
                }
                onResult(snapshot)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    // endregion

    private val mediaStorage by lazy { com.vaultbrain.shared.storage.IosMediaVaultStorage() }

    /**
     * Imports an image the shell wrote to a temp file: moves it into protected
     * vault storage, inserts a vault item referencing it, then removes the temp
     * file. [onResult] receives the new item id, or null on failure.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun importCapturedImage(tempPath: String, sourceName: String, onResult: (String?) -> Unit) {
        scope.launch {
            try {
                val nsData = NSData.dataWithContentsOfFile(tempPath)
                if (nsData == null || nsData.length.toInt() == 0) {
                    onResult(null)
                    return@launch
                }
                val bytes = ByteArray(nsData.length.toInt())
                bytes.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
                }
                val now = com.vaultbrain.shared.util.currentTimeMillis()
                val fileName = "capture-$now.jpg"
                val storedPath = mediaStorage.saveMedia(fileName, bytes)
                val source = com.vaultbrain.shared.model.SourceType.entries
                    .firstOrNull { it.name == sourceName }
                    ?: com.vaultbrain.shared.model.SourceType.MANUAL
                val itemId = repository.addCapturedItem(
                    title = "Capture ${now % 100_000}",
                    source = source,
                    capturedImageUri = storedPath,
                )
                NSFileManager.defaultManager.removeItemAtPath(tempPath, error = null)
                onResult(itemId)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    // endregion
}

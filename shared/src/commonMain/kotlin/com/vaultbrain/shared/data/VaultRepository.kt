package com.vaultbrain.shared.data

import com.vaultbrain.shared.database.SharedVaultDatabase
import com.vaultbrain.shared.database.dao.PersonalCollectionRow
import com.vaultbrain.shared.database.entity.VaultItemEntity
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.util.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Read/write access to the shared vault database.
 *
 * Lives in commonMain so any platform shell (SwiftUI demos today, shared
 * Compose screens tomorrow) drives the same code path.
 */
class VaultRepository(private val database: SharedVaultDatabase) {

    private val items get() = database.vaultItemDao()

    /** Live view of active (non-archived) vault items, newest first. */
    fun observeItems(): Flow<List<VaultItemSummary>> =
        items.observeActive().map { list ->
            list.map { it.toSummary() }
        }

    /** Live view of archived vault items, newest first. */
    fun observeArchivedItems(): Flow<List<VaultItemSummary>> =
        items.observeArchived().map { list ->
            list.map { it.toSummary() }
        }

    /**
     * Live full-text search over title, summary, OCR text and tags.
     * Falls back to the active list when the query has no searchable tokens.
     */
    fun observeSearch(query: String): Flow<List<VaultItemSummary>> {
        val sanitized = sanitizeFtsQuery(query)
        if (sanitized == null) return observeItems()
        return items.observeSearchFts(sanitized).map { list ->
            list.filter { !it.isArchived }.map { it.toSummary() }
        }
    }

    private fun sanitizeFtsQuery(query: String): String? {
        val tokens = query.trim().split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}_]"), "") }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" AND ") { "\"$it\"" }
    }

    suspend fun archiveItem(id: String) {
        items.updateArchiveState(id, true, currentTimeMillis())
    }

    suspend fun unarchiveItem(id: String) {
        items.updateArchiveState(id, false, currentTimeMillis())
    }

    suspend fun setItemPinned(id: String, pinned: Boolean) {
        items.updatePinnedState(id, pinned, currentTimeMillis())
    }

    suspend fun updateItemNotes(id: String, notes: String?) {
        items.updateNotes(id, notes?.takeIf { it.isNotBlank() }, currentTimeMillis())
    }

    /** Inserts a small demo item and returns its id. */
    suspend fun addSampleItem(title: String? = null): String {
        val now = currentTimeMillis()
        val id = "ios-item-$now"
        items.insert(
            VaultItemEntity(
                id = id,
                title = title ?: "Vault item ${now % 10_000}",
                summary = "Added from the iOS shell",
                sourceType = SourceType.MANUAL,
                parsedMetadata = "{}",
                lensTags = "[\"MONEY\"]",
            )
        )
        return id
    }

    suspend fun deleteItem(id: String) {
        items.deleteById(id)
    }

    // region Collections

    /** Live view of active collections with item counts. */
    fun observeCollections(): Flow<List<CollectionSummary>> =
        database.personalCollectionDao().observeActive().map { rows ->
            rows.map { it.toSummary() }
        }

    /** Creates a user collection and returns its id. */
    suspend fun createCollection(name: String): String {
        val now = currentTimeMillis()
        val id = "ios-collection-$now"
        database.personalCollectionDao().insertCollection(
            com.vaultbrain.shared.database.entity.PersonalCollectionEntity(
                id = id,
                name = name,
                createdAt = now,
                updatedAt = now,
            )
        )
        return id
    }

    // region Membership

    /** Live view of the collections that contain [itemId]. */
    fun observeCollectionsForItem(itemId: String): Flow<List<CollectionSummary>> =
        database.personalCollectionDao().observeForItem(itemId).map { rows ->
            rows.map { it.toSummary() }
        }

    /** Live view of the items inside one collection. */
    fun observeCollectionItems(collectionId: String): Flow<List<VaultItemSummary>> =
        database.personalCollectionDao().observeItems(collectionId).map { list ->
            list.map { it.toSummary() }
        }

    /** Adds or removes [itemId] from [collectionId]. */
    suspend fun setItemInCollection(itemId: String, collectionId: String, member: Boolean) {
        val dao = database.personalCollectionDao()
        if (member) {
            dao.insertMembership(
                com.vaultbrain.shared.database.entity.PersonalCollectionMembershipEntity(
                    collectionId = collectionId,
                    itemId = itemId,
                    createdAt = currentTimeMillis(),
                )
            )
        } else {
            dao.removeMembership(collectionId = collectionId, itemId = itemId)
        }
    }

    // endregion

    // endregion

    // region Item detail

    /** Loads full detail for one item, or null if it no longer exists. */
    suspend fun getItemDetail(id: String): VaultItemDetail? =
        items.getById(id)?.let { entity ->
            VaultItemDetail(
                id = entity.id,
                title = entity.title,
                summary = entity.summary,
                rawOcrText = entity.rawOcrText,
                sourceTypeName = entity.sourceType.name,
                lensTags = parseLensTags(entity.lensTags),
                createdAtMillis = entity.createdAt,
                updatedAtMillis = entity.updatedAt,
                capturedImageUri = entity.capturedImageUri,
                userNotes = entity.userNotes,
                isArchived = entity.isArchived,
                isPinned = entity.isPinned,
            )
        }

    suspend fun renameCollection(id: String, name: String) {
        database.personalCollectionDao().rename(id, name, currentTimeMillis())
    }

    suspend fun deleteCollection(id: String) {
        database.personalCollectionDao().deletePermanently(id)
    }

    suspend fun setCollectionPinned(id: String, pinned: Boolean) {
        database.personalCollectionDao().setPinned(id, pinned, currentTimeMillis())
    }

    /** Inserts a vault item that references an already-stored image file. Returns the item id. */
    suspend fun addCapturedItem(title: String, source: SourceType, capturedImageUri: String): String {
        val now = currentTimeMillis()
        val id = "ios-item-$now"
        items.insert(
            VaultItemEntity(
                id = id,
                title = title,
                summary = "Captured from the iOS shell",
                sourceType = source,
                capturedImageUri = capturedImageUri,
                parsedMetadata = "{}",
                lensTags = "[\"MONEY\"]",
            )
        )
        return id
    }

    // endregion

    private fun VaultItemEntity.toSummary(): VaultItemSummary {
        return VaultItemSummary(
            id = id,
            title = title,
            summary = summary,
            createdAtMillis = createdAt,
            lensTags = parseLensTags(lensTags),
        )
    }

    private fun PersonalCollectionRow.toSummary(): CollectionSummary {
        return CollectionSummary(
            id = id,
            name = name,
            itemCount = itemCount,
            createdAtMillis = createdAt,
            isPinned = isPinned,
        )
    }

    private fun parseLensTags(json: String): List<String> = runCatching {
        val element = Json.parseToJsonElement(json)
        if (element is JsonArray) {
            element.mapNotNull { (it as? JsonPrimitive)?.content }
        } else {
            emptyList()
        }
    }.getOrDefault(emptyList())
}

package com.vaultbrain.shared.data

import com.vaultbrain.shared.database.SharedVaultDatabase
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

    /** Live view of all vault items, newest first. */
    fun observeItems(): Flow<List<VaultItemSummary>> =
        items.observeAll().map { list ->
            list.sortedByDescending { it.createdAt }.map { it.toSummary() }
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
            rows.map { row ->
                CollectionSummary(
                    id = row.id,
                    name = row.name,
                    itemCount = row.itemCount,
                    createdAtMillis = row.createdAt,
                    isPinned = row.isPinned,
                )
            }
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
            )
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

    private fun parseLensTags(json: String): List<String> = runCatching {
        val element = Json.parseToJsonElement(json)
        if (element is JsonArray) {
            element.mapNotNull { (it as? JsonPrimitive)?.content }
        } else {
            emptyList()
        }
    }.getOrDefault(emptyList())
}

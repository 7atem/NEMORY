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

    // region Brain chat (local vault-aware responder)

    fun observeBrainMessages(): Flow<List<BrainMessage>> =
        database.brainMessageDao().observeAll().map { list ->
            list.sortedBy { it.createdAt }.map { entity ->
                BrainMessage(
                    id = entity.id,
                    role = entity.role,
                    text = entity.text,
                    isError = entity.isError,
                    createdAtMillis = entity.createdAt,
                )
            }
        }

    suspend fun addBrainMessage(role: String, text: String, isError: Boolean = false) {
        val now = currentTimeMillis()
        database.brainMessageDao().upsert(
            com.vaultbrain.shared.database.entity.BrainMessageEntity(
                id = "brain-$now-${(0..9999).random()}",
                role = role,
                text = text,
                isError = isError,
                createdAt = now,
            )
        )
    }

    suspend fun clearBrainMessages() {
        database.brainMessageDao().clear()
    }

    /**
     * Local, offline responder: answers about the vault itself using live data.
     * Returns the reply text plus the vault item ids it referenced.
     */
    suspend fun brainReply(query: String): Pair<String, List<String>> {
        val active = items.getActive()
        val archived = items.getArchived()
        val q = query.trim().lowercase()

        fun titles(list: List<VaultItemEntity>, limit: Int = 3) =
            list.take(limit).map { "• ${it.title}" }

        return when {
            q.isEmpty() ->
                "Ask me about your vault — try \"how many items\", \"latest\", \"collections\" or any keyword." to emptyList()
            q in setOf("hi", "hello", "hey", "salam", "مرحبا") ->
                "Hello! I'm your local Nemory brain — fully offline. Ask me about what's in your vault." to emptyList()

            q.contains("how many") || q.contains("count") || q.contains("total") ->
                "You have ${active.size} active item${if (active.size == 1) "" else "s"}" +
                    " and ${archived.size} archived." to emptyList()

            q.contains("latest") || q.contains("recent") || q.contains("new") -> {
                val fresh = active.sortedByDescending { it.createdAt }.take(3)
                if (fresh.isEmpty()) "Your vault is empty — capture something first." to emptyList()
                else "Your latest items:\n" + titles(fresh).joinToString("\n") to fresh.map { it.id }
            }

            q.contains("collection") -> {
                val cols = database.personalCollectionDao().getActiveRows()
                if (cols.isEmpty()) "You haven't created any collections yet." to emptyList()
                else "Your collections:\n" + cols.take(5)
                    .joinToString("\n") { "• ${it.name} (${it.itemCount})" } to emptyList()
            }

            q.contains("expir") || q.contains("deadline") -> {
                val soon = items.getExpiringSoon(currentTimeMillis(), 5)
                if (soon.isEmpty()) "Nothing is expiring soon. Set expiry dates on items and I'll track them." to emptyList()
                else "Expiring soon:\n" + titles(soon).joinToString("\n") to soon.map { it.id }
            }

            q.contains("photo") || q.contains("camera") || q.contains("picture") || q.contains("image") -> {
                val captured = active.count { it.sourceType == SourceType.CAMERA }
                val gallery = active.count { it.sourceType == SourceType.GALLERY }
                "You have $captured camera capture${if (captured == 1) "" else "s"} and " +
                    "$gallery gallery import${if (gallery == 1) "" else "s"} in the vault." to emptyList()
            }

            q.contains("archive") ->
                "${archived.size} item${if (archived.size == 1) "" else "s"} archived right now." to emptyList()

            else -> {
                val sanitized = sanitizeFtsQuery(q)
                val matches = if (sanitized != null) items.searchFts(sanitized) else emptyList()
                val visible = matches.filter { !it.isArchived }.take(3)
                when {
                    visible.isNotEmpty() -> {
                        val details = visible.map { item ->
                            val metaClean = if (item.parsedMetadata.isNotBlank() && item.parsedMetadata != "{}") {
                                val pairs = item.parsedMetadata
                                    .trim('{', '}')
                                    .split(',')
                                    .mapNotNull {
                                        val parts = it.split(':')
                                        if (parts.size == 2) "${parts[0].trim('"', ' ')}: ${parts[1].trim('"', ' ')}" else null
                                    }
                                if (pairs.isNotEmpty()) "\n  " + pairs.take(3).joinToString(" | ") else ""
                            } else if (!item.summary.isNullOrBlank()) {
                                "\n  ${item.summary}"
                            } else ""
                            "• ${item.title}$metaClean"
                        }.joinToString("\n")
                        "Here's what I found for \"$query\":\n$details" to visible.map { it.id }
                    }
                    else ->
                        "I searched your vault for \"$query\" and found nothing. " +
                            "Try searching by store name, amount, or keyword from the document." to emptyList()
                }
            }
        }
    }

    // endregion

    // region Briefing

    /** One-shot digest for the briefing screen. */
    suspend fun loadBriefing(): BriefingSnapshot {
        val now = currentTimeMillis()
        val dayAgo = now - 24L * 60 * 60 * 1000
        val active = items.getActive()
        val archived = items.getArchived()
        val recent = active.filter { it.createdAt >= dayAgo }.sortedByDescending { it.createdAt }
        val expiring = items.getExpiringSoon(now, 5)
            .filter { !it.isArchived }
        val needsReview = active.count { it.needsReview }
        val collections = database.personalCollectionDao().getActiveRows().map { it.toSummary() }
        return BriefingSnapshot(
            totalActive = active.size,
            totalArchived = archived.size,
            newSinceMillis = dayAgo,
            recentItems = recent.take(5).map { it.toSummary() },
            expiringSoon = expiring.map { it.toSummary() },
            needsReviewCount = needsReview,
            collections = collections,
        )
    }

    // endregion

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
                expiryDateMillis = entity.expiryDate ?: 0,
                secondaryAlertDateMillis = entity.secondaryAlertDate ?: 0,
                locationName = entity.locationName,
                latitude = entity.captureLatitude ?: Double.NaN,
                longitude = entity.captureLongitude ?: Double.NaN,
                parsedMetadataJson = entity.parsedMetadata,
                primaryLensId = entity.primaryLensId ?: parseLensTags(entity.lensTags).firstOrNull(),
                aiClassificationName = entity.aiClassification?.name,
                evidenceCoordinatesJson = entity.customFields,
            )
        }

    /** Sets or clears expiry/alert dates. A 0 millis means "clear". */
    suspend fun setItemDates(id: String, expiryMillis: Long, alertMillis: Long) {
        items.updateDates(
            id = id,
            expiryDate = expiryMillis.takeIf { it > 0 },
            secondaryAlertDate = alertMillis.takeIf { it > 0 },
            now = currentTimeMillis(),
        )
    }

    /** Updates title and summary. A blank summary clears it. */
    suspend fun updateItemCore(id: String, title: String, summary: String) {
        items.updateCoreFields(
            id = id,
            title = title.trim().ifEmpty { "Untitled" },
            summary = summary.trim().takeIf { it.isNotEmpty() },
            now = currentTimeMillis(),
        )
    }

    /** Attaches capture coordinates and a human-readable place name to an item. */
    suspend fun setItemLocation(id: String, latitude: Double, longitude: Double, locationName: String) {
        items.updateLocation(
            id = id,
            latitude = latitude,
            longitude = longitude,
            locationName = locationName.takeIf { it.isNotBlank() },
            now = currentTimeMillis(),
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

    /** Inserts a vault item that references an already-stored image file with optional OCR and metadata. */
    suspend fun addCapturedItem(
        title: String,
        source: SourceType,
        capturedImageUri: String,
        summary: String? = null,
        rawOcrText: String? = null,
        parsedMetadataJson: String = "{}",
        lensTags: List<String> = listOf("MONEY"),
        primaryLensId: String? = null,
        expiryDateMillis: Long? = null,
        alertDateMillis: Long? = null,
        evidenceCoordinatesJson: String? = null,
    ): String {
        val now = currentTimeMillis()
        val id = "ios-item-$now"
        val effectiveLens = primaryLensId ?: lensTags.firstOrNull() ?: "MONEY"
        val tags = if (lensTags.isEmpty()) listOf(effectiveLens) else lensTags
        items.insert(
            VaultItemEntity(
                id = id,
                title = title.trim().ifEmpty { "Scanned Document" },
                summary = summary?.trim()?.takeIf { it.isNotEmpty() } ?: "Document stored in Nemory Vault",
                rawOcrText = rawOcrText?.trim()?.takeIf { it.isNotEmpty() },
                sourceType = source,
                capturedImageUri = capturedImageUri,
                parsedMetadata = parsedMetadataJson.trim().ifEmpty { "{}" },
                lensTags = serializeLensTags(tags),
                primaryLensId = effectiveLens,
                expiryDate = expiryDateMillis?.takeIf { it > 0 },
                secondaryAlertDate = alertDateMillis?.takeIf { it > 0 },
                customFields = evidenceCoordinatesJson,
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
            isPinned = isPinned,
            capturedImageUri = capturedImageUri,
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

    private fun serializeLensTags(tags: List<String>): String {
        return tags.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }
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

package com.vaultbrain.core.database.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.common.model.CollectionSuggestionStatus
import com.vaultbrain.core.common.model.PersonalCollection
import com.vaultbrain.core.common.model.PersonalCollectionSource
import com.vaultbrain.core.common.model.PersonalCollectionSuggestion
import com.vaultbrain.core.common.model.EnrichmentState
import com.vaultbrain.core.common.model.ProcessingState
import com.vaultbrain.core.database.dao.AuditLogDao
import com.vaultbrain.core.database.dao.NotificationQueueDao
import com.vaultbrain.core.database.dao.PersonalCollectionDao
import com.vaultbrain.core.database.dao.PersonalCollectionRow
import com.vaultbrain.core.database.dao.PersonalCollectionSuggestionDao
import com.vaultbrain.core.database.dao.SuggestionWithNameRow
import com.vaultbrain.core.database.dao.VaultItemDao
import com.vaultbrain.core.database.entity.AuditLogEntity
import com.vaultbrain.core.database.entity.PersonalCollectionEntity
import com.vaultbrain.core.database.entity.PersonalCollectionMembershipEntity
import com.vaultbrain.core.database.entity.PersonalCollectionSuggestionEntity
import com.vaultbrain.core.database.mapper.VaultItemMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

/**
 * Primary repository exposing [VaultItem] domain objects backed by the encrypted Room DB.
 */
import com.vaultbrain.core.common.security.DecoySessionState

@Singleton
open class VaultRepository @Inject constructor(
    private val vaultItemDao: VaultItemDao,
    private val auditLogDao: AuditLogDao,
    private val notificationQueueDao: NotificationQueueDao,
    private val personalCollectionDao: PersonalCollectionDao,
    private val personalCollectionSuggestionDao: PersonalCollectionSuggestionDao
) {

    fun observeActiveCollections(): Flow<List<PersonalCollection>> =
        kotlinx.coroutines.flow.combine(personalCollectionDao.observeActive(), DecoySessionState.isDecoy) { rows, isDecoy ->
            if (isDecoy) emptyList() else rows.map(::collectionOf)
        }

    fun observeArchivedCollections(): Flow<List<PersonalCollection>> =
        kotlinx.coroutines.flow.combine(personalCollectionDao.observeArchived(), DecoySessionState.isDecoy) { rows, isDecoy ->
            if (isDecoy) emptyList() else rows.map(::collectionOf)
        }

    fun observeCollection(id: String): Flow<PersonalCollection?> =
        kotlinx.coroutines.flow.combine(personalCollectionDao.observeById(id), DecoySessionState.isDecoy) { row, isDecoy ->
            if (isDecoy) null else row?.let(::collectionOf)
        }

    fun observeCollectionsByName(query: String): Flow<List<PersonalCollection>> =
        kotlinx.coroutines.flow.combine(personalCollectionDao.observeSearch(query.trim()), DecoySessionState.isDecoy) { rows, isDecoy ->
            if (isDecoy) emptyList() else rows.map(::collectionOf)
        }

    fun observeCollectionsForItem(itemId: String): Flow<List<PersonalCollection>> =
        kotlinx.coroutines.flow.combine(personalCollectionDao.observeForItem(itemId), DecoySessionState.isDecoy) { rows, isDecoy ->
            if (isDecoy) emptyList() else rows.map(::collectionOf)
        }

    fun observeItemsForCollection(collectionId: String): Flow<List<VaultItem>> =
        kotlinx.coroutines.flow.combine(personalCollectionDao.observeItems(collectionId), DecoySessionState.isDecoy) { rows, isDecoy ->
            if (isDecoy) emptyList() else rows.map(VaultItemMapper::toDomain)
        }

    suspend fun createCollection(
        name: String,
        now: Long = System.currentTimeMillis()
    ): PersonalCollection {
        require(!DecoySessionState.isDecoy.value) { "Collections are unavailable in decoy mode" }
        val normalizedName = normalizedCollectionName(name)
        val collection = PersonalCollection(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            createdAt = now,
            updatedAt = now,
            source = PersonalCollectionSource.USER
        )
        personalCollectionDao.insertCollection(collection.toEntity())
        return collection
    }

    suspend fun getCollection(id: String): PersonalCollection? {
        if (DecoySessionState.isDecoy.value) return null
        val entity = personalCollectionDao.getEntity(id) ?: return null
        return entity.toDomain(personalCollectionDao.countItems(id))
    }

    suspend fun renameCollection(id: String, name: String): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        return personalCollectionDao.rename(id, normalizedCollectionName(name), System.currentTimeMillis()) == 1
    }

    suspend fun archiveCollection(id: String): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        val now = System.currentTimeMillis()
        return personalCollectionDao.setArchived(id, now, now) == 1
    }

    suspend fun restoreCollection(id: String): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        return personalCollectionDao.setArchived(id, null, System.currentTimeMillis()) == 1
    }

    suspend fun setCollectionPinned(id: String, pinned: Boolean): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        return personalCollectionDao.setPinned(id, pinned, System.currentTimeMillis()) == 1
    }

    suspend fun deleteCollectionPermanently(id: String): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        return personalCollectionDao.deletePermanently(id) == 1
    }

    suspend fun addItemToCollection(itemId: String, collectionId: String): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        return personalCollectionDao.insertMembership(
            PersonalCollectionMembershipEntity(
                collectionId = collectionId,
                itemId = itemId,
                createdAt = System.currentTimeMillis()
            )
        ) != -1L
    }

    suspend fun removeItemFromCollection(itemId: String, collectionId: String): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        return personalCollectionDao.removeMembership(collectionId, itemId) == 1
    }

    // ---- Collection suggestions (machine-proposed; never user-authoritative) ----

    fun observeSuggestionsForItem(itemId: String): Flow<List<PersonalCollectionSuggestion>> =
        kotlinx.coroutines.flow.combine(
            personalCollectionSuggestionDao.observePendingForItem(itemId),
            DecoySessionState.isDecoy
        ) { rows, isDecoy -> if (isDecoy) emptyList() else rows.map(::suggestionOf) }

    fun observeSuggestionsForCollection(collectionId: String): Flow<List<PersonalCollectionSuggestion>> =
        kotlinx.coroutines.flow.combine(
            personalCollectionSuggestionDao.observePendingForCollection(collectionId),
            DecoySessionState.isDecoy
        ) { rows, isDecoy -> if (isDecoy) emptyList() else rows.map(::suggestionOf) }

    /** All suggestion rows (any status) for an item — matcher suppression input. */
    suspend fun getAllSuggestionsForItem(itemId: String): List<PersonalCollectionSuggestion> {
        if (DecoySessionState.isDecoy.value) return emptyList()
        return personalCollectionSuggestionDao.getAllForItem(itemId).map {
            PersonalCollectionSuggestion(
                itemId = it.itemId,
                collectionId = it.collectionId,
                confidence = it.confidence,
                status = it.status,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        }
    }

    /** One-shot active collections for the matcher / RAG context. */
    suspend fun getActiveCollections(): List<PersonalCollection> {
        if (DecoySessionState.isDecoy.value) return emptyList()
        return personalCollectionDao.getActiveRows().map(::collectionOf)
    }

    /** Existing collection ids including archived. Empty in decoy mode. */
    suspend fun getAllCollectionIds(): List<String> {
        if (DecoySessionState.isDecoy.value) return emptyList()
        return personalCollectionDao.getAllCollectionIds()
    }

    /** One-shot member items for collection semantic text / RAG context. */
    suspend fun getItemsForCollection(collectionId: String): List<VaultItem> {
        if (DecoySessionState.isDecoy.value) return emptyList()
        return personalCollectionDao.getItemsOnce(collectionId).map(VaultItemMapper::toDomain)
    }

    /** Items in active collections whose name matches [query] (collection-aware search). */
    fun observeItemsInCollectionsNamed(query: String): Flow<List<VaultItem>> =
        kotlinx.coroutines.flow.combine(
            personalCollectionDao.observeItemsForCollectionNameLike(query.trim()),
            DecoySessionState.isDecoy
        ) { rows, isDecoy -> if (isDecoy) emptyList() else rows.map(VaultItemMapper::toDomain) }

    /**
     * Inserts or refreshes a SUGGESTED row. Existing ACCEPTED/REJECTED rows are
     * untouched (rejection is suppression evidence; acceptance is user-authoritative).
     */
    suspend fun upsertSuggestion(itemId: String, collectionId: String, confidence: Float): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        val now = System.currentTimeMillis()
        val existing = personalCollectionSuggestionDao.get(collectionId, itemId)
        return when {
            existing == null -> personalCollectionSuggestionDao.insert(
                PersonalCollectionSuggestionEntity(
                    collectionId = collectionId,
                    itemId = itemId,
                    confidence = confidence,
                    status = CollectionSuggestionStatus.SUGGESTED,
                    createdAt = now,
                    updatedAt = now
                )
            ) != -1L
            existing.status == CollectionSuggestionStatus.SUGGESTED ->
                personalCollectionSuggestionDao.update(
                    collectionId, itemId, confidence, CollectionSuggestionStatus.SUGGESTED, now
                ) == 1
            else -> false
        }
    }

    /** Accepts a suggestion: creates membership + marks ACCEPTED atomically. */
    suspend fun acceptSuggestion(itemId: String, collectionId: String): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        val now = System.currentTimeMillis()
        val suggestion = personalCollectionSuggestionDao.get(collectionId, itemId) ?: return false
        personalCollectionSuggestionDao.accept(
            collectionId = collectionId,
            itemId = itemId,
            membership = PersonalCollectionMembershipEntity(
                collectionId = collectionId,
                itemId = itemId,
                createdAt = now
            ),
            now = now
        )
        return suggestion.status == CollectionSuggestionStatus.SUGGESTED
    }

    /** Rejects a suggestion; the REJECTED row persists as suppression evidence. */
    suspend fun rejectSuggestion(itemId: String, collectionId: String): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        val now = System.currentTimeMillis()
        return personalCollectionSuggestionDao.setStatus(
            collectionId, itemId, CollectionSuggestionStatus.REJECTED, now
        ) == 1
    }

    fun observeAll(): Flow<List<VaultItem>> =
        kotlinx.coroutines.flow.combine(vaultItemDao.observeAll(), DecoySessionState.isDecoy) { list, isDecoy ->
            if (isDecoy) emptyList() else list.map(VaultItemMapper::toDomain)
        }

    fun observeActive(): Flow<List<VaultItem>> =
        kotlinx.coroutines.flow.combine(vaultItemDao.observeActive(), DecoySessionState.isDecoy) { list, isDecoy ->
            if (isDecoy) emptyList() else list.map(VaultItemMapper::toDomain)
        }

    suspend fun getActive(): List<VaultItem> =
        if (DecoySessionState.isDecoy.value) emptyList()
        else vaultItemDao.getActive().map(VaultItemMapper::toDomain)

    suspend fun getArchived(): List<VaultItem> =
        if (DecoySessionState.isDecoy.value) emptyList()
        else vaultItemDao.getArchived().map(VaultItemMapper::toDomain)

    fun observeRecent(limit: Int): Flow<List<VaultItem>> =
        kotlinx.coroutines.flow.combine(vaultItemDao.observeRecent(limit), DecoySessionState.isDecoy) { list, isDecoy ->
            if (isDecoy) emptyList() else list.map(VaultItemMapper::toDomain)
        }

    fun observeByLens(lensTag: String): Flow<List<VaultItem>> =
        kotlinx.coroutines.flow.combine(vaultItemDao.observeByLens(lensTag), DecoySessionState.isDecoy) { list, isDecoy ->
            if (isDecoy) emptyList() else list.map(VaultItemMapper::toDomain)
        }

    fun observeByLensWithStealth(lensTag: String): Flow<List<VaultItem>> =
        kotlinx.coroutines.flow.combine(vaultItemDao.observeByLensWithStealth(lensTag), DecoySessionState.isDecoy) { list, isDecoy ->
            if (isDecoy) emptyList() else list.map(VaultItemMapper::toDomain)
        }

    fun observeNeedsReview(): Flow<List<VaultItem>> =
        kotlinx.coroutines.flow.combine(vaultItemDao.observeNeedsReview(), DecoySessionState.isDecoy) { list, isDecoy ->
            if (isDecoy) emptyList() else list.map(VaultItemMapper::toDomain)
        }

    fun observeStealth(): Flow<List<VaultItem>> =
        kotlinx.coroutines.flow.combine(vaultItemDao.observeStealth(), DecoySessionState.isDecoy) { list, isDecoy ->
            if (isDecoy) emptyList() else list.map(VaultItemMapper::toDomain)
        }

    fun observeExpiring(limitTime: Long): Flow<List<VaultItem>> =
        kotlinx.coroutines.flow.combine(vaultItemDao.observeExpiringBefore(limitTime), DecoySessionState.isDecoy) { list, isDecoy ->
            if (isDecoy) emptyList() else list.map(VaultItemMapper::toDomain)
        }

    suspend fun getById(id: String): VaultItem? =
        if (DecoySessionState.isDecoy.value) null else vaultItemDao.getById(id)?.let(VaultItemMapper::toDomain)

    suspend fun getByIds(ids: List<String>): List<VaultItem> =
        if (DecoySessionState.isDecoy.value) {
            emptyList()
        } else {
            vaultItemDao.getByIds(ids).map(VaultItemMapper::toDomain)
        }

    suspend fun save(item: VaultItem) {
        if (DecoySessionState.isDecoy.value) return
        val action = if (vaultItemDao.getById(item.id) == null) "CREATE" else "EDIT"
        vaultItemDao.insert(VaultItemMapper.toEntity(item))
        auditLogDao.insert(
            AuditLogEntity(
                action = action,
                itemId = item.id
            )
        )
    }

    suspend fun transitionEnrichmentState(
        id: String,
        expectedStates: Set<EnrichmentState>,
        nextState: EnrichmentState
    ): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        require(expectedStates.isNotEmpty()) { "At least one expected enrichment state is required" }
        require(expectedStates.all { it.canTransitionTo(nextState) }) {
            "Invalid enrichment transition to $nextState from $expectedStates"
        }
        return vaultItemDao.transitionEnrichmentState(
            id = id,
            expectedStates = expectedStates.map(EnrichmentState::name),
            nextState = nextState,
            updatedAt = System.currentTimeMillis()
        ) == 1
    }

    suspend fun claimNextEnrichment(
        now: Long = System.currentTimeMillis(),
        staleAfterMillis: Long,
        retryAfterMillis: Long,
        maxAttempts: Int
    ): VaultItem? {
        if (DecoySessionState.isDecoy.value) return null
        return vaultItemDao.claimNextEnrichment(
            now = now,
            staleBefore = now - staleAfterMillis,
            retryBefore = now - retryAfterMillis,
            maxAttempts = maxAttempts
        )?.let(VaultItemMapper::toDomain)
    }

    suspend fun countEnrichmentCandidates(maxAttempts: Int): Int =
        if (DecoySessionState.isDecoy.value) 0
        else vaultItemDao.countEnrichmentCandidates(maxAttempts)

    fun observeEnrichmentCandidateCount(maxAttempts: Int = 3): Flow<Int> =
        kotlinx.coroutines.flow.combine(
            vaultItemDao.observeEnrichmentCandidateCount(maxAttempts),
            DecoySessionState.isDecoy
        ) { count, isDecoy -> if (isDecoy) 0 else count }

    /** Requeues historical heuristic results for a full pass with the active local model. */
    suspend fun queueAllForLocalAiEnrichment(
        now: Long = System.currentTimeMillis()
    ): Int = if (DecoySessionState.isDecoy.value) {
        0
    } else {
        vaultItemDao.queueAllForLocalAiEnrichment(now)
    }

    suspend fun failClaimedEnrichment(
        id: String,
        errorCode: String,
        maxAttempts: Int,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        require(errorCode.matches(Regex("[A-Z0-9_]{1,64}"))) { "Invalid enrichment error code" }
        return vaultItemDao.failClaimedEnrichment(id, errorCode, maxAttempts, now) == 1
    }

    suspend fun releaseEnrichmentClaim(
        id: String,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        return vaultItemDao.releaseEnrichmentClaim(id, now) == 1
    }

    suspend fun replaceWhileEnrichmentClaimed(item: VaultItem): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        require(item.enrichmentState == EnrichmentState.RUNNING)
        return vaultItemDao.replaceWhileEnrichmentClaimed(VaultItemMapper.toEntity(item))
    }

    suspend fun transitionIndexingState(
        id: String,
        expectedState: ProcessingState,
        nextState: ProcessingState
    ): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        return vaultItemDao.transitionIndexingState(
            id = id,
            expectedState = expectedState.name,
            nextState = nextState.name,
            updatedAt = System.currentTimeMillis()
        ) == 1
    }

    suspend fun completeIndexing(
        id: String,
        duplicateItemId: String?,
        similarity: Float?
    ): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        require((duplicateItemId == null) == (similarity == null)) {
            "Duplicate item and similarity must be provided together"
        }
        similarity?.let { require(it in 0f..1f) { "Duplicate similarity must be between 0 and 1" } }
        return vaultItemDao.completeIndexing(
            id = id,
            duplicateItemId = duplicateItemId,
            similarity = similarity,
            updatedAt = System.currentTimeMillis()
        ) == 1
    }

    suspend fun claimNextIndexing(
        now: Long = System.currentTimeMillis(),
        staleAfterMillis: Long,
        retryAfterMillis: Long
    ): VaultItem? {
        if (DecoySessionState.isDecoy.value) return null
        return vaultItemDao.claimNextIndexing(
            now = now,
            staleBefore = now - staleAfterMillis,
            retryBefore = now - retryAfterMillis
        )?.let(VaultItemMapper::toDomain)
    }

    suspend fun replaceAfterEnrichment(
        item: VaultItem,
        expectedState: EnrichmentState
    ): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        require(expectedState.canTransitionTo(item.enrichmentState)) {
            "Invalid enrichment transition from $expectedState to ${item.enrichmentState}"
        }
        return vaultItemDao.replaceAfterEnrichment(
            item = VaultItemMapper.toEntity(item),
            expectedState = expectedState
        )
    }

    suspend fun delete(item: VaultItem) {
        if (DecoySessionState.isDecoy.value) return
        vaultItemDao.delete(VaultItemMapper.toEntity(item))
        auditLogDao.insert(AuditLogEntity(action = "DELETE", itemId = item.id))
    }

    suspend fun search(query: String): List<VaultItem> {
        val ftsQuery = buildFtsQuery(query) ?: return emptyList()
        return if (DecoySessionState.isDecoy.value) emptyList() else vaultItemDao.searchFts(ftsQuery).map(VaultItemMapper::toDomain)
    }

    fun observeSearch(query: String): Flow<List<VaultItem>> {
        val ftsQuery = buildFtsQuery(query) ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return kotlinx.coroutines.flow.combine(vaultItemDao.observeSearchFts(ftsQuery), DecoySessionState.isDecoy) { list, isDecoy ->
            if (isDecoy) emptyList() else list.map(VaultItemMapper::toDomain)
        }
    }

    /**
     * FTS4 MATCH accepts only trailing wildcards and treats most punctuation as syntax.
     * Reduce arbitrary user text to prefix tokens ("foo bar" -> "foo* bar*") so search
     * cannot throw SQLiteException per keystroke. Null means "no usable tokens".
     */
    private fun buildFtsQuery(raw: String): String? {
        val tokens = raw.trim()
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}_]"), "") }
            .filter { it.isNotBlank() }
        return tokens.takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }
    }

    /**
     * Filter item IDs by exact metadata predicates. Used by the RAG pipeline after
     * a vector search has produced candidate IDs.
     *
     * All values are bound as SQL parameters to avoid injection.
     */
    suspend fun filterIds(
        itemIds: List<String>,
        lensTag: String? = null,
        dateFrom: Long? = null,
        dateTo: Long? = null,
        hasImage: Boolean? = null,
        collectionId: String? = null
    ): List<String> {
        if (itemIds.isEmpty()) return emptyList()
        val membershipCollectionId =
            if (DecoySessionState.isDecoy.value) null else collectionId

        val bindArgs = mutableListOf<Any>()
        val inPlaceholders = itemIds.joinToString(",") { "?" }
        bindArgs.addAll(itemIds)

        val conditions = mutableListOf("id IN ($inPlaceholders)")

        lensTag?.let {
            conditions += "lens_tags LIKE ?"
            bindArgs += "%$it%"
        }
        membershipCollectionId?.let {
            conditions += "EXISTS (SELECT 1 FROM personal_collection_memberships m WHERE m.item_id = vault_items.id AND m.collection_id = ?)"
            bindArgs += it
        }
        dateFrom?.let {
            conditions += "created_at >= ?"
            bindArgs += it
        }
        dateTo?.let {
            conditions += "created_at <= ?"
            bindArgs += it
        }
        when (hasImage) {
            true -> conditions += "captured_image_uri IS NOT NULL"
            false -> conditions += "captured_image_uri IS NULL"
            null -> Unit
        }

        val sql = "SELECT id FROM vault_items WHERE ${conditions.joinToString(" AND ")}"
        return vaultItemDao.filterByMetadata(SimpleSQLiteQuery(sql, bindArgs.toTypedArray()))
    }

    suspend fun getExpiringSoon(now: Long, limit: Int): List<VaultItem> =
        vaultItemDao.getExpiringSoon(now, limit).map(VaultItemMapper::toDomain)

    suspend fun setArchived(id: String, isArchived: Boolean) {
        if (DecoySessionState.isDecoy.value) return
        vaultItemDao.updateArchiveState(id, isArchived, System.currentTimeMillis())
        val action = if (isArchived) "ARCHIVE" else "UNARCHIVE"
        auditLogDao.insert(AuditLogEntity(action = action, itemId = id))
    }

    suspend fun setPinned(id: String, isPinned: Boolean) {
        if (DecoySessionState.isDecoy.value) return
        vaultItemDao.updatePinnedState(id, isPinned, System.currentTimeMillis())
        val action = if (isPinned) "PIN" else "UNPIN"
        auditLogDao.insert(AuditLogEntity(action = action, itemId = id))
    }

    suspend fun setPinnedForIds(ids: List<String>, isPinned: Boolean) {
        if (DecoySessionState.isDecoy.value) return
        vaultItemDao.updatePinnedStateForIds(ids, isPinned, System.currentTimeMillis())
        val action = if (isPinned) "PIN" else "UNPIN"
        ids.forEach { id ->
            auditLogDao.insert(AuditLogEntity(action = action, itemId = id))
        }
    }

    private fun normalizedCollectionName(name: String): String {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Collection name cannot be blank" }
        require(normalized.length <= MAX_COLLECTION_NAME_LENGTH) {
            "Collection name cannot exceed $MAX_COLLECTION_NAME_LENGTH characters"
        }
        return normalized
    }

    private fun collectionOf(row: PersonalCollectionRow): PersonalCollection = PersonalCollection(
        id = row.id,
        name = row.name,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
        archivedAt = row.archivedAt,
        isPinned = row.isPinned,
        source = row.source,
        itemCount = row.itemCount
    )

    private fun suggestionOf(row: SuggestionWithNameRow) = PersonalCollectionSuggestion(
        itemId = row.itemId,
        collectionId = row.collectionId,
        collectionName = row.collectionName,
        itemTitle = row.itemTitle,
        confidence = row.confidence,
        status = row.status,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt
    )

    private fun PersonalCollection.toEntity() = PersonalCollectionEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
        isPinned = isPinned,
        source = source
    )

    private fun PersonalCollectionEntity.toDomain(itemCount: Int) = PersonalCollection(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
        isPinned = isPinned,
        source = source,
        itemCount = itemCount
    )

    private companion object {
        const val MAX_COLLECTION_NAME_LENGTH = 120
    }
}

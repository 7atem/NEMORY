package com.vaultbrain.shared.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.room.Transaction
// import removed
import com.vaultbrain.shared.database.entity.VaultItemEntity
import com.vaultbrain.shared.database.entity.VaultItemFts
import com.vaultbrain.shared.model.EnrichmentState
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for [VaultItemEntity] and its FTS shadow table.
 */
@Dao
interface VaultItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VaultItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<VaultItemEntity>)

    @Update
    suspend fun update(item: VaultItemEntity)

    @Delete
    suspend fun delete(item: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getById(id: String): VaultItemEntity?

    @Query("SELECT * FROM vault_items WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<VaultItemEntity>

    @Query("""
        UPDATE vault_items
        SET enrichment_state = :nextState, updated_at = :updatedAt
        WHERE id = :id AND enrichment_state IN (:expectedStates)
    """)
    suspend fun transitionEnrichmentState(
        id: String,
        expectedStates: List<String>,
        nextState: EnrichmentState,
        updatedAt: Long
    ): Int

    @Query("""
        UPDATE vault_items
        SET enrichment_state = 'FAILED_RETRYABLE',
            enrichment_claimed_at = NULL,
            enrichment_error_code = 'STALE_FOREGROUND_CLAIM',
            updated_at = :now
        WHERE enrichment_state = 'RUNNING'
          AND enrichment_claimed_at IS NOT NULL
          AND enrichment_claimed_at < :staleBefore
    """)
    suspend fun recoverStaleEnrichmentClaims(staleBefore: Long, now: Long): Int

    @Query("""
        SELECT * FROM vault_items
        WHERE enrichment_state IN ('PENDING', 'FAILED_RETRYABLE')
          AND extraction_state = 'COMPLETE'
          AND enrichment_attempt_count < :maxAttempts
          AND (enrichment_state = 'PENDING' OR enrichment_last_attempt_at <= :retryBefore)
        ORDER BY created_at ASC
        LIMIT 1
    """)
    suspend fun getNextEnrichmentCandidate(maxAttempts: Int, retryBefore: Long): VaultItemEntity?

    @Query("""
        SELECT COUNT(*) FROM vault_items
        WHERE enrichment_state IN ('PENDING', 'FAILED_RETRYABLE')
          AND extraction_state = 'COMPLETE'
          AND enrichment_attempt_count < :maxAttempts
    """)
    suspend fun countEnrichmentCandidates(maxAttempts: Int): Int

    @Query("""
        SELECT COUNT(*) FROM vault_items
        WHERE enrichment_state = 'PENDING'
          AND extraction_state = 'COMPLETE'
          AND enrichment_attempt_count < :maxAttempts
    """)
    fun observeEnrichmentCandidateCount(maxAttempts: Int): Flow<Int>

    /**
     * Makes every deterministically extracted, non-running item eligible for a fresh local-AI
     * pass. Explicit category overrides and user notes remain protected by the merge layer.
     */
    @Query("""
        UPDATE vault_items
        SET enrichment_state = 'PENDING',
            enrichment_attempt_count = 0,
            enrichment_claimed_at = NULL,
            enrichment_last_attempt_at = NULL,
            enrichment_error_code = NULL,
            updated_at = :now
        WHERE extraction_state = 'COMPLETE'
          AND enrichment_state NOT IN ('RUNNING', 'SKIPPED_PRIVACY')
    """)
    suspend fun queueAllForLocalAiEnrichment(now: Long): Int

    @Query("""
        UPDATE vault_items
        SET enrichment_state = 'RUNNING',
            enrichment_attempt_count = enrichment_attempt_count + 1,
            enrichment_claimed_at = :now,
            enrichment_last_attempt_at = :now,
            enrichment_error_code = NULL,
            updated_at = :now
        WHERE id = :id
          AND enrichment_state IN ('PENDING', 'FAILED_RETRYABLE')
          AND enrichment_attempt_count < :maxAttempts
    """)
    suspend fun claimEnrichment(id: String, now: Long, maxAttempts: Int): Int

    @Transaction
    suspend fun claimNextEnrichment(
        now: Long,
        staleBefore: Long,
        retryBefore: Long,
        maxAttempts: Int
    ): VaultItemEntity? {
        recoverStaleEnrichmentClaims(staleBefore, now)
        val candidate = getNextEnrichmentCandidate(maxAttempts, retryBefore) ?: return null
        if (claimEnrichment(candidate.id, now, maxAttempts) != 1) return null
        return getById(candidate.id)
    }

    @Query("""
        UPDATE vault_items
        SET enrichment_state = CASE
                WHEN enrichment_attempt_count >= :maxAttempts THEN 'FAILED_FINAL'
                ELSE 'FAILED_RETRYABLE'
            END,
            enrichment_claimed_at = NULL,
            enrichment_error_code = :errorCode,
            needs_review = CASE
                WHEN enrichment_attempt_count >= :maxAttempts THEN 1
                ELSE needs_review
            END,
            updated_at = :now
        WHERE id = :id AND enrichment_state = 'RUNNING'
    """)
    suspend fun failClaimedEnrichment(
        id: String,
        errorCode: String,
        maxAttempts: Int,
        now: Long
    ): Int

    @Query("""
        UPDATE vault_items
        SET enrichment_state = 'PENDING',
            enrichment_attempt_count = MAX(enrichment_attempt_count - 1, 0),
            enrichment_claimed_at = NULL,
            enrichment_error_code = NULL,
            updated_at = :now
        WHERE id = :id AND enrichment_state = 'RUNNING'
    """)
    suspend fun releaseEnrichmentClaim(id: String, now: Long): Int

    @Transaction
    suspend fun replaceWhileEnrichmentClaimed(item: VaultItemEntity): Boolean {
        val current = getById(item.id) ?: return false
        if (current.enrichmentState != EnrichmentState.RUNNING ||
            current.enrichmentClaimedAt != item.enrichmentClaimedAt
        ) return false
        update(item.preserveConcurrentUserEditsFrom(current))
        return true
    }

    @Query("""
        UPDATE vault_items
        SET indexing_state = :nextState, updated_at = :updatedAt
        WHERE id = :id AND indexing_state = :expectedState
    """)
    suspend fun transitionIndexingState(
        id: String,
        expectedState: String,
        nextState: String,
        updatedAt: Long
    ): Int

    @Query("""
        UPDATE vault_items
        SET indexing_state = 'COMPLETE',
            possible_duplicate_of_item_id = :duplicateItemId,
            duplicate_similarity = :similarity,
            needs_review = CASE
                WHEN :duplicateItemId IS NOT NULL THEN 1
                ELSE needs_review
            END,
            updated_at = :updatedAt
        WHERE id = :id AND indexing_state = 'RUNNING'
    """)
    suspend fun completeIndexing(
        id: String,
        duplicateItemId: String?,
        similarity: Float?,
        updatedAt: Long
    ): Int

    @Query("""
        UPDATE vault_items
        SET indexing_state = 'FAILED_RETRYABLE', updated_at = :now
        WHERE indexing_state = 'RUNNING' AND updated_at < :staleBefore
    """)
    suspend fun recoverStaleIndexingClaims(staleBefore: Long, now: Long): Int

    @Query("""
        SELECT * FROM vault_items
        WHERE indexing_state IN ('PENDING', 'FAILED_RETRYABLE')
          AND (indexing_state = 'PENDING' OR updated_at <= :retryBefore)
        ORDER BY created_at ASC
        LIMIT 1
    """)
    suspend fun getNextIndexingCandidate(retryBefore: Long): VaultItemEntity?

    @Transaction
    suspend fun claimNextIndexing(
        now: Long,
        staleBefore: Long,
        retryBefore: Long
    ): VaultItemEntity? {
        recoverStaleIndexingClaims(staleBefore, now)
        val candidate = getNextIndexingCandidate(retryBefore) ?: return null
        return if (transitionIndexingState(
                candidate.id,
                candidate.indexingState.name,
                "RUNNING",
                now
            ) == 1
        ) {
            getById(candidate.id)
        } else {
            null
        }
    }

    @Transaction
    suspend fun replaceAfterEnrichment(
        item: VaultItemEntity,
        expectedState: EnrichmentState
    ): Boolean {
        val current = getById(item.id) ?: return false
        if (current.enrichmentState != expectedState) return false
        update(item.preserveConcurrentUserEditsFrom(current))
        return true
    }

    @Query("SELECT * FROM vault_items ORDER BY created_at DESC")
    fun observeAll(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE is_stealth = 0 ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE is_archived = 0 AND is_stealth = 0 ORDER BY is_pinned DESC, created_at DESC")
    fun observeActive(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE is_archived = 0 AND is_stealth = 0 ORDER BY is_pinned DESC, created_at DESC")
    suspend fun getActive(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE is_archived = 1 AND is_stealth = 0 ORDER BY created_at DESC")
    fun observeArchived(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE is_archived = 1 AND is_stealth = 0 ORDER BY created_at DESC")
    suspend fun getArchived(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE is_stealth = 1 AND is_archived = 0 ORDER BY created_at DESC")
    fun observeStealth(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE (primary_lens_id = :lensTag OR (primary_lens_id IS NULL AND lens_tags LIKE '%' || :lensTag || '%')) AND is_archived = 0 AND is_stealth = 0 ORDER BY created_at DESC")
    fun observeByLens(lensTag: String): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE (primary_lens_id = :lensTag OR (primary_lens_id IS NULL AND lens_tags LIKE '%' || :lensTag || '%')) AND is_archived = 0 ORDER BY created_at DESC")
    fun observeByLensWithStealth(lensTag: String): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE needs_review = 1 ORDER BY created_at DESC")
    fun observeNeedsReview(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE expiry_date IS NOT NULL AND expiry_date > :now ORDER BY expiry_date ASC LIMIT :limit")
    suspend fun getExpiringSoon(now: Long, limit: Int): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE is_archived = 0 AND is_stealth = 0 AND expiry_date IS NOT NULL AND expiry_date <= :limitTime ORDER BY is_pinned DESC, created_at DESC")
    fun observeExpiringBefore(limitTime: Long): Flow<List<VaultItemEntity>>

    @Query("SELECT COUNT(*) FROM vault_items")
    suspend fun count(): Int

    /**
     * Full-text search backed by the FTS4 virtual table.
     */
    @Query("""
        SELECT vault_items.* FROM vault_items 
        INNER JOIN vault_items_fts ON vault_items.rowid = vault_items_fts.docid 
        WHERE vault_items_fts MATCH :query 
        ORDER BY vault_items.created_at DESC
    """)
    suspend fun searchFts(query: String): List<VaultItemEntity>

    @Query("""
        SELECT vault_items.* FROM vault_items 
        INNER JOIN vault_items_fts ON vault_items.rowid = vault_items_fts.docid 
        WHERE vault_items_fts MATCH :query 
        ORDER BY vault_items.created_at DESC
    """)
    fun observeSearchFts(query: String): Flow<List<VaultItemEntity>>

    /**
     * Filter a list of item IDs by metadata predicates (used after vector search).
     */
    @RawQuery
    suspend fun filterByMetadata(query: androidx.room.RoomRawQuery): List<String>

    @Query("UPDATE vault_items SET is_archived = :isArchived, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateArchiveState(id: String, isArchived: Boolean, updatedAt: Long)

    @Query("UPDATE vault_items SET is_pinned = :isPinned, updated_at = :updatedAt WHERE id = :id")
    suspend fun updatePinnedState(id: String, isPinned: Boolean, updatedAt: Long)

    @Query("UPDATE vault_items SET is_pinned = :isPinned, updated_at = :updatedAt WHERE id IN (:ids)")
    suspend fun updatePinnedStateForIds(ids: List<String>, isPinned: Boolean, updatedAt: Long)
}

private fun VaultItemEntity.preserveConcurrentUserEditsFrom(current: VaultItemEntity): VaultItemEntity {
    val claimStartedAt = current.enrichmentClaimedAt ?: return this
    val userEditedDuringClaim = current.userEditedAt?.let { it > claimStartedAt } == true
    if (!userEditedDuringClaim) return this
    return copy(
        title = current.title,
        summary = current.summary,
        userClassificationOverride = current.userClassificationOverride,
        userEditedAt = current.userEditedAt,
        parsedMetadata = current.parsedMetadata,
        lensTags = current.lensTags,
        expiryDate = current.expiryDate,
        secondaryAlertDate = current.secondaryAlertDate,
        recurringRule = current.recurringRule,
        locationName = current.locationName,
        needsReview = current.needsReview,
        isPinned = current.isPinned,
        isArchived = current.isArchived,
        isStealth = current.isStealth,
        userNotes = current.userNotes,
        customFields = current.customFields,
        targetPrice = current.targetPrice,
        affiliateUrl = current.affiliateUrl
    )
}

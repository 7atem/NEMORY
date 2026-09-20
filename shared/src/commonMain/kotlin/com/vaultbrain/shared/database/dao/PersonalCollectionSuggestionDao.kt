package com.vaultbrain.shared.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vaultbrain.shared.model.CollectionSuggestionStatus
import com.vaultbrain.shared.database.entity.PersonalCollectionMembershipEntity
import com.vaultbrain.shared.database.entity.PersonalCollectionSuggestionEntity
import kotlinx.coroutines.flow.Flow

/** Suggestion row joined with its collection name for display. */
data class SuggestionWithNameRow(
    @ColumnInfo(name = "collection_id") val collectionId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "collection_name") val collectionName: String,
    @ColumnInfo(name = "item_title") val itemTitle: String,
    @ColumnInfo(name = "confidence") val confidence: Float,
    @ColumnInfo(name = "status") val status: CollectionSuggestionStatus,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Dao
interface PersonalCollectionSuggestionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(suggestion: PersonalCollectionSuggestionEntity): Long

    @Query("""
        UPDATE personal_collection_suggestions
        SET confidence = :confidence, status = :status, updated_at = :updatedAt
        WHERE collection_id = :collectionId AND item_id = :itemId
    """)
    suspend fun update(
        collectionId: String,
        itemId: String,
        confidence: Float,
        status: CollectionSuggestionStatus,
        updatedAt: Long
    ): Int

    @Query("""
        UPDATE personal_collection_suggestions
        SET status = :status, updated_at = :updatedAt
        WHERE collection_id = :collectionId AND item_id = :itemId
    """)
    suspend fun setStatus(
        collectionId: String,
        itemId: String,
        status: CollectionSuggestionStatus,
        updatedAt: Long
    ): Int

    @Query("""
        SELECT * FROM personal_collection_suggestions
        WHERE collection_id = :collectionId AND item_id = :itemId
    """)
    suspend fun get(collectionId: String, itemId: String): PersonalCollectionSuggestionEntity?

    @Query("""
        SELECT s.*, c.name AS collection_name, v.title AS item_title
        FROM personal_collection_suggestions s
        INNER JOIN personal_collections c ON c.id = s.collection_id
        INNER JOIN vault_items v ON v.id = s.item_id
        WHERE s.item_id = :itemId AND s.status = 'SUGGESTED' AND c.archived_at IS NULL
        ORDER BY s.confidence DESC, c.name COLLATE NOCASE ASC
    """)
    fun observePendingForItem(itemId: String): Flow<List<SuggestionWithNameRow>>

    @Query("""
        SELECT s.*, c.name AS collection_name, v.title AS item_title
        FROM personal_collection_suggestions s
        INNER JOIN personal_collections c ON c.id = s.collection_id
        INNER JOIN vault_items v ON v.id = s.item_id
        WHERE s.collection_id = :collectionId AND s.status = 'SUGGESTED'
            AND v.is_archived = 0 AND v.is_stealth = 0
        ORDER BY s.confidence DESC, v.created_at DESC
    """)
    fun observePendingForCollection(collectionId: String): Flow<List<SuggestionWithNameRow>>

    /** One-shot read of the pairs the matcher must not recreate or duplicate. */
    @Query("SELECT * FROM personal_collection_suggestions WHERE item_id = :itemId")
    suspend fun getAllForItem(itemId: String): List<PersonalCollectionSuggestionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembership(membership: PersonalCollectionMembershipEntity): Long

    /**
     * Accepting a suggestion creates the user-authoritative membership (duplicates
     * ignored) and marks the suggestion ACCEPTED — atomically.
     */
    @Transaction
    suspend fun accept(
        collectionId: String,
        itemId: String,
        membership: PersonalCollectionMembershipEntity,
        now: Long
    ) {
        insertMembership(membership)
        setStatus(collectionId, itemId, CollectionSuggestionStatus.ACCEPTED, now)
    }
}

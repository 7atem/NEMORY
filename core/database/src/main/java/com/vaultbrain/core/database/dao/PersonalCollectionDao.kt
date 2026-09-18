package com.vaultbrain.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vaultbrain.core.common.model.PersonalCollectionSource
import com.vaultbrain.core.database.entity.PersonalCollectionEntity
import com.vaultbrain.core.database.entity.PersonalCollectionMembershipEntity
import com.vaultbrain.core.database.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow

data class PersonalCollectionRow(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean,
    @ColumnInfo(name = "source") val source: PersonalCollectionSource,
    @ColumnInfo(name = "item_count") val itemCount: Int
)

@Dao
interface PersonalCollectionDao {
    @Insert
    suspend fun insertCollection(collection: PersonalCollectionEntity)

    @Query("""
        SELECT c.*, COUNT(m.item_id) AS item_count
        FROM personal_collections c
        LEFT JOIN personal_collection_memberships m ON m.collection_id = c.id
        WHERE c.archived_at IS NULL
        GROUP BY c.id
        ORDER BY c.is_pinned DESC, c.updated_at DESC, c.name COLLATE NOCASE ASC
    """)
    fun observeActive(): Flow<List<PersonalCollectionRow>>

    @Query("""
        SELECT c.*, COUNT(m.item_id) AS item_count
        FROM personal_collections c
        LEFT JOIN personal_collection_memberships m ON m.collection_id = c.id
        WHERE c.archived_at IS NOT NULL
        GROUP BY c.id
        ORDER BY c.archived_at DESC, c.name COLLATE NOCASE ASC
    """)
    fun observeArchived(): Flow<List<PersonalCollectionRow>>

    @Query("""
        SELECT c.*, COUNT(m.item_id) AS item_count
        FROM personal_collections c
        LEFT JOIN personal_collection_memberships m ON m.collection_id = c.id
        WHERE c.id = :id
        GROUP BY c.id
    """)
    fun observeById(id: String): Flow<PersonalCollectionRow?>

    @Query("SELECT * FROM personal_collections WHERE id = :id")
    suspend fun getEntity(id: String): PersonalCollectionEntity?

    /** One-shot list of active (non-archived) collections for the matcher. */
    @Query("SELECT * FROM personal_collections WHERE archived_at IS NULL")
    suspend fun getActiveEntities(): List<PersonalCollectionEntity>

    /** One-shot list of active (non-archived) collections with pre-computed item counts. */
    @Query("""
        SELECT c.*, COUNT(m.item_id) AS item_count
        FROM personal_collections c
        LEFT JOIN personal_collection_memberships m ON m.collection_id = c.id
        WHERE c.archived_at IS NULL
        GROUP BY c.id
        ORDER BY c.is_pinned DESC, c.updated_at DESC, c.name COLLATE NOCASE ASC
    """)
    suspend fun getActiveRows(): List<PersonalCollectionRow>

    /** All collection ids, including archived — used to reconcile ObjectBox embeddings. */
    @Query("SELECT id FROM personal_collections")
    suspend fun getAllCollectionIds(): List<String>

    /** One-shot member items of a collection (semantic representation / RAG context). */
    @Query("""
        SELECT v.* FROM vault_items v
        INNER JOIN personal_collection_memberships m ON m.item_id = v.id
        WHERE m.collection_id = :collectionId AND v.is_archived = 0 AND v.is_stealth = 0
        ORDER BY v.is_pinned DESC, v.created_at DESC
    """)
    suspend fun getItemsOnce(collectionId: String): List<VaultItemEntity>

    /** Items in active collections whose name matches [query] (collection-aware search). */
    @Query("""
        SELECT DISTINCT v.* FROM vault_items v
        INNER JOIN personal_collection_memberships m ON m.item_id = v.id
        INNER JOIN personal_collections c ON c.id = m.collection_id
        WHERE c.archived_at IS NULL AND c.name LIKE '%' || :query || '%' COLLATE NOCASE
            AND v.is_archived = 0 AND v.is_stealth = 0
    """)
    fun observeItemsForCollectionNameLike(query: String): Flow<List<VaultItemEntity>>

    @Query("SELECT COUNT(*) FROM personal_collection_memberships WHERE collection_id = :collectionId")
    suspend fun countItems(collectionId: String): Int

    @Query("""
        SELECT c.*, COUNT(m.item_id) AS item_count
        FROM personal_collections c
        LEFT JOIN personal_collection_memberships m ON m.collection_id = c.id
        WHERE c.archived_at IS NULL AND c.name LIKE '%' || :query || '%'
        GROUP BY c.id
        ORDER BY c.is_pinned DESC, c.updated_at DESC, c.name COLLATE NOCASE ASC
    """)
    fun observeSearch(query: String): Flow<List<PersonalCollectionRow>>

    @Query("UPDATE personal_collections SET name = :name, updated_at = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long): Int

    @Query("UPDATE personal_collections SET archived_at = :archivedAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archivedAt: Long?, updatedAt: Long): Int

    @Query("UPDATE personal_collections SET is_pinned = :pinned, updated_at = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM personal_collections WHERE id = :id")
    suspend fun deletePermanently(id: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembership(membership: PersonalCollectionMembershipEntity): Long

    @Query("DELETE FROM personal_collection_memberships WHERE collection_id = :collectionId AND item_id = :itemId")
    suspend fun removeMembership(collectionId: String, itemId: String): Int

    @Query("SELECT COUNT(*) FROM personal_collection_memberships WHERE collection_id = :collectionId AND item_id = :itemId")
    suspend fun hasMembership(collectionId: String, itemId: String): Int

    @Query("""
        SELECT v.* FROM vault_items v
        INNER JOIN personal_collection_memberships m ON m.item_id = v.id
        WHERE m.collection_id = :collectionId AND v.is_archived = 0 AND v.is_stealth = 0
        ORDER BY v.is_pinned DESC, v.created_at DESC
    """)
    fun observeItems(collectionId: String): Flow<List<VaultItemEntity>>

    @Query("""
        SELECT c.*, COUNT(all_members.item_id) AS item_count
        FROM personal_collections c
        INNER JOIN personal_collection_memberships selected_members ON selected_members.collection_id = c.id
        LEFT JOIN personal_collection_memberships all_members ON all_members.collection_id = c.id
        WHERE selected_members.item_id = :itemId
        GROUP BY c.id
        ORDER BY c.archived_at IS NOT NULL, c.is_pinned DESC, c.name COLLATE NOCASE ASC
    """)
    fun observeForItem(itemId: String): Flow<List<PersonalCollectionRow>>
}

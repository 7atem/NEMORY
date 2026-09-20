package com.vaultbrain.shared.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vaultbrain.shared.database.entity.ItemEntityCrossRef
import com.vaultbrain.shared.database.entity.KnowledgeEntityEntity
import com.vaultbrain.shared.database.entity.RelationshipEntity

@Dao
abstract class KnowledgeGraphDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertEntity(entity: KnowledgeEntityEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertItemEntityCrossRef(crossRef: ItemEntityCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRelationship(relationship: RelationshipEntity)

    @Query("SELECT * FROM knowledge_entities WHERE id = :id")
    abstract suspend fun getEntity(id: String): KnowledgeEntityEntity?

    @Query("SELECT * FROM knowledge_entities WHERE type = :type AND name = :name")
    abstract suspend fun getEntityByTypeAndName(type: String, name: String): KnowledgeEntityEntity?

    @Query("DELETE FROM item_entities WHERE itemId = :itemId")
    abstract suspend fun deleteCrossRefsForItem(itemId: String)

    @Transaction
    open suspend fun replaceCrossRefsForItem(itemId: String, crossRefs: List<ItemEntityCrossRef>) {
        deleteCrossRefsForItem(itemId)
        crossRefs.forEach { insertItemEntityCrossRef(it) }
    }

    @Query("SELECT DISTINCT ie2.itemId FROM item_entities ie1 JOIN item_entities ie2 ON ie1.entityId = ie2.entityId JOIN vault_items v ON v.id = ie2.itemId WHERE ie1.itemId = :itemId AND ie2.itemId != :itemId AND v.is_archived = 0 AND v.is_stealth = 0")
    abstract suspend fun getItemIdsSharingEntities(itemId: String): List<String>

    @Query("SELECT r.* FROM relationships r JOIN vault_items s ON s.id = r.sourceItemId JOIN vault_items t ON t.id = r.targetItemId WHERE (r.sourceItemId = :itemId OR r.targetItemId = :itemId) AND s.is_archived = 0 AND s.is_stealth = 0 AND t.is_archived = 0 AND t.is_stealth = 0")
    abstract suspend fun getRelationshipsForItem(itemId: String): List<RelationshipEntity>

    @Query("SELECT r.* FROM relationships r JOIN vault_items s ON s.id = r.sourceItemId JOIN vault_items t ON t.id = r.targetItemId WHERE (r.sourceItemId IN (:itemIds) OR r.targetItemId IN (:itemIds)) AND s.is_archived = 0 AND s.is_stealth = 0 AND t.is_archived = 0 AND t.is_stealth = 0")
    abstract suspend fun getRelationshipsForItems(itemIds: List<String>): List<RelationshipEntity>

    @Query("DELETE FROM relationships WHERE sourceItemId = :itemId OR targetItemId = :itemId")
    abstract suspend fun deleteRelationshipsInvolving(itemId: String)

    @Transaction
    open suspend fun replaceRelationshipsInvolving(itemId: String, relationships: List<RelationshipEntity>) {
        deleteRelationshipsInvolving(itemId)
        relationships.forEach { insertRelationship(it) }
    }

    @Query("SELECT knowledge_entities.* FROM knowledge_entities INNER JOIN item_entities ON knowledge_entities.id = item_entities.entityId WHERE item_entities.itemId = :itemId")
    abstract suspend fun getEntitiesForItem(itemId: String): List<KnowledgeEntityEntity>
}

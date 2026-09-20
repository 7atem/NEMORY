package com.vaultbrain.shared.database.dao

import androidx.room.*
import com.vaultbrain.shared.database.entity.DerivedFactEntity

@Dao
abstract class DerivedFactDao {
    @Query("SELECT f.* FROM derived_facts f JOIN vault_items v ON v.id=f.sourceItemId WHERE f.sourceItemId=:itemId AND v.is_archived=0 AND v.is_stealth=0 AND v.updated_at=f.sourceUpdatedAt")
    abstract suspend fun forItem(itemId: String): List<DerivedFactEntity>

    @Query("DELETE FROM derived_facts WHERE sourceItemId=:itemId")
    abstract suspend fun deleteForItem(itemId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(facts: List<DerivedFactEntity>)

    @Transaction
    open suspend fun replace(itemId: String, facts: List<DerivedFactEntity>) {
        deleteForItem(itemId)
        insert(facts)
    }

    @Query("SELECT DISTINCT b.sourceItemId FROM derived_facts a JOIN vault_items source ON source.id=a.sourceItemId JOIN derived_facts b ON a.kind=b.kind AND a.value=b.value JOIN vault_items v ON v.id=b.sourceItemId WHERE a.sourceItemId=:itemId AND a.kind='entity' AND b.sourceItemId!=:itemId AND source.updated_at=a.sourceUpdatedAt AND source.is_archived=0 AND source.is_stealth=0 AND v.is_archived=0 AND v.is_stealth=0 AND v.updated_at=b.sourceUpdatedAt LIMIT 10")
    abstract suspend fun relatedIds(itemId: String): List<String>
}

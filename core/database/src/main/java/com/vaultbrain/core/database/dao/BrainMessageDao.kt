package com.vaultbrain.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vaultbrain.core.database.entity.BrainMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrainMessageDao {
    @Query("SELECT * FROM brain_messages ORDER BY created_at ASC")
    fun observeAll(): Flow<List<BrainMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: BrainMessageEntity)

    @Query("DELETE FROM brain_messages")
    suspend fun clear()

    @Query("DELETE FROM brain_messages WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

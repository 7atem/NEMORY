package com.vaultbrain.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaultbrain.core.database.entity.NotificationQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationQueueEntity)

    @Update
    suspend fun update(notification: NotificationQueueEntity)

    @Query("SELECT * FROM notification_queue WHERE is_delivered = 0 AND trigger_at <= :now ORDER BY trigger_at ASC")
    suspend fun getDue(now: Long): List<NotificationQueueEntity>

    @Query("SELECT * FROM notification_queue WHERE is_delivered = 0 ORDER BY trigger_at ASC")
    fun observePending(): Flow<List<NotificationQueueEntity>>

    @Query("DELETE FROM notification_queue WHERE item_id = :itemId")
    suspend fun deleteForItem(itemId: String)
}

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

    @Query("DELETE FROM notification_queue WHERE target_id = :targetId")
    suspend fun deleteForTarget(targetId: String)

    @Query("DELETE FROM notification_queue WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE notification_queue SET trigger_at = :triggerAt, is_delivered = 0 WHERE id = :id")
    suspend fun snoozeById(id: String, triggerAt: Long): Int

    @Query("UPDATE notification_queue SET is_delivered = 1 WHERE id = :id AND trigger_at = :triggerAt AND is_delivered = 0 AND ((target_type = 'VAULT_ITEM' AND EXISTS (SELECT 1 FROM vault_items v WHERE v.id = target_id AND v.is_archived = 0 AND v.is_stealth = 0)) OR (target_type = 'EXTERNAL_RECORD' AND EXISTS (SELECT 1 FROM external_records r JOIN external_connections c ON c.connector_id = r.connector_id AND c.account_id = r.account_id WHERE r.external_id = target_id AND r.is_resolved = 0 AND (r.expires_at IS NULL OR r.expires_at > :now) AND c.state = 'CONNECTED' AND ((channel_id = 'CALENDAR_EVENTS' AND r.connector_id = 'calendar') OR (channel_id = 'GMAIL_SYNC' AND r.connector_id = 'gmail')))))")
    suspend fun claimVisible(id: String, triggerAt: Long, now: Long): Int

    @Query("UPDATE notification_queue SET is_delivered = 0 WHERE id = :id AND trigger_at = :triggerAt")
    suspend fun releaseClaim(id: String, triggerAt: Long)
}

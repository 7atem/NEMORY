package com.vaultbrain.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaultbrain.core.common.model.VaultReminderStatus
import com.vaultbrain.core.database.entity.VaultReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultReminderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reminder: VaultReminderEntity): Long

    @Update
    suspend fun update(reminder: VaultReminderEntity): Int

    @Query("SELECT * FROM vault_reminders WHERE id = :id")
    suspend fun get(id: String): VaultReminderEntity?

    @Query(
        "SELECT * FROM vault_reminders WHERE vault_item_id = :vaultItemId AND due_at = :dueAt " +
            "AND status IN ('SCHEDULED', 'SNOOZED') LIMIT 1"
    )
    suspend fun getActiveForVaultItemAt(vaultItemId: String, dueAt: Long): VaultReminderEntity?

    @Query(
        "SELECT * FROM vault_reminders WHERE status IN ('SCHEDULED', 'SNOOZED') " +
            "ORDER BY due_at ASC"
    )
    fun observeActive(): Flow<List<VaultReminderEntity>>

    @Query(
        "SELECT * FROM vault_reminders WHERE status IN ('SCHEDULED', 'SNOOZED') " +
            "AND due_at <= :now ORDER BY due_at ASC"
    )
    suspend fun getDue(now: Long): List<VaultReminderEntity>

    @Query(
        "UPDATE vault_reminders SET status = :status, updated_at = :updatedAt WHERE id = :id"
    )
    suspend fun setStatus(id: String, status: VaultReminderStatus, updatedAt: Long): Int

    @Query("DELETE FROM vault_reminders WHERE id = :id")
    suspend fun delete(id: String): Int
}

package com.vaultbrain.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.vaultbrain.core.database.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {

    @Insert
    suspend fun insert(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>

    @Query("DELETE FROM audit_logs WHERE timestamp < :cutoff")
    suspend fun prune(cutoff: Long)
}

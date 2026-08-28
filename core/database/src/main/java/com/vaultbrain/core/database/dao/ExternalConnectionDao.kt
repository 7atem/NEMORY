package com.vaultbrain.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaultbrain.core.common.model.external.ConnectionState
import com.vaultbrain.core.database.entity.ExternalConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExternalConnectionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(connection: ExternalConnectionEntity): Long

    @Update
    suspend fun update(connection: ExternalConnectionEntity): Int

    @Query(
        """
        SELECT * FROM external_connections
        WHERE connector_id = :connectorId AND account_id = :accountId
        """
    )
    suspend fun get(connectorId: String, accountId: String): ExternalConnectionEntity?

    @Query("SELECT * FROM external_connections ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ExternalConnectionEntity>>

    @Query(
        """
        SELECT * FROM external_connections
        WHERE state = :state ORDER BY updated_at DESC
        """
    )
    fun observeByState(state: ConnectionState): Flow<List<ExternalConnectionEntity>>

    @Query(
        """
        UPDATE external_connections
        SET state = :state, last_error = :lastError, updated_at = :updatedAt
        WHERE connector_id = :connectorId AND account_id = :accountId
        """
    )
    suspend fun setState(
        connectorId: String,
        accountId: String,
        state: ConnectionState,
        lastError: String?,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE external_connections
        SET last_sync_at = :lastSyncAt, updated_at = :updatedAt
        WHERE connector_id = :connectorId AND account_id = :accountId
        """
    )
    suspend fun setLastSync(
        connectorId: String,
        accountId: String,
        lastSyncAt: Long,
        updatedAt: Long
    ): Int

    @Query(
        """
        DELETE FROM external_connections
        WHERE connector_id = :connectorId AND account_id = :accountId
        """
    )
    suspend fun delete(connectorId: String, accountId: String): Int
}

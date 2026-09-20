package com.vaultbrain.shared.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaultbrain.shared.model.external.ExternalRecordType
import com.vaultbrain.shared.model.external.ExternalSource
import com.vaultbrain.shared.database.entity.ExternalRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExternalRecordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: ExternalRecordEntity): Long

    @Update
    suspend fun update(record: ExternalRecordEntity): Int

    @Query(
        """
        SELECT * FROM external_records
        WHERE connector_id = :connectorId AND account_id = :accountId AND external_id = :externalId
        """
    )
    suspend fun get(
        connectorId: String,
        accountId: String,
        externalId: String
    ): ExternalRecordEntity?

    @Query(
        "SELECT * FROM external_records WHERE connector_id = :connectorId AND account_id = :accountId"
    )
    suspend fun getByConnection(connectorId: String, accountId: String): List<ExternalRecordEntity>

    @Query(
        "UPDATE external_records SET is_resolved = 1, updated_at = :updatedAt " +
            "WHERE connector_id = :connectorId AND account_id = :accountId AND external_id = :externalId"
    )
    suspend fun markResolved(
        connectorId: String,
        accountId: String,
        externalId: String,
        updatedAt: Long
    ): Int

    @Query("SELECT * FROM external_records ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ExternalRecordEntity>>

    @Query(
        """
        SELECT * FROM external_records
        WHERE source = :source AND is_resolved = 0
        ORDER BY COALESCE(due_at, start_at, created_at) DESC
        """
    )
    fun observeUnresolvedBySource(source: ExternalSource): Flow<List<ExternalRecordEntity>>

    @Query(
        """
        SELECT * FROM external_records
        WHERE record_type = :recordType AND is_resolved = 0
        ORDER BY COALESCE(due_at, start_at, created_at) DESC
        """
    )
    fun observeUnresolvedByType(recordType: ExternalRecordType): Flow<List<ExternalRecordEntity>>

    @Query(
        """
        SELECT * FROM external_records
        WHERE connector_id = :connectorId AND is_resolved = 0
        ORDER BY COALESCE(due_at, start_at, created_at) DESC
        """
    )
    fun observeUnresolvedByConnector(connectorId: String): Flow<List<ExternalRecordEntity>>

    @Query(
        """
        DELETE FROM external_records
        WHERE connector_id = :connectorId AND account_id = :accountId AND external_id = :externalId
        """
    )
    suspend fun delete(
        connectorId: String,
        accountId: String,
        externalId: String
    ): Int

    @Query(
        """
        DELETE FROM external_records
        WHERE connector_id = :connectorId AND account_id = :accountId
        """
    )
    suspend fun deleteByConnection(connectorId: String, accountId: String): Int

    @Query(
        """
        DELETE FROM external_records
        WHERE expires_at IS NOT NULL AND expires_at < :now
        """
    )
    suspend fun deleteExpired(now: Long): Int

    @Query("DELETE FROM external_records")
    suspend fun deleteAll(): Int
}

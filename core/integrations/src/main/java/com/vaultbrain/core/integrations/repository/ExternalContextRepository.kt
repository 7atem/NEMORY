package com.vaultbrain.core.integrations.repository

import com.vaultbrain.core.common.model.external.ConnectionState
import com.vaultbrain.core.common.model.external.ConnectorCapability
import com.vaultbrain.core.common.model.external.ExternalRecordType
import com.vaultbrain.core.common.model.external.ExternalSource
import com.vaultbrain.core.database.dao.ExternalConnectionDao
import com.vaultbrain.core.database.dao.ExternalRecordDao
import com.vaultbrain.core.database.entity.ExternalConnectionEntity
import com.vaultbrain.core.database.entity.ExternalRecordEntity
import com.vaultbrain.core.integrations.model.ConnectionRecord
import com.vaultbrain.core.integrations.model.ExternalRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for external context storage.
 *
 * Reads/writes the Room-backed [external_records] and [external_connections] tables
 * and maps between the typed domain models and the storage entities.
 */
@Singleton
class ExternalContextRepository @Inject constructor(
    private val recordDao: ExternalRecordDao,
    private val connectionDao: ExternalConnectionDao
) {

    fun observeRecords(): Flow<List<ExternalRecord>> =
        recordDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeConnections(): Flow<List<ConnectionRecord>> =
        connectionDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeRecordsBySource(source: ExternalSource): Flow<List<ExternalRecord>> =
        recordDao.observeUnresolvedBySource(source).map { list -> list.map { it.toDomain() } }

    fun observeRecordsByType(recordType: ExternalRecordType): Flow<List<ExternalRecord>> =
        recordDao.observeUnresolvedByType(recordType).map { list -> list.map { it.toDomain() } }

    fun observeRecordsByConnector(connectorId: String): Flow<List<ExternalRecord>> =
        recordDao.observeUnresolvedByConnector(connectorId).map { list -> list.map { it.toDomain() } }

    suspend fun getRecord(
        connectorId: String,
        accountId: String,
        externalId: String
    ): ExternalRecord? = recordDao.get(connectorId, accountId, externalId)?.toDomain()

    suspend fun getConnection(
        connectorId: String,
        accountId: String
    ): ConnectionRecord? = connectionDao.get(connectorId, accountId)?.toDomain()

    suspend fun upsertRecord(record: ExternalRecord) {
        val entity = record.toEntity()
        if (recordDao.insert(entity) == -1L) {
            recordDao.update(entity)
        }
    }

    suspend fun upsertConnection(connection: ConnectionRecord) {
        val entity = connection.toEntity()
        if (connectionDao.insert(entity) == -1L) {
            connectionDao.update(entity)
        }
    }

    suspend fun updateConnectionState(
        connectorId: String,
        accountId: String,
        state: ConnectionState,
        lastError: String? = null
    ) {
        connectionDao.setState(
            connectorId = connectorId,
            accountId = accountId,
            state = state,
            lastError = lastError,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteRecord(connectorId: String, accountId: String, externalId: String) {
        recordDao.delete(connectorId, accountId, externalId)
    }

    suspend fun deleteConnection(connectorId: String, accountId: String) {
        connectionDao.delete(connectorId, accountId)
    }

    suspend fun deleteExpired(now: Long = System.currentTimeMillis()): Int =
        recordDao.deleteExpired(now)

    suspend fun clearAll() {
        recordDao.deleteAll()
        // Connections are per-row; callers should delete them individually.
    }
}

private fun ExternalRecordEntity.toDomain(): ExternalRecord = ExternalRecord(
    connectorId = connectorId,
    accountId = accountId,
    externalId = externalId,
    source = source,
    recordType = recordType,
    retention = retention,
    title = title,
    description = description,
    startAt = startAt,
    endAt = endAt,
    dueAt = dueAt,
    sensitivity = sensitivity,
    payload = payload ?: emptyMap(),
    deepLinkUri = deepLinkUri,
    hash = hash,
    createdAt = createdAt,
    updatedAt = updatedAt,
    expiresAt = expiresAt,
    seenAt = seenAt,
    isResolved = isResolved
)

private fun ExternalRecord.toEntity(): ExternalRecordEntity = ExternalRecordEntity(
    connectorId = connectorId,
    accountId = accountId,
    externalId = externalId,
    source = source,
    recordType = recordType,
    retention = retention,
    title = title,
    description = description,
    startAt = startAt,
    endAt = endAt,
    dueAt = dueAt,
    sensitivity = sensitivity,
    payload = payload.takeIf { it.isNotEmpty() },
    deepLinkUri = deepLinkUri,
    hash = hash,
    createdAt = createdAt,
    updatedAt = updatedAt,
    expiresAt = expiresAt,
    seenAt = seenAt,
    isResolved = isResolved
)

private fun ExternalConnectionEntity.toDomain(): ConnectionRecord = ConnectionRecord(
    connectorId = connectorId,
    accountId = accountId,
    state = state,
    accountName = accountName,
    accountIconUri = accountIconUri,
    capabilities = capabilities.toCapabilities(),
    metadataJson = metadataJson,
    lastSyncAt = lastSyncAt,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun ConnectionRecord.toEntity(): ExternalConnectionEntity = ExternalConnectionEntity(
    connectorId = connectorId,
    accountId = accountId,
    state = state,
    accountName = accountName,
    accountIconUri = accountIconUri,
    capabilities = capabilities.map { it.name }.toSet(),
    metadataJson = metadataJson,
    lastSyncAt = lastSyncAt,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun Set<String>.toCapabilities(): Set<ConnectorCapability> =
    mapNotNull { name ->
        runCatching { enumValueOf<ConnectorCapability>(name) }.getOrNull()
    }.toSet()

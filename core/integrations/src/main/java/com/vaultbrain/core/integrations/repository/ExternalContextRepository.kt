package com.vaultbrain.core.integrations.repository

import com.vaultbrain.shared.model.external.ConnectionState
import com.vaultbrain.shared.model.external.ConnectorCapability
import com.vaultbrain.shared.model.external.ExternalRecordType
import com.vaultbrain.shared.model.external.ExternalSource
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.shared.database.dao.ExternalConnectionDao
import com.vaultbrain.shared.database.dao.ExternalRecordDao
import com.vaultbrain.shared.database.entity.ExternalConnectionEntity
import com.vaultbrain.shared.database.entity.ExternalRecordEntity
import com.vaultbrain.core.integrations.model.ConnectionRecord
import com.vaultbrain.core.integrations.model.ExternalRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        combine(recordDao.observeAll(), DecoySessionState.isDecoy) { list, isDecoy ->
            if (isDecoy) emptyList() else list.map { it.toDomain() }
        }

    fun observeConnections(): Flow<List<ConnectionRecord>> =
        combine(connectionDao.observeAll(), DecoySessionState.isDecoy) { list, isDecoy ->
            if (isDecoy) emptyList() else list.map { it.toDomain() }
        }

    fun observeRecordsBySource(source: ExternalSource): Flow<List<ExternalRecord>> =
        combine(recordDao.observeUnresolvedBySource(source), DecoySessionState.isDecoy) { list, isDecoy ->
            if (isDecoy) emptyList() else list.map { it.toDomain() }
        }

    fun observeRecordsByType(recordType: ExternalRecordType): Flow<List<ExternalRecord>> =
        combine(recordDao.observeUnresolvedByType(recordType), DecoySessionState.isDecoy) { list, isDecoy ->
            if (isDecoy) emptyList() else list.map { it.toDomain() }
        }

    fun observeRecordsByConnector(connectorId: String): Flow<List<ExternalRecord>> =
        combine(recordDao.observeUnresolvedByConnector(connectorId), DecoySessionState.isDecoy) { list, isDecoy ->
            if (isDecoy) emptyList() else list.map { it.toDomain() }
        }

    suspend fun getRecord(
        connectorId: String,
        accountId: String,
        externalId: String
    ): ExternalRecord? = if (DecoySessionState.isDecoy.value) null
        else recordDao.get(connectorId, accountId, externalId)?.toDomain()

    suspend fun getRecordsForConnection(connectorId: String, accountId: String): List<ExternalRecord> =
        if (DecoySessionState.isDecoy.value) emptyList()
        else recordDao.getByConnection(connectorId, accountId).map { it.toDomain() }

    suspend fun getConnection(
        connectorId: String,
        accountId: String
    ): ConnectionRecord? = if (DecoySessionState.isDecoy.value) null
        else connectionDao.get(connectorId, accountId)?.toDomain()

    suspend fun upsertRecord(record: ExternalRecord) {
        if (DecoySessionState.isDecoy.value) return
        val entity = record.toEntity()
        if (recordDao.insert(entity) == -1L) {
            recordDao.update(entity)
        }
    }

    suspend fun upsertConnection(connection: ConnectionRecord) {
        if (DecoySessionState.isDecoy.value) return
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
        if (DecoySessionState.isDecoy.value) return
        connectionDao.setState(
            connectorId = connectorId,
            accountId = accountId,
            state = state,
            lastError = lastError,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun updateLastSync(connectorId: String, accountId: String, at: Long) {
        if (DecoySessionState.isDecoy.value) return
        connectionDao.setLastSync(connectorId, accountId, at, at)
    }

    suspend fun markResolved(connectorId: String, accountId: String, externalId: String) {
        if (DecoySessionState.isDecoy.value) return
        recordDao.markResolved(connectorId, accountId, externalId, System.currentTimeMillis())
    }

    suspend fun deleteRecord(connectorId: String, accountId: String, externalId: String) {
        if (DecoySessionState.isDecoy.value) return
        recordDao.delete(connectorId, accountId, externalId)
    }

    suspend fun deleteRecordsForConnection(connectorId: String, accountId: String): Int {
        if (DecoySessionState.isDecoy.value) return 0
        return recordDao.deleteByConnection(connectorId, accountId)
    }

    suspend fun deleteConnection(connectorId: String, accountId: String) {
        if (DecoySessionState.isDecoy.value) return
        connectionDao.delete(connectorId, accountId)
    }

    suspend fun deleteExpired(now: Long = System.currentTimeMillis()): Int {
        if (DecoySessionState.isDecoy.value) return 0
        return recordDao.deleteExpired(now)
    }

    suspend fun clearAll() {
        if (DecoySessionState.isDecoy.value) return
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

package com.vaultbrain.shared.database.dao

import com.vaultbrain.shared.model.external.ExternalRecordType
import com.vaultbrain.shared.model.external.ExternalSource
import com.vaultbrain.shared.database.entity.ExternalRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake for [ExternalRecordDao] tests.
 */
class FakeExternalRecordDao : ExternalRecordDao {

    private val records = mutableListOf<ExternalRecordEntity>()
    private val _all = MutableStateFlow<List<ExternalRecordEntity>>(emptyList())

    override fun observeAll(): Flow<List<ExternalRecordEntity>> = _all.asStateFlow()

    override fun observeUnresolvedBySource(source: ExternalSource): Flow<List<ExternalRecordEntity>> =
        _all.map { list ->
            list.filter { it.source == source && !it.isResolved }
                .sortedByDescending { it.dueAt ?: it.startAt ?: it.createdAt }
        }

    override fun observeUnresolvedByType(recordType: ExternalRecordType): Flow<List<ExternalRecordEntity>> =
        _all.map { list ->
            list.filter { it.recordType == recordType && !it.isResolved }
                .sortedByDescending { it.dueAt ?: it.startAt ?: it.createdAt }
        }

    override fun observeUnresolvedByConnector(connectorId: String): Flow<List<ExternalRecordEntity>> =
        _all.map { list ->
            list.filter { it.connectorId == connectorId && !it.isResolved }
                .sortedByDescending { it.dueAt ?: it.startAt ?: it.createdAt }
        }

    override suspend fun insert(record: ExternalRecordEntity): Long {
        if (records.any { it.sameKey(record) }) return -1L
        records.add(record)
        emit()
        return 1L
    }

    override suspend fun update(record: ExternalRecordEntity): Int {
        val index = records.indexOfFirst { it.sameKey(record) }
        if (index == -1) return 0
        records[index] = record
        emit()
        return 1
    }

    override suspend fun get(
        connectorId: String,
        accountId: String,
        externalId: String
    ): ExternalRecordEntity? = records.find {
        it.connectorId == connectorId && it.accountId == accountId && it.externalId == externalId
    }

    override suspend fun getByConnection(
        connectorId: String,
        accountId: String
    ): List<ExternalRecordEntity> = records.filter {
        it.connectorId == connectorId && it.accountId == accountId
    }

    override suspend fun markResolved(
        connectorId: String,
        accountId: String,
        externalId: String,
        updatedAt: Long
    ): Int {
        val index = records.indexOfFirst {
            it.connectorId == connectorId && it.accountId == accountId && it.externalId == externalId
        }
        if (index < 0) return 0
        records[index] = records[index].copy(isResolved = true, updatedAt = updatedAt)
        emit()
        return 1
    }

    override suspend fun delete(
        connectorId: String,
        accountId: String,
        externalId: String
    ): Int {
        val removed = records.removeAll {
            it.connectorId == connectorId && it.accountId == accountId && it.externalId == externalId
        }
        if (removed) emit()
        return if (removed) 1 else 0
    }

    override suspend fun deleteByConnection(connectorId: String, accountId: String): Int {
        val count = records.count { it.connectorId == connectorId && it.accountId == accountId }
        records.removeAll { it.connectorId == connectorId && it.accountId == accountId }
        if (count > 0) emit()
        return count
    }

    override suspend fun deleteExpired(now: Long): Int {
        val expired = records.filter { record ->
            val expires = record.expiresAt
            expires != null && expires < now
        }
        records.removeAll { record ->
            val expires = record.expiresAt
            expires != null && expires < now
        }
        if (expired.isNotEmpty()) emit()
        return expired.size
    }

    override suspend fun deleteAll(): Int {
        val count = records.size
        records.clear()
        emit()
        return count
    }

    private fun ExternalRecordEntity.sameKey(other: ExternalRecordEntity): Boolean =
        connectorId == other.connectorId && accountId == other.accountId && externalId == other.externalId

    private fun emit() {
        _all.value = records.toList()
    }
}

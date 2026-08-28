package com.vaultbrain.core.database.dao

import com.vaultbrain.core.common.model.external.ConnectionState
import com.vaultbrain.core.database.entity.ExternalConnectionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake for [ExternalConnectionDao] tests.
 */
class FakeExternalConnectionDao : ExternalConnectionDao {

    private val connections = mutableListOf<ExternalConnectionEntity>()
    private val _all = MutableStateFlow<List<ExternalConnectionEntity>>(emptyList())

    override fun observeAll(): Flow<List<ExternalConnectionEntity>> = _all.asStateFlow()

    override fun observeByState(state: ConnectionState): Flow<List<ExternalConnectionEntity>> =
        _all.map { list -> list.filter { it.state == state } }

    override suspend fun insert(connection: ExternalConnectionEntity): Long {
        if (connections.any { it.sameKey(connection) }) return -1L
        connections.add(connection)
        emit()
        return 1L
    }

    override suspend fun update(connection: ExternalConnectionEntity): Int {
        val index = connections.indexOfFirst { it.sameKey(connection) }
        if (index == -1) return 0
        connections[index] = connection
        emit()
        return 1
    }

    override suspend fun get(connectorId: String, accountId: String): ExternalConnectionEntity? =
        connections.find { it.connectorId == connectorId && it.accountId == accountId }

    override suspend fun setState(
        connectorId: String,
        accountId: String,
        state: ConnectionState,
        lastError: String?,
        updatedAt: Long
    ): Int {
        val index = connections.indexOfFirst { it.connectorId == connectorId && it.accountId == accountId }
        if (index == -1) return 0
        connections[index] = connections[index].copy(state = state, lastError = lastError, updatedAt = updatedAt)
        emit()
        return 1
    }

    override suspend fun setLastSync(
        connectorId: String,
        accountId: String,
        lastSyncAt: Long,
        updatedAt: Long
    ): Int {
        val index = connections.indexOfFirst { it.connectorId == connectorId && it.accountId == accountId }
        if (index == -1) return 0
        connections[index] = connections[index].copy(lastSyncAt = lastSyncAt, updatedAt = updatedAt)
        emit()
        return 1
    }

    override suspend fun delete(connectorId: String, accountId: String): Int {
        val removed = connections.removeAll { it.connectorId == connectorId && it.accountId == accountId }
        if (removed) emit()
        return if (removed) 1 else 0
    }

    private fun ExternalConnectionEntity.sameKey(other: ExternalConnectionEntity): Boolean =
        connectorId == other.connectorId && accountId == other.accountId

    private fun emit() {
        _all.value = connections.toList()
    }
}

package com.vaultbrain.core.integrations.repository

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.common.model.external.ConnectionState
import com.vaultbrain.core.common.model.external.ConnectorCapability
import com.vaultbrain.core.common.model.external.ExternalRecordType
import com.vaultbrain.core.common.model.external.ExternalSource
import com.vaultbrain.core.database.dao.FakeExternalConnectionDao
import com.vaultbrain.core.database.dao.FakeExternalRecordDao
import com.vaultbrain.core.integrations.model.ConnectionRecord
import com.vaultbrain.core.integrations.model.ExternalRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ExternalContextRepositoryTest {

    private val recordDao = FakeExternalRecordDao()
    private val connectionDao = FakeExternalConnectionDao()
    private val repository = ExternalContextRepository(recordDao, connectionDao)

    @Test
    fun `upsertRecord inserts new record and exposes it as domain`() = runTest {
        val record = sampleRecord()

        repository.upsertRecord(record)

        val observed = repository.observeRecords().first()
        assertThat(observed).hasSize(1)
        assertThat(observed[0].externalId).isEqualTo("evt-1")
        assertThat(observed[0].source).isEqualTo(ExternalSource.CALENDAR)
    }

    @Test
    fun `upsertRecord updates existing record by composite key`() = runTest {
        val record = sampleRecord(title = "Old title")
        repository.upsertRecord(record)

        repository.upsertRecord(record.copy(title = "New title"))

        val observed = repository.observeRecords().first()
        assertThat(observed).hasSize(1)
        assertThat(observed[0].title).isEqualTo("New title")
    }

    @Test
    fun `upsertConnection maps capabilities round trip`() = runTest {
        val connection = sampleConnection(capabilities = setOf(ConnectorCapability.READ, ConnectorCapability.SEARCH))

        repository.upsertConnection(connection)

        val observed = repository.observeConnections().first()
        assertThat(observed[0].capabilities).containsExactly(
            ConnectorCapability.READ,
            ConnectorCapability.SEARCH
        )
    }

    @Test
    fun `updateConnectionState persists state and error`() = runTest {
        repository.upsertConnection(sampleConnection())

        repository.updateConnectionState("calendar", "work", ConnectionState.ERROR, "auth failed")

        val observed = repository.observeConnections().first().first()
        assertThat(observed.state).isEqualTo(ConnectionState.ERROR)
        assertThat(observed.lastError).isEqualTo("auth failed")
    }

    @Test
    fun `deleteRecord removes only the matching row`() = runTest {
        repository.upsertRecord(sampleRecord(externalId = "a"))
        repository.upsertRecord(sampleRecord(externalId = "b"))

        repository.deleteRecord("calendar", "work", "a")

        val observed = repository.observeRecords().first()
        assertThat(observed).hasSize(1)
        assertThat(observed[0].externalId).isEqualTo("b")
    }

    @Test
    fun `deleteExpired removes only records with passed expiry`() = runTest {
        val now = 1_000_000L
        repository.upsertRecord(sampleRecord(externalId = "old", expiresAt = now - 1))
        repository.upsertRecord(sampleRecord(externalId = "fresh", expiresAt = now + 1))

        repository.deleteExpired(now)

        val observed = repository.observeRecords().first()
        assertThat(observed).hasSize(1)
        assertThat(observed[0].externalId).isEqualTo("fresh")
    }

    private fun sampleRecord(
        externalId: String = "evt-1",
        title: String = "Meeting",
        expiresAt: Long? = null
    ): ExternalRecord = ExternalRecord(
        connectorId = "calendar",
        accountId = "work",
        externalId = externalId,
        source = ExternalSource.CALENDAR,
        recordType = ExternalRecordType.EVENT,
        title = title,
        expiresAt = expiresAt
    )

    private fun sampleConnection(
        capabilities: Set<ConnectorCapability> = emptySet()
    ): ConnectionRecord = ConnectionRecord(
        connectorId = "calendar",
        accountId = "work",
        state = ConnectionState.CONNECTED,
        capabilities = capabilities
    )
}

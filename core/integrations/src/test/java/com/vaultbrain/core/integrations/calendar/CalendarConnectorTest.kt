package com.vaultbrain.core.integrations.calendar

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.common.model.external.ConnectionState
import com.vaultbrain.core.database.dao.FakeExternalConnectionDao
import com.vaultbrain.core.database.dao.FakeExternalRecordDao
import com.vaultbrain.core.integrations.model.ExternalAction
import com.vaultbrain.core.integrations.model.SyncRequest
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CalendarConnectorTest {
    private val source = FakeCalendarDataSource()
    private val repository = ExternalContextRepository(FakeExternalRecordDao(), FakeExternalConnectionDao())
    private val alertManager: com.vaultbrain.core.notifications.UnifiedAlertManager = io.mockk.mockk(relaxed = true)
    private val connector = CalendarConnector(source, repository, alertManager)

    @Test
    fun `sync normalizes one-time all-day recurring timezone and multiple calendars`() = runTest {
        val now = System.currentTimeMillis()
        source.selected = setOf(1, 2)
        source.rows = listOf(
            row(10, 1, now, allDay = false, timezone = "Africa/Cairo"),
            row(11, 2, now + 1_000, allDay = true, recurrence = "FREQ=DAILY", timezone = "UTC")
        )
        val connection = connector.connect().getOrThrow()
        val result = connector.sync(connection, SyncRequest()).getOrThrow()

        assertThat(result.recordsAdded).isEqualTo(2)
        assertThat(source.lastRequestedCalendarIds).containsExactly(1L, 2L)
        val records = repository.getRecordsForConnection(CalendarConnector.CONNECTOR_ID, CalendarConnector.ACCOUNT_ID)
        assertThat(records).hasSize(2)
        assertThat(records[0].payload["timezone"]).isEqualTo("Africa/Cairo")
        assertThat(records[1].payload["all_day"]).isEqualTo("true")
        assertThat(records[1].payload["recurrence"]).isEqualTo("FREQ=DAILY")
        assertThat(records[1].externalId).contains(":")
    }

    @Test
    fun `duplicate sync updates and deleted instance is removed`() = runTest {
        val now = System.currentTimeMillis()
        source.selected = setOf(1)
        source.rows = listOf(row(20, 1, now))
        val connection = connector.connect().getOrThrow()
        connector.sync(connection, SyncRequest()).getOrThrow()

        val second = connector.sync(connection, SyncRequest()).getOrThrow()
        assertThat(second.recordsAdded).isEqualTo(0)
        assertThat(second.recordsUpdated).isEqualTo(1)

        source.rows = emptyList()
        val deleted = connector.sync(connection, SyncRequest()).getOrThrow()
        assertThat(deleted.recordsRemoved).isEqualTo(1)
        assertThat(repository.getRecordsForConnection(CalendarConnector.CONNECTOR_ID, CalendarConnector.ACCOUNT_ID))
            .isEmpty()
    }

    @Test
    fun `permission denied and revoked update state and clear cached context`() = runTest {
        source.permission = false
        assertThat(connector.connect().isFailure).isTrue()
        val stored = repository.getConnection(CalendarConnector.CONNECTOR_ID, CalendarConnector.ACCOUNT_ID)
        assertThat(stored?.state).isEqualTo(ConnectionState.PERMISSION_REVOKED)

        source.permission = true
        val connection = connector.connect().getOrThrow()
        source.selected = setOf(1)
        source.rows = listOf(row(30, 1, System.currentTimeMillis()))
        connector.sync(connection, SyncRequest()).getOrThrow()
        source.permission = false
        assertThat(connector.sync(connection, SyncRequest()).isFailure).isTrue()
        assertThat(repository.getConnection(CalendarConnector.CONNECTOR_ID, CalendarConnector.ACCOUNT_ID)?.state)
            .isEqualTo(ConnectionState.PERMISSION_REVOKED)
        source.permission = true
        assertThat(repository.getRecordsForConnection(CalendarConnector.CONNECTOR_ID, CalendarConnector.ACCOUNT_ID))
            .isEmpty()
    }

    @Test
    fun `disconnect clears selected calendars and locally indexed events`() = runTest {
        source.selected = setOf(1)
        source.rows = listOf(row(31, 1, System.currentTimeMillis()))
        val connection = connector.connect().getOrThrow()
        connector.sync(connection, SyncRequest()).getOrThrow()

        connector.disconnect(connection).getOrThrow()

        assertThat(source.selected).isEmpty()
        assertThat(repository.getRecordsForConnection(CalendarConnector.CONNECTOR_ID, CalendarConnector.ACCOUNT_ID))
            .isEmpty()
        assertThat(repository.getConnection(CalendarConnector.CONNECTOR_ID, CalendarConnector.ACCOUNT_ID)?.state)
            .isEqualTo(ConnectionState.DISCONNECTED)
    }

    @Test
    fun `open original and create event delegate to the user-confirmed data source`() = runTest {
        val opened = connector.perform(ExternalAction.OpenOriginal("content://calendar/events/42")).getOrThrow()
        assertThat(opened.success).isTrue()
        assertThat(source.openedUri).isEqualTo("content://calendar/events/42")

        val draft = CalendarEventDraft("Dentist", "Checkup", "Clinic", 100, 200, false)
        assertThat(connector.createEvent(draft)).isTrue()
        assertThat(source.createdDraft).isEqualTo(draft)
    }

    private fun row(
        eventId: Long,
        calendarId: Long,
        start: Long,
        allDay: Boolean = false,
        recurrence: String? = null,
        timezone: String? = null
    ) = CalendarEventRow(
        eventId = eventId,
        calendarId = calendarId,
        title = "Event $eventId",
        description = "Description",
        location = "Cairo",
        startAt = start,
        endAt = start + 3_600_000,
        allDay = allDay,
        recurrenceRule = recurrence,
        timezone = timezone
    )
}

private class FakeCalendarDataSource : CalendarDataSource {
    var available = true
    var permission = true
    var selected: Set<Long> = emptySet()
    var rows: List<CalendarEventRow> = emptyList()
    var lastRequestedCalendarIds: Set<Long> = emptySet()
    var openedUri: String? = null
    var createdDraft: CalendarEventDraft? = null

    override fun isAvailable() = available
    override fun hasReadPermission() = permission
    override fun selectedCalendarIds() = selected
    override fun setSelectedCalendarIds(ids: Set<Long>) { selected = ids }
    override suspend fun calendars() = listOf(CalendarInfo(1, "Personal"), CalendarInfo(2, "Work"))
    override suspend fun events(calendarIds: Set<Long>, startAt: Long, endAt: Long): List<CalendarEventRow> {
        lastRequestedCalendarIds = calendarIds
        return rows.filter { it.calendarId in calendarIds }
    }
    override fun open(uri: String): Boolean { openedUri = uri; return true }
    override fun createEvent(draft: CalendarEventDraft): Boolean { createdDraft = draft; return true }
}

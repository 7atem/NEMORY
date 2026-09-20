package com.vaultbrain.core.integrations.calendar

import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import com.vaultbrain.shared.model.external.ConnectionState
import com.vaultbrain.shared.model.external.ConnectorCapability
import com.vaultbrain.shared.model.external.ExternalRecordType
import com.vaultbrain.shared.model.external.ExternalSource
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.integrations.connector.ExternalConnector
import com.vaultbrain.core.integrations.model.ConnectionRecord
import com.vaultbrain.core.integrations.model.ConnectorAvailability
import com.vaultbrain.core.integrations.model.ContextQuery
import com.vaultbrain.core.integrations.model.ExternalAction
import com.vaultbrain.core.integrations.model.ExternalActionResult
import com.vaultbrain.core.integrations.model.ExternalRecord
import com.vaultbrain.core.integrations.model.SyncRequest
import com.vaultbrain.core.integrations.model.SyncResult
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarConnector @Inject constructor(
    private val dataSource: CalendarDataSource,
    private val repository: ExternalContextRepository,
    private val alertManager: com.vaultbrain.core.notifications.UnifiedAlertManager
) : ExternalConnector {
    override val connectorId = CONNECTOR_ID
    override val displayName = "Calendar"
    override val capabilities = setOf(
        ConnectorCapability.READ,
        ConnectorCapability.SEARCH,
        ConnectorCapability.CREATE,
        ConnectorCapability.OPEN_ORIGINAL,
        ConnectorCapability.BACKGROUND_SYNC
    )

    override suspend fun availability(): ConnectorAvailability {
        val available = dataSource.isAvailable()
        val granted = available && dataSource.hasReadPermission()
        return ConnectorAvailability(
            connectorId = connectorId,
            displayName = displayName,
            isAvailable = available,
            state = when {
                !available -> ConnectionState.UNAVAILABLE
                granted -> ConnectionState.CONNECTED
                else -> ConnectionState.DISCONNECTED
            },
            capabilities = capabilities,
            accountIds = if (granted) listOf(ACCOUNT_ID) else emptyList()
        )
    }

    override suspend fun connect(accountId: String?): Result<ConnectionRecord> {
        if (!dataSource.isAvailable()) return Result.failure(IllegalStateException("Calendar provider unavailable"))
        if (!dataSource.hasReadPermission()) {
            repository.upsertConnection(connection(ConnectionState.PERMISSION_REVOKED, "Calendar permission required"))
            return Result.failure(SecurityException("Calendar permission required"))
        }
        val connected = connection(ConnectionState.CONNECTED)
        repository.upsertConnection(connected)
        return Result.success(connected)
    }

    override suspend fun disconnect(connection: ConnectionRecord): Result<Unit> {
        dataSource.setSelectedCalendarIds(emptySet())
        repository.deleteRecordsForConnection(CONNECTOR_ID, ACCOUNT_ID)
        repository.upsertConnection(connection(ConnectionState.DISCONNECTED))
        return Result.success(Unit)
    }

    suspend fun calendars(): List<CalendarInfo> = dataSource.calendars()

    fun selectedCalendarIds(): Set<Long> = dataSource.selectedCalendarIds()

    fun setSelectedCalendarIds(ids: Set<Long>) = dataSource.setSelectedCalendarIds(ids)

    fun createEvent(draft: CalendarEventDraft): Boolean = dataSource.createEvent(draft)

    override suspend fun sync(connection: ConnectionRecord, request: SyncRequest): Result<SyncResult> {
        if (DecoySessionState.isDecoy.value) return Result.success(emptySync())
        if (!dataSource.hasReadPermission()) {
            repository.updateConnectionState(CONNECTOR_ID, ACCOUNT_ID, ConnectionState.PERMISSION_REVOKED)
            repository.deleteRecordsForConnection(CONNECTOR_ID, ACCOUNT_ID)
            return Result.failure(SecurityException("Calendar permission revoked"))
        }
        return runCatching {
            val now = System.currentTimeMillis()
            val windowStart = now - PAST_WINDOW_MS
            val windowEnd = now + FUTURE_WINDOW_MS
            val selectedIds = dataSource.selectedCalendarIds()
            val rows = dataSource.events(selectedIds, windowStart, windowEnd)
                .filterNot(CalendarEventRow::deleted)
                .distinctBy { externalId(it) }
            val existing = repository.getRecordsForConnection(CONNECTOR_ID, ACCOUNT_ID)
                .associateBy(ExternalRecord::externalId)
            var added = 0
            var updated = 0
            val seen = mutableSetOf<String>()
            rows.forEach { row ->
                val record = normalize(row)
                seen += record.externalId
                if (existing.containsKey(record.externalId)) updated++ else added++
                repository.upsertRecord(record)

                // Schedule notification for upcoming calendar event (15 minutes prior)
                if (row.startAt > now) {
                    val alertTime = row.startAt - 15 * 60 * 1000L
                    if (alertTime > now) {
                        val locationSnippet = row.location?.takeIf(String::isNotBlank)?.let { " ($it)" } ?: ""
                        alertManager.scheduleForExternal(
                            targetId = record.externalId,
                            triggerAt = alertTime,
                            title = record.title ?: "Calendar Event",
                            body = "Event starting in 15 minutes$locationSnippet",
                            channelId = com.vaultbrain.core.notifications.UnifiedAlertManager.CHANNEL_CALENDAR
                        )
                    }
                }
            }
            val stale = existing.values.filter { record ->
                val anchor = record.startAt ?: return@filter false
                anchor in windowStart..windowEnd && record.externalId !in seen
            }
            stale.forEach { repository.deleteRecord(it.connectorId, it.accountId, it.externalId) }
            repository.updateLastSync(CONNECTOR_ID, ACCOUNT_ID, now)
            SyncResult(
                connectorId = CONNECTOR_ID,
                accountId = ACCOUNT_ID,
                success = true,
                recordsAdded = added,
                recordsUpdated = updated,
                recordsRemoved = stale.size
            )
        }
    }

    override suspend fun query(
        connection: ConnectionRecord,
        query: ContextQuery
    ): Result<List<ExternalRecord>> = runCatching {
        val now = System.currentTimeMillis()
        repository.getRecordsForConnection(CONNECTOR_ID, ACCOUNT_ID)
            .filter { record ->
                val anchor = record.startAt ?: record.createdAt
                anchor in (now - query.timeWindowMs)..(now + query.timeWindowMs) &&
                    (query.recordTypes?.let { record.recordType in it } ?: true) &&
                    (query.sources?.let { record.source in it } ?: true) &&
                    (query.text?.let { text ->
                        record.title.orEmpty().contains(text, true) ||
                            record.description.orEmpty().contains(text, true)
                    } ?: true)
            }
            .sortedBy { it.startAt }
            .take(query.limit)
    }

    override suspend fun perform(action: ExternalAction): Result<ExternalActionResult> = runCatching {
        when (action) {
            is ExternalAction.OpenOriginal -> ExternalActionResult(dataSource.open(action.deepLinkUri))
            is ExternalAction.MarkResolved -> {
                repository.markResolved(action.connectorId, action.accountId, action.externalId)
                ExternalActionResult(true)
            }
            is ExternalAction.ImportAsVaultItem -> ExternalActionResult(false, "Calendar import is not supported")
        }
    }

    internal fun normalize(row: CalendarEventRow): ExternalRecord {
        val id = externalId(row)
        val eventUri = "content://com.android.calendar/events/${row.eventId}"
        return ExternalRecord(
            connectorId = CONNECTOR_ID,
            accountId = ACCOUNT_ID,
            externalId = id,
            source = ExternalSource.CALENDAR,
            recordType = ExternalRecordType.EVENT,
            title = row.title?.takeIf(String::isNotBlank) ?: "Calendar event",
            description = row.description,
            startAt = row.startAt,
            endAt = row.endAt,
            payload = buildMap {
                put("calendar_id", row.calendarId.toString())
                put("event_id", row.eventId.toString())
                put("all_day", row.allDay.toString())
                row.location?.takeIf(String::isNotBlank)?.let { put("location", it) }
                row.recurrenceRule?.takeIf(String::isNotBlank)?.let { put("recurrence", it) }
                row.timezone?.takeIf(String::isNotBlank)?.let { put("timezone", it) }
            },
            deepLinkUri = eventUri,
            hash = listOf(row.title, row.startAt, row.endAt, row.location).joinToString("|").hashCode().toString(),
            updatedAt = System.currentTimeMillis(),
            expiresAt = row.endAt + RETENTION_AFTER_EVENT_MS
        )
    }

    private fun connection(state: ConnectionState, error: String? = null) = ConnectionRecord(
        connectorId = CONNECTOR_ID,
        accountId = ACCOUNT_ID,
        state = state,
        accountName = "Device calendars",
        capabilities = capabilities,
        lastError = error
    )

    private fun emptySync() = SyncResult(CONNECTOR_ID, ACCOUNT_ID, success = true)

    private fun externalId(row: CalendarEventRow) = "${row.eventId}:${row.startAt}"

    companion object {
        const val CONNECTOR_ID = "calendar"
        const val ACCOUNT_ID = "device"
        const val PAST_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
        const val FUTURE_WINDOW_MS = 90L * 24 * 60 * 60 * 1000
        private const val RETENTION_AFTER_EVENT_MS = 30L * 24 * 60 * 60 * 1000
    }
}

@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted context: android.content.Context,
    @Assisted params: WorkerParameters,
    private val connector: CalendarConnector,
    private val repository: ExternalContextRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (DecoySessionState.isDecoy.value) return Result.success()
        val connection = repository.getConnection(CalendarConnector.CONNECTOR_ID, CalendarConnector.ACCOUNT_ID)
            ?: return Result.success()
        if (connection.state != ConnectionState.CONNECTED) return Result.success()
        return connector.sync(connection, SyncRequest()).fold(
            onSuccess = { Result.success() },
            onFailure = { if (it is SecurityException) Result.success() else Result.retry() }
        )
    }

    companion object {
        private const val WORK_NAME = "calendar_bounded_sync"

        fun enqueuePeriodic(context: android.content.Context) {
            val work = PeriodicWorkRequestBuilder<CalendarSyncWorker>(6, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }
    }
}

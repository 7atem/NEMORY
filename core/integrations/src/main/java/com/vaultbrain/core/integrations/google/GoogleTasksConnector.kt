package com.vaultbrain.core.integrations.google

import com.vaultbrain.core.common.model.external.*
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.integrations.connector.ExternalConnector
import com.vaultbrain.core.integrations.model.*
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleTasksConnector @Inject constructor(
    private val api: GoogleDeviceApi,
    private val authorization: GoogleDeviceAuthorization,
    private val repository: ExternalContextRepository
) : ExternalConnector {
    override val connectorId = "google_tasks"
    override val displayName = "Google Tasks"
    override val capabilities = setOf(ConnectorCapability.READ, ConnectorCapability.SEARCH,
        ConnectorCapability.OPEN_ORIGINAL, ConnectorCapability.BACKGROUND_SYNC)
    private val mutex = GoogleSyncGate.mutex

    override suspend fun availability(): ConnectorAvailability {
        val accounts = repository.observeConnections().first().filter { it.connectorId == connectorId && it.state == ConnectionState.CONNECTED }
        return ConnectorAvailability(connectorId, displayName, true,
            if (accounts.isEmpty()) ConnectionState.DISCONNECTED else ConnectionState.CONNECTED,
            capabilities, accounts.map { it.accountId })
    }
    override suspend fun connect(accountId: String?): Result<ConnectionRecord> = safe {
        val account = requireNotNull(accountId)
        authorization.token(GoogleService.TASKS, account)
        requireNotNull(repository.getConnection(connectorId, account))
    }
    override suspend fun disconnect(connection: ConnectionRecord): Result<Unit> = safe {
        authorization.disconnect(connection.accountId)
    }

    override suspend fun sync(connection: ConnectionRecord, request: SyncRequest): Result<SyncResult> = safe {
        mutex.withLock {
            if (DecoySessionState.isDecoy.value) return@withLock SyncResult(connectorId, connection.accountId, true)
            val current = repository.getConnection(connectorId, connection.accountId) ?: throw GoogleConsentRequired()
            if (current.state != ConnectionState.CONNECTED) throw GoogleConsentRequired()
            val now = System.currentTimeMillis()
            val state = current.metadataJson?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            val listToken = state?.text("listPage")
            val activeList = state?.text("listId")
            val taskToken = state?.text("taskPage")
            val since = state?.text("since") ?: current.lastSyncAt?.let { Instant.ofEpochMilli(it - 60_000).toString() }
            val syncStarted = state?.text("started")?.toLongOrNull() ?: now
            val lists = api.get(GoogleService.TASKS, current.accountId, "/users/@me/lists",
                buildMap { put("maxResults", "10"); listToken?.let { put("pageToken", it) } })
            val rows = lists.array("items")
            var resumeAt = if (activeList == null) 0 else rows.indexOfFirst { it.jsonObject.text("id") == activeList }.coerceAtLeast(0)
            var recordsUpdated = 0
            var removed = 0
            var nextListId: String? = null
            var nextTaskPage: String? = null
            var pages = 0
            while (resumeAt < rows.size && pages < 3) {
                val list = rows[resumeAt].jsonObject
                val listId = list.text("id") ?: error("Missing task list id")
                val tasks = api.get(GoogleService.TASKS, current.accountId, "/lists/${GoogleDeviceApi.encode(listId)}/tasks", buildMap {
                    put("maxResults", "100"); put("showCompleted", "true"); put("showDeleted", "true"); put("showHidden", "true")
                    since?.let { put("updatedMin", it) }
                    if (listId == activeList) taskToken?.let { put("pageToken", it) }
                })
                pages++
                tasks.array("items").forEach { element ->
                    if (DecoySessionState.isDecoy.value || repository.getConnection(connectorId, current.accountId)?.state != ConnectionState.CONNECTED) throw GoogleConsentRequired()
                    val task = element.jsonObject
                    val id = task.text("id") ?: return@forEach
                    val externalId = "$listId:$id"
                    if (task["deleted"]?.jsonPrimitive?.booleanOrNull == true) {
                        repository.deleteRecord(connectorId, current.accountId, externalId); removed++
                    } else {
                        repository.upsertRecord(normalize(current.accountId, listId, list.text("title").orEmpty(), task, now))
                        recordsUpdated++
                    }
                }
                nextTaskPage = tasks.text("nextPageToken")
                if (nextTaskPage != null) { nextListId = listId; break }
                resumeAt++
            }
            if (nextListId == null && resumeAt < rows.size) nextListId = rows[resumeAt].jsonObject.text("id")
            val nextListPage = if (nextListId != null) listToken else lists.text("nextPageToken")
            val complete = nextListId == null && nextListPage == null
            val cursor = if (complete) null else buildJsonObject {
                nextListPage?.let { put("listPage", it) }
                nextListId?.let { put("listId", it) }
                nextTaskPage?.let { put("taskPage", it) }
                since?.let { put("since", it) }
                put("started", syncStarted)
            }.toString()
            if (!DecoySessionState.isDecoy.value && repository.getConnection(connectorId, current.accountId)?.state == ConnectionState.CONNECTED) {
                repository.upsertConnection(current.copy(metadataJson = cursor,
                    lastSyncAt = if (complete) syncStarted else current.lastSyncAt, lastError = null, updatedAt = now))
                repository.deleteExpired()
            }
            SyncResult(connectorId, current.accountId, true, recordsUpdated = recordsUpdated, recordsRemoved = removed)
        }
    }

    override suspend fun query(connection: ConnectionRecord, query: ContextQuery): Result<List<ExternalRecord>> = safe {
        repository.getRecordsForConnection(connectorId, connection.accountId).filter {
            !it.isResolved && (query.text.isNullOrBlank() || (it.title.orEmpty() + " " + it.description.orEmpty()).contains(query.text, true))
        }.take(query.limit.coerceIn(0, 30))
    }
    override suspend fun perform(action: ExternalAction): Result<ExternalActionResult> = Result.success(ExternalActionResult(false))

    companion object {
        internal fun normalize(account: String, listId: String, listTitle: String, task: JsonObject, now: Long): ExternalRecord {
            val updated = task.text("updated")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: now
            // Google Tasks due dates are dates, not appointment times.
            val due = task.text("due")?.take(10)
            return ExternalRecord("google_tasks", account, "$listId:${task.text("id")}", ExternalSource.GOOGLE_TASKS,
                ExternalRecordType.TASK, title = task.text("title").orEmpty().take(240),
                description = task.text("notes").orEmpty().take(4000),
                dueAt = due?.let { runCatching { java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull() },
                sensitivity = SensitivityLevel.PERSONAL,
                payload = buildMap { put("list", listTitle.take(240)); due?.let { put("due_date", it) }; put("status", task.text("status").orEmpty()) },
                deepLinkUri = "https://tasks.google.com/tasks/?authuser=${GoogleDeviceApi.encode(account)}",
                createdAt = updated, updatedAt = updated, expiresAt = now + 120L * 86_400_000,
                isResolved = task.text("status") == "completed")
        }
        private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull
        private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
        private suspend fun <T> safe(block: suspend () -> T): Result<T> = try { Result.success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Result.failure(failure) }
    }
}

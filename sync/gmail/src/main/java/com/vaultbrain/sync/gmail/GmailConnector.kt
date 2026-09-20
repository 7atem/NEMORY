package com.vaultbrain.sync.gmail

import kotlinx.coroutines.sync.withLock

import com.vaultbrain.shared.model.external.ConnectionState
import com.vaultbrain.shared.model.external.ConnectorCapability
import com.vaultbrain.shared.model.external.ExternalRecordType
import com.vaultbrain.shared.model.external.ExternalSource
import com.vaultbrain.shared.model.external.SensitivityLevel
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.integrations.connector.ExternalConnector
import com.vaultbrain.core.integrations.model.*
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import com.vaultbrain.core.notifications.UnifiedAlertManager
import javax.inject.Inject
import javax.inject.Singleton

/** Read-only, bounded local Gmail context connector. Email content is never promoted automatically. */
@Singleton
class GmailConnector @Inject constructor(
    private val dataSource: GmailDataSource,
    private val parser: GmailParser,
    private val repository: ExternalContextRepository,
    private val alertManager: UnifiedAlertManager
) : ExternalConnector {
    override val connectorId = CONNECTOR_ID
    override val displayName = "Gmail"
    override val capabilities = setOf(
        ConnectorCapability.READ,
        ConnectorCapability.SEARCH,
        ConnectorCapability.OPEN_ORIGINAL,
        ConnectorCapability.BACKGROUND_SYNC
    )

    override suspend fun availability(): ConnectorAvailability {
        val configured = dataSource.isConfigured()
        val accounts = if (configured) dataSource.authorizedAccounts() else emptyList()
        return ConnectorAvailability(
            connectorId = connectorId,
            displayName = displayName,
            isAvailable = configured,
            state = when {
                !configured -> ConnectionState.UNAVAILABLE
                accounts.isEmpty() -> ConnectionState.DISCONNECTED
                else -> ConnectionState.CONNECTED
            },
            capabilities = capabilities,
            accountIds = accounts
        )
    }

    override suspend fun connect(accountId: String?): Result<ConnectionRecord> = runCatching {
        val authorizedAccount = dataSource.authorize(accountId).getOrThrow()
        connection(authorizedAccount, ConnectionState.CONNECTED).also { repository.upsertConnection(it) }
    }

    override suspend fun disconnect(connection: ConnectionRecord): Result<Unit> = runCatching {
        dataSource.revoke(connection.accountId).getOrThrow()
        repository.deleteRecordsForConnection(CONNECTOR_ID, connection.accountId)
        repository.deleteConnection(CONNECTOR_ID, connection.accountId)
    }

    override suspend fun sync(connection: ConnectionRecord, request: SyncRequest): Result<SyncResult> {
        return com.vaultbrain.core.integrations.google.GoogleSyncGate.mutex.withLock {
            syncLocked(connection, request)
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    private suspend fun syncLocked(connection: ConnectionRecord, request: SyncRequest): Result<SyncResult> {
        if (DecoySessionState.isDecoy.value) return Result.success(emptySync(connection.accountId))
        return runCatching {
            val now = System.currentTimeMillis()
            val existing = repository.getRecordsForConnection(CONNECTOR_ID, connection.accountId)
                .associateBy(ExternalRecord::externalId)
            val page = dataSource.page(connection.accountId, now - LOOKBACK_MS, MAX_MESSAGES_PER_SYNC, connection.metadataJson)
            val rows = page.messages.distinctBy(GmailMessageRow::id)
            var added = 0
            var updated = 0
            rows.forEach { row ->
                if (DecoySessionState.isDecoy.value || repository.getConnection(CONNECTOR_ID, connection.accountId)?.state != ConnectionState.CONNECTED) return@runCatching emptySync(connection.accountId)
                val normalized = normalize(connection.accountId, row)
                val prior = existing[normalized.externalId]
                // Keep user-managed state (resolved/seen) across re-syncs.
                val record = prior?.let {
                    normalized.copy(isResolved = it.isResolved, seenAt = it.seenAt)
                } ?: normalized
                if (prior != null) updated++ else added++
                repository.upsertRecord(record)

            }
            if (DecoySessionState.isDecoy.value || repository.getConnection(CONNECTOR_ID, connection.accountId)?.state != ConnectionState.CONNECTED) {
                return@runCatching emptySync(connection.accountId)
            }
            page.deletedIds.forEach { repository.deleteRecord(CONNECTOR_ID, connection.accountId, it) }
            repository.deleteExpired()
            repository.upsertConnection(connection.copy(metadataJson = page.cursor, lastSyncAt = now, lastError = null,
                updatedAt = now))
            repository.updateLastSync(CONNECTOR_ID, connection.accountId, now)
            SyncResult(CONNECTOR_ID, connection.accountId, true, added, updated, page.deletedIds.size)
        }
    }

    override suspend fun query(connection: ConnectionRecord, query: ContextQuery): Result<List<ExternalRecord>> = runCatching {
        repository.getRecordsForConnection(CONNECTOR_ID, connection.accountId)
            .filter { record ->
                (query.recordTypes?.let { record.recordType in it } ?: true) &&
                    (query.sources?.let { record.source in it } ?: true) &&
                    (query.text?.let { text ->
                        record.title.orEmpty().contains(text, true) ||
                            record.description.orEmpty().contains(text, true) ||
                            record.payload.values.any { it.contains(text, true) }
                    } ?: true)
            }.sortedByDescending(ExternalRecord::createdAt).take(query.limit)
    }

    override suspend fun perform(action: ExternalAction): Result<ExternalActionResult> = runCatching {
        when (action) {
            is ExternalAction.OpenOriginal -> ExternalActionResult(dataSource.open(action.deepLinkUri))
            is ExternalAction.MarkResolved -> {
                repository.markResolved(action.connectorId, action.accountId, action.externalId)
                ExternalActionResult(true)
            }
            is ExternalAction.ImportAsVaultItem -> ExternalActionResult(false, "Confirm import from the Gmail record screen")
        }
    }

    internal fun normalize(accountId: String, row: GmailMessageRow): ExternalRecord {
        val safeBody = GmailTextSanitizer.sanitize(row.plainText)
        val parsed = parser.parseEmailContent(row.subject, safeBody)
        return ExternalRecord(
            connectorId = CONNECTOR_ID,
            accountId = accountId,
            externalId = row.id,
            source = ExternalSource.GMAIL,
            recordType = ExternalRecordType.EMAIL,
            title = row.subject.take(MAX_TITLE_CHARS),
            description = parsed.bodySnippet,
            sensitivity = SensitivityLevel.PERSONAL,
            payload = buildMap {
                row.sender?.takeIf(String::isNotBlank)?.let { put("sender", it.take(200)) }
                parsed.merchant?.let { put("merchant", it) }
                parsed.orderNumber?.let { put("order_number", it) }
                parsed.trackingNumber?.let { put("tracking_number", it) }
                parsed.totalAmount?.let { put("total", it.toString()) }
                parsed.currency?.let { put("currency", it) }
            },
            deepLinkUri = "https://mail.google.com/mail/?authuser=${com.vaultbrain.core.integrations.google.GoogleDeviceApi.encode(accountId)}#all/${row.threadId}",
            hash = listOf(row.subject, safeBody, row.receivedAt).joinToString("|").hashCode().toString(),
            createdAt = row.receivedAt,
            updatedAt = row.receivedAt,
            expiresAt = row.receivedAt + RETENTION_MS
        )
    }

    private fun connection(accountId: String, state: ConnectionState) = ConnectionRecord(
        connectorId = CONNECTOR_ID,
        accountId = accountId,
        state = state,
        accountName = accountId,
        capabilities = capabilities
    )

    private fun emptySync(accountId: String) = SyncResult(CONNECTOR_ID, accountId, true)

    companion object {
        const val CONNECTOR_ID = "gmail"
        const val LOOKBACK_MS = 90L * 24 * 60 * 60 * 1000
        const val RETENTION_MS = 120L * 24 * 60 * 60 * 1000
        const val MAX_MESSAGES_PER_SYNC = 200
        const val MAX_TITLE_CHARS = 240
    }
}

internal object GmailTextSanitizer {
    private val BLOCKED = Regex("(?is)<(script|style|iframe|object)[^>]*>.*?</\\1>")
    private val TAG = Regex("(?s)<[^>]+>")
    fun sanitize(value: String): String = value
        .replace(BLOCKED, " ")
        .replace(TAG, " ")
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
        .take(16_000)
}

package com.vaultbrain.core.integrations.connector

import com.vaultbrain.shared.model.external.ConnectorCapability
import com.vaultbrain.core.integrations.model.ConnectionRecord
import com.vaultbrain.core.integrations.model.ConnectorAvailability
import com.vaultbrain.core.integrations.model.ContextQuery
import com.vaultbrain.core.integrations.model.ExternalAction
import com.vaultbrain.core.integrations.model.ExternalActionResult
import com.vaultbrain.core.integrations.model.ExternalRecord
import com.vaultbrain.core.integrations.model.SyncRequest
import com.vaultbrain.core.integrations.model.SyncResult

/**
 * Contract for a source of external personal context.
 *
 * Each connector owns one account scope (accountId may be empty for device-local
 * sources). Callers must check [capabilities] before invoking optional operations.
 */
interface ExternalConnector {

    /** Stable identifier, e.g. "calendar", "gmail", "android_share". */
    val connectorId: String

    /** User-facing label for the Connections UI. */
    val displayName: String

    /** Operations this connector can perform when connected. */
    val capabilities: Set<ConnectorCapability>

    /** Whether the underlying API/app is installed and reachable on this device. */
    suspend fun availability(): ConnectorAvailability

    /** Establish or refresh authorization for [accountId]; null means the default account. */
    suspend fun connect(accountId: String? = null): Result<ConnectionRecord>

    /** Revoke access for the given connection. */
    suspend fun disconnect(connection: ConnectionRecord): Result<Unit>

    /** Pull/update records for [connection] according to [request]. */
    suspend fun sync(connection: ConnectionRecord, request: SyncRequest): Result<SyncResult>

    /** Query records already known to this connector. */
    suspend fun query(connection: ConnectionRecord, query: ContextQuery): Result<List<ExternalRecord>>

    /** Execute a user action such as opening the original record. */
    suspend fun perform(action: ExternalAction): Result<ExternalActionResult>
}

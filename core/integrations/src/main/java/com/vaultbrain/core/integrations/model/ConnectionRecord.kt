package com.vaultbrain.core.integrations.model

import com.vaultbrain.core.common.model.external.ConnectionState
import com.vaultbrain.core.common.model.external.ConnectorCapability

/**
 * User-readable runtime state for a connector/account pair.
 *
 * Mirrors [com.vaultbrain.core.database.entity.ExternalConnectionEntity].
 */
data class ConnectionRecord(
    val connectorId: String,
    val accountId: String,
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val accountName: String? = null,
    val accountIconUri: String? = null,
    val capabilities: Set<ConnectorCapability> = emptySet(),
    val metadataJson: String? = null,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

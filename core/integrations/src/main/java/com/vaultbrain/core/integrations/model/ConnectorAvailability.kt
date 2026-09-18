package com.vaultbrain.core.integrations.model

import com.vaultbrain.core.common.model.external.ConnectionState
import com.vaultbrain.core.common.model.external.ConnectorCapability

/**
 * Static + runtime metadata for a connector, used by the Connections UI.
 */
data class ConnectorAvailability(
    val connectorId: String,
    val displayName: String,
    val isAvailable: Boolean,
    val state: ConnectionState = ConnectionState.UNAVAILABLE,
    val capabilities: Set<ConnectorCapability> = emptySet(),
    val accountIds: List<String> = emptyList()
)

package com.vaultbrain.core.integrations.model

import com.vaultbrain.shared.model.external.ConnectionState
import com.vaultbrain.shared.model.external.ConnectorCapability

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

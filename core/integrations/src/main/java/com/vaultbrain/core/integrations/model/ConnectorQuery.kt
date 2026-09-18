package com.vaultbrain.core.integrations.model

/**
 * A [ContextQuery] scoped to a specific connector/account pair.
 */
data class ConnectorQuery(
    val query: ContextQuery,
    val connectorId: String,
    val accountId: String
)

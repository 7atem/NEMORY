package com.vaultbrain.core.integrations.model

/**
 * Request to refresh external records from one or all connectors.
 */
data class SyncRequest(
    /** Null pulls every registered connector. */
    val connectorId: String? = null,

    /** Null pulls every account for the chosen connector. */
    val accountId: String? = null,

    /** Bypass local staleness checks. */
    val force: Boolean = false
)

package com.vaultbrain.core.integrations.model

/**
 * Outcome of a single connector/account sync.
 */
data class SyncResult(
    val connectorId: String,
    val accountId: String? = null,
    val success: Boolean,
    val recordsAdded: Int = 0,
    val recordsUpdated: Int = 0,
    val recordsRemoved: Int = 0,
    val error: String? = null
)

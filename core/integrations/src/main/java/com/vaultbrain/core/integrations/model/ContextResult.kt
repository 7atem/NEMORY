package com.vaultbrain.core.integrations.model

/**
 * Normalized result of assembling external context for a vault item or query.
 */
data class ContextResult(
    val records: List<ExternalRecord> = emptyList(),
    val matchedConnectors: List<String> = emptyList(),
    val isPartial: Boolean = false
)

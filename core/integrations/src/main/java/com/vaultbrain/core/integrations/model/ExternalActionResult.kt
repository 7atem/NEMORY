package com.vaultbrain.core.integrations.model

/**
 * Outcome of an [ExternalAction].
 */
data class ExternalActionResult(
    val success: Boolean,
    val error: String? = null
)

package com.vaultbrain.core.common.model

import kotlinx.serialization.Serializable

/** Persistent state for deterministic extraction and indexing stages. */
@Serializable
enum class ProcessingState {
    PENDING,
    RUNNING,
    COMPLETE,
    FAILED_RETRYABLE,
    SKIPPED_UNSUPPORTED
}

/** Calm user-facing status derived from the internal pipeline states. */
enum class ItemProcessingStatus(val label: String) {
    SAVED("Saved"),
    ORGANIZING("Organizing…"),
    READY("Ready"),
    NEEDS_REVIEW("Needs review")
}

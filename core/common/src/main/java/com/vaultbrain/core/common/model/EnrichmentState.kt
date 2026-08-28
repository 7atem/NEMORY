package com.vaultbrain.core.common.model

import kotlinx.serialization.Serializable

/** Persistent lifecycle for optional foreground AI enrichment. */
@Serializable
enum class EnrichmentState {
    PENDING,
    RUNNING,
    COMPLETE,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    SKIPPED_UNSUPPORTED,
    SKIPPED_PRIVACY,
    USER_ACCEPTED;

    fun canTransitionTo(next: EnrichmentState): Boolean = when (this) {
        PENDING -> next in setOf(RUNNING, FAILED_RETRYABLE, SKIPPED_UNSUPPORTED, SKIPPED_PRIVACY)
        RUNNING -> next in setOf(
            PENDING,
            COMPLETE,
            FAILED_RETRYABLE,
            FAILED_FINAL,
            SKIPPED_UNSUPPORTED,
            SKIPPED_PRIVACY
        )
        FAILED_RETRYABLE -> next == RUNNING
        COMPLETE, FAILED_FINAL, SKIPPED_UNSUPPORTED, SKIPPED_PRIVACY, USER_ACCEPTED -> false
    }
}

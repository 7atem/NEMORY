package com.vaultbrain.shared.workers

/**
 * Types of background jobs that Nemory needs to run.
 */
enum class VaultJobType {
    SYNC_EXTERNAL_RECORDS,
    EMBEDDING_BACKFILL,
    DEFERRED_ANALYSIS
}

/**
 * Cross-platform abstraction for scheduling background work.
 * Replaces direct Android WorkManager calls.
 */
interface VaultWorkScheduler {
    /**
     * Schedules a one-off background job.
     */
    fun scheduleOneOffJob(jobType: VaultJobType)

    /**
     * Schedules a repeating background job (e.g. daily sync).
     */
    fun schedulePeriodicJob(jobType: VaultJobType, repeatIntervalHours: Long)
    
    /**
     * Cancels all scheduled jobs of the given type.
     */
    fun cancelJob(jobType: VaultJobType)
}

/** CompositionLocal for providing the platform-specific VaultWorkScheduler. */
val LocalVaultWorkScheduler = androidx.compose.runtime.staticCompositionLocalOf<VaultWorkScheduler?> { null }

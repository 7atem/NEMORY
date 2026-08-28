package com.vaultbrain.feature.capture

import android.content.Context
import com.vaultbrain.core.database.repository.VaultRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Coordinates the one-time upgrade of historical heuristic-only items to local-AI results. */
@Singleton
class LocalAiAdoptionManager @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: VaultRepository
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    /** Requeues every eligible historical item exactly once for the current adoption version. */
    suspend fun prepareExistingItemsIfNeeded(): Int = mutex.withLock {
        if (preferences.getInt(KEY_QUEUED_VERSION, 0) >= CURRENT_ADOPTION_VERSION) return 0
        val queued = repository.queueAllForLocalAiEnrichment()
        preferences.edit().putInt(KEY_QUEUED_VERSION, CURRENT_ADOPTION_VERSION).apply()
        queued
    }

    /** Explicitly refreshes every eligible item, including previously enriched results. */
    suspend fun reprocessAllItems(): Int = mutex.withLock {
        repository.queueAllForLocalAiEnrichment()
    }

    private companion object {
        const val PREFERENCES_NAME = "local_ai_adoption"
        const val KEY_QUEUED_VERSION = "queued_version"
        const val CURRENT_ADOPTION_VERSION = 1
    }
}

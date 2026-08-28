package com.vaultbrain.core.common

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks how often the user saves items to each experience type.
 *
 * Used to boost keyword suggestion scores for the user's most-used experiences,
 * making the capture flow faster and more intuitive over time.
 *
 * Data is stored in local SharedPreferences only (no cloud sync).
 */
@Singleton
class UserExperienceFrequency @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Increment the save count for an experience. Call after successful save. */
    fun recordSave(experienceId: String) {
        if (experienceId.isBlank()) return
        val current = prefs.getInt(experienceId, 0)
        prefs.edit().putInt(experienceId, current + 1).apply()
    }

    /** Returns the save count for a specific experience. */
    fun countFor(experienceId: String): Int = prefs.getInt(experienceId, 0)

    /**
     * Returns all recorded frequencies as a map of experienceId to count.
     * Only includes experiences with count > 0.
     */
    fun allFrequencies(): Map<String, Int> {
        @Suppress("UNCHECKED_CAST")
        return (prefs.all as? Map<String, Int>)
            ?.filter { it.value > 0 }
            ?: emptyMap()
    }

    /**
     * Returns the top N most-used experience IDs, sorted by frequency descending.
     */
    fun topExperiences(n: Int = 6): List<String> =
        allFrequencies()
            .entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key }

    /** Total number of items saved across all experiences. */
    fun totalSaves(): Int = allFrequencies().values.sum()

    /** True when the user has never saved an item (first-use). */
    fun isFirstUse(): Boolean = prefs.all.isEmpty()

    private companion object {
        const val PREFS_NAME = "vaultbrain_experience_frequency"
    }
}

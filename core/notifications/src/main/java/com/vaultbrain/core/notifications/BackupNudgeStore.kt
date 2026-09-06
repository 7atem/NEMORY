package com.vaultbrain.core.notifications

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences bookkeeping for the dismissible Today-tab backup nudge.
 * Dismissal is permanent; the nudge links to Settings and has no reshow budget.
 */
@Singleton
class BackupNudgeStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun shouldShow(): Boolean = !preferences.getBoolean(KEY_DISMISSED, false)

    fun recordDismiss() {
        preferences.edit().putBoolean(KEY_DISMISSED, true).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "nemory_backup_nudge"
        const val KEY_DISMISSED = "dismissed"
    }
}

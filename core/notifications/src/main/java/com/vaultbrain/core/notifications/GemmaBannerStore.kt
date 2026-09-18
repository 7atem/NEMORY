package com.vaultbrain.core.notifications

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences bookkeeping for the dismissible Today-tab Gemma download banner.
 *
 * The banner is hidden for [RESHOW_DELAY_MS] after each dismiss and disappears permanently
 * after [MAX_DISMISSES] dismisses. Separate from [GemmaDownloadPromptStore] so dismissing the
 * banner never counts against the notification prompt budget (and vice versa).
 */
@Singleton
class GemmaBannerStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun shouldShow(now: Long = System.currentTimeMillis()): Boolean = shouldShowBanner(
        dismissCount = preferences.getInt(KEY_DISMISS_COUNT, 0),
        lastDismissedAt = preferences.getLong(KEY_LAST_DISMISSED_AT, 0L),
        now = now
    )

    fun recordDismiss(now: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putInt(KEY_DISMISS_COUNT, preferences.getInt(KEY_DISMISS_COUNT, 0) + 1)
            .putLong(KEY_LAST_DISMISSED_AT, now)
            .apply()
    }

    companion object {
        const val PREFERENCES_NAME = "nemory_gemma_banner"
        const val KEY_DISMISS_COUNT = "dismiss_count"
        const val KEY_LAST_DISMISSED_AT = "last_dismissed_at"
        const val RESHOW_DELAY_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_DISMISSES = 3

        internal fun shouldShowBanner(dismissCount: Int, lastDismissedAt: Long, now: Long): Boolean = when {
            dismissCount >= MAX_DISMISSES -> false
            dismissCount == 0 -> true
            else -> now - lastDismissedAt >= RESHOW_DELAY_MS
        }
    }
}

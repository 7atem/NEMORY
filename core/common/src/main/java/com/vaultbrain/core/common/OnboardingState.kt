package com.vaultbrain.core.common

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object OnboardingState {
    private const val PREFS_NAME = "onboarding_prefs"
    private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    private const val KEY_DEMO_MODE_ENABLED = "demo_mode_enabled"
    private const val KEY_DEMO_MODE_DISMISSED = "demo_mode_dismissed"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasSeenOnboarding(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
    }

    fun setHasSeenOnboarding(context: Context, value: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_HAS_SEEN_ONBOARDING, value) }
    }

    /** True when the user chose the demo path during onboarding and has not yet saved a real item. */
    fun isDemoModeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DEMO_MODE_ENABLED, false) &&
            !getPrefs(context).getBoolean(KEY_DEMO_MODE_DISMISSED, false)
    }

    fun setDemoModeEnabled(context: Context, value: Boolean) {
        getPrefs(context).edit {
            putBoolean(KEY_DEMO_MODE_ENABLED, value)
            if (!value) putBoolean(KEY_DEMO_MODE_DISMISSED, true)
        }
    }

    /** Call after the first real item is saved to exit demo mode. */
    fun recordFirstRealItemSaved(context: Context) {
        getPrefs(context).edit {
            putBoolean(KEY_DEMO_MODE_ENABLED, false)
            putBoolean(KEY_DEMO_MODE_DISMISSED, true)
        }
    }
}

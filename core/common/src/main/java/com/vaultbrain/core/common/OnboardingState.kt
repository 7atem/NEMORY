package com.vaultbrain.core.common

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object OnboardingState {
    private const val PREFS_NAME = "onboarding_prefs"
    private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasSeenOnboarding(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
    }

    fun setHasSeenOnboarding(context: Context, value: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_HAS_SEEN_ONBOARDING, value) }
    }
}

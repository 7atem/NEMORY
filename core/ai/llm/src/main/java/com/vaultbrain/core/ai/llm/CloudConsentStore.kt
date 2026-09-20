package com.vaultbrain.core.ai.llm

import android.content.Context
import android.content.SharedPreferences
import com.vaultbrain.shared.model.Classification
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device-only remembered cloud approvals. Vault content is never stored here. */
interface CloudConsentStore {
    val optedInCategories: StateFlow<Set<Classification>>

    fun isOptedIn(classification: Classification?): Boolean
    fun remember(categories: Set<Classification>)
    fun revoke(category: Classification)
    fun clear()
}

@Singleton
class SharedPreferencesCloudConsentStore @Inject constructor(
    @ApplicationContext context: Context,
    private val privacyEngine: PrivacyEngine
) : CloudConsentStore {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val _optedInCategories = MutableStateFlow(readCategories())

    override val optedInCategories: StateFlow<Set<Classification>> =
        _optedInCategories.asStateFlow()

    override fun isOptedIn(classification: Classification?): Boolean =
        classification != null && classification in _optedInCategories.value

    override fun remember(categories: Set<Classification>) {
        val updated = _optedInCategories.value + categories.filter(
            privacyEngine::isRememberableCategory
        )
        persist(updated)
    }

    override fun revoke(category: Classification) {
        persist(_optedInCategories.value - category)
    }

    override fun clear() {
        persist(emptySet())
    }

    private fun readCategories(): Set<Classification> = preferences
        .getStringSet(KEY_CATEGORIES, emptySet())
        .orEmpty()
        .mapNotNull { stored -> Classification.entries.firstOrNull { it.name == stored } }
        .filter(privacyEngine::isRememberableCategory)
        .toSet()

    private fun persist(categories: Set<Classification>) {
        val safe = categories.filter(privacyEngine::isRememberableCategory).toSet()
        preferences.edit().putStringSet(KEY_CATEGORIES, safe.map { it.name }.toSet()).apply()
        _optedInCategories.value = safe
    }

    private companion object {
        const val PREFERENCES_NAME = "nemory_cloud_consent"
        const val KEY_CATEGORIES = "approved_categories"
    }
}

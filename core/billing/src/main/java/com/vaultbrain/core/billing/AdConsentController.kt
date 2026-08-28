package com.vaultbrain.core.billing

import android.app.Activity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stubbed consent controller for the ad-free release.
 *
 * The User Messaging Platform (UMP) and AdMob SDKs are not included, so this
 * controller always reports that ads cannot be requested and never shows a form.
 * The public API is preserved to avoid breaking existing callers.
 */
@Singleton
class AdConsentController @Inject constructor(
    @ApplicationContext private val context: android.content.Context
) {

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _isFormAvailable = MutableStateFlow(false)
    val isFormAvailable: StateFlow<Boolean> = _isFormAvailable.asStateFlow()

    /**
     * No-op in the ad-free release. Always returns [ConsentStatus.NOT_REQUIRED].
     */
    suspend fun refreshConsentInfo(activity: Activity): ConsentStatus {
        return ConsentStatus.NOT_REQUIRED
    }

    /**
     * No-op in the ad-free release. Always returns false.
     */
    suspend fun showForm(activity: Activity): Boolean {
        return false
    }

    /**
     * No-op in the ad-free release. Always returns false.
     */
    suspend fun showPrivacyOptions(activity: Activity): Boolean {
        return false
    }

    enum class ConsentStatus {
        NOT_REQUIRED,
        REQUIRED,
        OBTAINED,
        UNKNOWN,
        ERROR
    }
}

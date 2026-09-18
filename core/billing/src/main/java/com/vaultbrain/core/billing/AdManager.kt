package com.vaultbrain.core.billing

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stubbed ad boundary for the ad-free release.
 *
 * The first public release of Nemory does not include advertising or the AdMob SDK.
 * This class is retained so callers and dependency injection continue to compile,
 * but every method is a no-op and [canShowAds] always returns false.
 */
@Singleton
class AdManager @Inject constructor() {

    /**
     * No-op: the AdMob SDK is not present in this release.
     */
    fun initializeIfAllowed() {
        // Ads are disabled in the ad-free release.
    }

    /**
     * Returns false in the ad-free release.
     */
    fun canShowAds(): Boolean = false

    /**
     * No-op: test devices are irrelevant when ads are disabled.
     */
    fun addTestDevice(deviceId: String) {
        // Ads are disabled in the ad-free release.
    }
}

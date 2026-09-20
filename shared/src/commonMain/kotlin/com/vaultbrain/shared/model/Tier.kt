package com.vaultbrain.shared.model

import kotlinx.serialization.Serializable

/**
 * Subscription/feature tier for entitlements.
 */
@Serializable
enum class Tier {
    FREE,
    PRO,
    ULTRA
}

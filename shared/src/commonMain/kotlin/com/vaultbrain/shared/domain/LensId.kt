package com.vaultbrain.shared.domain

/**
 * Canonical lens identifiers. All lenses are available regardless of monetization state.
 */
object LensId {
    const val MONEY = "MONEY" // Finance & Shopping
    const val HEALTH = "HEALTH" // Health & Wellness
    const val TRAVEL = "TRAVEL" // Travel & Vehicles
    const val BUREAUCRACY = "BUREAUCRACY" // Documents & Identity
    const val MEDIA = "MEDIA" // Media & Knowledge

    val FREE_LENSES = setOf(MONEY, HEALTH, TRAVEL, BUREAUCRACY, MEDIA)
    val PRO_LENSES = emptySet<String>()

    val ALL_LENSES: Set<String> = FREE_LENSES + PRO_LENSES

    /** Converts external/model output into a supported canonical lens id. */
    fun canonicalOrNull(value: String?): String? = value
        ?.trim()
        ?.uppercase()
        ?.takeIf { it in ALL_LENSES }
}

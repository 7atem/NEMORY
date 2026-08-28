package com.vaultbrain.core.common.model

/**
 * A machine-proposed association between a [VaultItem] and a [PersonalCollection].
 *
 * Never represents accepted organization — see [CollectionSuggestionStatus].
 * [collectionName] is resolved for display only; the pair (itemId, collectionId)
 * is the identity.
 */
data class PersonalCollectionSuggestion(
    val itemId: String,
    val collectionId: String,
    val collectionName: String = "",
    val itemTitle: String = "",
    val confidence: Float,
    val status: CollectionSuggestionStatus = CollectionSuggestionStatus.SUGGESTED,
    val createdAt: Long,
    val updatedAt: Long
)

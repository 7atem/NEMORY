package com.vaultbrain.shared.data

/** UI-facing summary of a personal collection. */
data class CollectionSummary(
    val id: String,
    val name: String,
    val itemCount: Int,
    val createdAtMillis: Long,
    val isPinned: Boolean,
)

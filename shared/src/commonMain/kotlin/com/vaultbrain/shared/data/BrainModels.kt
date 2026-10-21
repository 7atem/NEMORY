package com.vaultbrain.shared.data

/** UI-facing Brain chat turn. */
data class BrainMessage(
    val id: String,
    val role: String, // "user" | "assistant"
    val text: String,
    val isError: Boolean,
    val createdAtMillis: Long,
)

/** One-shot digest of the vault for the briefing screen. */
data class BriefingSnapshot(
    val totalActive: Int,
    val totalArchived: Int,
    val newSinceMillis: Long,
    val recentItems: List<VaultItemSummary>,
    val expiringSoon: List<VaultItemSummary>,
    val needsReviewCount: Int,
    val collections: List<CollectionSummary>,
)

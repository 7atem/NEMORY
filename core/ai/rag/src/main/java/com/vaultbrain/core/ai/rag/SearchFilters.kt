package com.vaultbrain.core.ai.rag

/**
 * Filters applied to a RAG query after vector search has produced candidate item IDs.
 */
data class SearchFilters(
    val lensTag: String? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val hasImage: Boolean? = null
)

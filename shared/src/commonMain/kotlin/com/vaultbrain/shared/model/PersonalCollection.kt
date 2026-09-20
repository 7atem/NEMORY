package com.vaultbrain.shared.model

/** Origin of a personal collection. P0 creates only [USER] collections. */
enum class PersonalCollectionSource {
    USER,
    SYSTEM_SUGGESTED
}

/** User-owned organization that is independent from classifications and system facets. */
data class PersonalCollection(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long? = null,
    val isPinned: Boolean = false,
    val source: PersonalCollectionSource = PersonalCollectionSource.USER,
    val itemCount: Int = 0
) {
    val isArchived: Boolean get() = archivedAt != null
}

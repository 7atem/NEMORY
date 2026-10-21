package com.vaultbrain.shared.data

/**
 * UI-facing summary of a vault item. Kept separate from the Room entity so
 * platform shells never touch storage types directly.
 */
data class VaultItemSummary(
    val id: String,
    val title: String,
    val summary: String?,
    val createdAtMillis: Long,
    val lensTags: List<String>,
    val isPinned: Boolean = false,
    /** Absolute path of the stored capture image, for list thumbnails. */
    val capturedImageUri: String? = null,
)

package com.vaultbrain.shared.data

/** Full detail of a vault item for the item screen. */
data class VaultItemDetail(
    val id: String,
    val title: String,
    val summary: String?,
    val rawOcrText: String?,
    val sourceTypeName: String,
    val lensTags: List<String>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** Absolute path of the captured image inside protected vault storage, if any. */
    val capturedImageUri: String? = null,
)

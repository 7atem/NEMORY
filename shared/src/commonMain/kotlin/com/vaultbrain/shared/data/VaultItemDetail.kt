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
    val userNotes: String? = null,
    val isArchived: Boolean = false,
    val isPinned: Boolean = false,
    /** Expiry moment in millis; 0 = none. */
    val expiryDateMillis: Long = 0,
    /** Secondary reminder moment in millis; 0 = none. */
    val secondaryAlertDateMillis: Long = 0,
    val locationName: String? = null,
    /** Capture coordinates; NaN = none. */
    val latitude: Double = Double.NaN,
    val longitude: Double = Double.NaN,
    /** Serialized key-value metadata map extracted from document. */
    val parsedMetadataJson: String = "{}",
    val primaryLensId: String? = null,
    val aiClassificationName: String? = null,
    /** JSON map of field names to bounding box rectangles (x, y, width, height) in normalized coordinates. */
    val evidenceCoordinatesJson: String? = null,
)

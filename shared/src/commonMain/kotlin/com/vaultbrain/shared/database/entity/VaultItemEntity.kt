package com.vaultbrain.shared.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.EnrichmentState
import com.vaultbrain.shared.model.ProcessingState
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.Tier

import androidx.room.Index

/**
 * Encrypted Room entity for a vault item.
 *
 * JSON-backed fields are serialized via [com.vaultbrain.core.database.util.RoomTypeConverters].
 */
@Entity(
    tableName = "vault_items",
    indices = [
        Index("primary_lens_id"),
        Index("is_archived"),
        Index("is_stealth"),
        Index("created_at"),
        Index("enrichment_state"),
        Index("indexing_state"),
        Index("needs_review"),
        Index("expiry_date")
    ]
)
data class VaultItemEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "summary")
    val summary: String? = null,

    @ColumnInfo(name = "raw_ocr_text")
    val rawOcrText: String? = null,

    @ColumnInfo(name = "source_type")
    val sourceType: SourceType,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = com.vaultbrain.shared.util.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = com.vaultbrain.shared.util.currentTimeMillis(),

    @ColumnInfo(name = "captured_image_uri")
    val capturedImageUri: String? = null,

    @ColumnInfo(name = "parsed_metadata")
    val parsedMetadata: String, // JSON map

    @ColumnInfo(name = "lens_tags")
    val lensTags: String, // JSON array

    @ColumnInfo(name = "primary_lens_id")
    val primaryLensId: String? = null,

    @ColumnInfo(name = "expiry_date")
    val expiryDate: Long? = null,

    @ColumnInfo(name = "secondary_alert_date")
    val secondaryAlertDate: Long? = null,

    @ColumnInfo(name = "recurring_rule")
    val recurringRule: String? = null,

    @ColumnInfo(name = "capture_latitude")
    val captureLatitude: Double? = null,

    @ColumnInfo(name = "capture_longitude")
    val captureLongitude: Double? = null,

    @ColumnInfo(name = "location_name")
    val locationName: String? = null,

    @ColumnInfo(name = "ai_confidence")
    val aiConfidence: Float = 0f,

    @ColumnInfo(name = "ai_classification")
    val aiClassification: Classification? = null,

    @ColumnInfo(name = "user_classification_override")
    val userClassificationOverride: Classification? = null,

    @ColumnInfo(name = "user_edited_at")
    val userEditedAt: Long? = null,

    @ColumnInfo(name = "extraction_state", defaultValue = "'COMPLETE'")
    val extractionState: ProcessingState = ProcessingState.COMPLETE,

    @ColumnInfo(name = "enrichment_state", defaultValue = "'PENDING'")
    val enrichmentState: EnrichmentState = EnrichmentState.PENDING,

    @ColumnInfo(name = "indexing_state", defaultValue = "'COMPLETE'")
    val indexingState: ProcessingState = ProcessingState.COMPLETE,

    @ColumnInfo(name = "enrichment_attempt_count", defaultValue = "0")
    val enrichmentAttemptCount: Int = 0,

    @ColumnInfo(name = "enrichment_claimed_at")
    val enrichmentClaimedAt: Long? = null,

    @ColumnInfo(name = "enrichment_last_attempt_at")
    val enrichmentLastAttemptAt: Long? = null,

    @ColumnInfo(name = "enrichment_error_code")
    val enrichmentErrorCode: String? = null,

    @ColumnInfo(name = "needs_review")
    val needsReview: Boolean = false,

    @ColumnInfo(name = "possible_duplicate_of_item_id")
    val possibleDuplicateOfItemId: String? = null,

    @ColumnInfo(name = "duplicate_similarity")
    val duplicateSimilarity: Float? = null,

    @ColumnInfo(name = "dominant_colors")
    val dominantColors: String? = null, // JSON array

    @ColumnInfo(name = "detected_objects")
    val detectedObjects: String? = null, // JSON array

    @ColumnInfo(name = "subtype")
    val subtype: String? = null,

    @ColumnInfo(name = "topics", defaultValue = "'[]'")
    val topics: String = "[]", // JSON array

    @ColumnInfo(name = "entities", defaultValue = "'[]'")
    val entities: String = "[]", // JSON array

    @ColumnInfo(name = "tags", defaultValue = "'[]'")
    val tags: String = "[]", // JSON array

    @ColumnInfo(name = "suggestions", defaultValue = "'[]'")
    val suggestions: String = "[]", // JSON array

    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "is_stealth")
    val isStealth: Boolean = false,

    @ColumnInfo(name = "user_notes")
    val userNotes: String? = null,

    @ColumnInfo(name = "custom_fields")
    val customFields: String? = null, // JSON map

    @ColumnInfo(name = "target_price")
    val targetPrice: Double? = null,

    @ColumnInfo(name = "affiliate_url")
    val affiliateUrl: String? = null,

    @ColumnInfo(name = "drive_backup_id")
    val driveBackupId: String? = null,

    @ColumnInfo(name = "last_sync_at")
    val lastSyncAt: Long? = null,

    @ColumnInfo(name = "required_lens_tier")
    val requiredLensTier: Tier = Tier.FREE,

    @ColumnInfo(name = "experience_id")
    val experienceId: String? = null
)

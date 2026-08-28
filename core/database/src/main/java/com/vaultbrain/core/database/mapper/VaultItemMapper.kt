package com.vaultbrain.core.database.mapper

import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.database.entity.VaultItemEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Maps between the encrypted [VaultItemEntity] and the domain [VaultItem].
 */
object VaultItemMapper {

    private val json = Json { ignoreUnknownKeys = true }

    fun toDomain(entity: VaultItemEntity): VaultItem = VaultItem(
        id = entity.id,
        title = entity.title,
        summary = entity.summary,
        rawOcrText = entity.rawOcrText,
        sourceType = entity.sourceType,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        capturedImageUri = entity.capturedImageUri,
        parsedMetadata = entity.parsedMetadata.let { json.decodeFromString(it) },
        lensTags = entity.lensTags.let { json.decodeFromString(it) },
        // Legacy rows (pre-migration) have no stored primary lens; fall back to the first tag.
        primaryLensId = entity.primaryLensId
            ?: entity.lensTags.let { json.decodeFromString<List<String>>(it) }.firstOrNull(),
        expiryDate = entity.expiryDate,
        secondaryAlertDate = entity.secondaryAlertDate,
        recurringRule = entity.recurringRule,
        captureLatitude = entity.captureLatitude,
        captureLongitude = entity.captureLongitude,
        locationName = entity.locationName,
        aiConfidence = entity.aiConfidence,
        aiClassification = entity.aiClassification,
        userClassificationOverride = entity.userClassificationOverride,
        userEditedAt = entity.userEditedAt,
        extractionState = entity.extractionState,
        enrichmentState = entity.enrichmentState,
        indexingState = entity.indexingState,
        enrichmentAttemptCount = entity.enrichmentAttemptCount,
        enrichmentClaimedAt = entity.enrichmentClaimedAt,
        enrichmentLastAttemptAt = entity.enrichmentLastAttemptAt,
        enrichmentErrorCode = entity.enrichmentErrorCode,
        needsReview = entity.needsReview,
        possibleDuplicateOfItemId = entity.possibleDuplicateOfItemId,
        duplicateSimilarity = entity.duplicateSimilarity,
        dominantColors = entity.dominantColors?.let { json.decodeFromString(it) } ?: emptyList(),
        detectedObjects = entity.detectedObjects?.let { json.decodeFromString(it) } ?: emptyList(),
        subtype = entity.subtype,
        topics = json.decodeFromString(entity.topics),
        entities = json.decodeFromString(entity.entities),
        tags = json.decodeFromString(entity.tags),
        suggestions = json.decodeFromString(entity.suggestions),
        isPinned = entity.isPinned,
        isArchived = entity.isArchived,
        isStealth = entity.isStealth,
        userNotes = entity.userNotes,
        customFields = entity.customFields?.let { json.decodeFromString(it) } ?: emptyMap(),
        targetPrice = entity.targetPrice,
        affiliateUrl = entity.affiliateUrl,
        driveBackupId = entity.driveBackupId,
        lastSyncAt = entity.lastSyncAt,
        requiredLensTier = entity.requiredLensTier,
        experienceId = entity.experienceId
    )

    fun toEntity(domain: VaultItem): VaultItemEntity = VaultItemEntity(
        id = domain.id,
        title = domain.title,
        summary = domain.summary,
        rawOcrText = domain.rawOcrText,
        sourceType = domain.sourceType,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
        capturedImageUri = domain.capturedImageUri,
        parsedMetadata = json.encodeToString(domain.parsedMetadata),
        lensTags = json.encodeToString(domain.lensTags.toList()),
        primaryLensId = domain.primaryLensId,
        expiryDate = domain.expiryDate,
        secondaryAlertDate = domain.secondaryAlertDate,
        recurringRule = domain.recurringRule,
        captureLatitude = domain.captureLatitude,
        captureLongitude = domain.captureLongitude,
        locationName = domain.locationName,
        aiConfidence = domain.aiConfidence,
        aiClassification = domain.aiClassification,
        userClassificationOverride = domain.userClassificationOverride,
        userEditedAt = domain.userEditedAt,
        extractionState = domain.extractionState,
        enrichmentState = domain.enrichmentState,
        indexingState = domain.indexingState,
        enrichmentAttemptCount = domain.enrichmentAttemptCount,
        enrichmentClaimedAt = domain.enrichmentClaimedAt,
        enrichmentLastAttemptAt = domain.enrichmentLastAttemptAt,
        enrichmentErrorCode = domain.enrichmentErrorCode,
        needsReview = domain.needsReview,
        possibleDuplicateOfItemId = domain.possibleDuplicateOfItemId,
        duplicateSimilarity = domain.duplicateSimilarity,
        dominantColors = json.encodeToString(domain.dominantColors),
        detectedObjects = json.encodeToString(domain.detectedObjects),
        subtype = domain.subtype,
        topics = json.encodeToString(domain.topics),
        entities = json.encodeToString(domain.entities),
        tags = json.encodeToString(domain.tags),
        suggestions = json.encodeToString(domain.suggestions),
        isPinned = domain.isPinned,
        isArchived = domain.isArchived,
        isStealth = domain.isStealth,
        userNotes = domain.userNotes,
        customFields = json.encodeToString(domain.customFields),
        targetPrice = domain.targetPrice,
        affiliateUrl = domain.affiliateUrl,
        driveBackupId = domain.driveBackupId,
        lastSyncAt = domain.lastSyncAt,
        requiredLensTier = domain.requiredLensTier,
        experienceId = domain.experienceId
    )
}

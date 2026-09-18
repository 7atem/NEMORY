package com.vaultbrain.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.vaultbrain.core.common.model.CollectionSuggestionStatus

/**
 * Machine-proposed item↔collection association. Strictly separate from accepted
 * membership ([PersonalCollectionMembershipEntity]); rows are never created by the
 * user directly and REJECTED rows persist as suppression evidence.
 */
@Entity(
    tableName = "personal_collection_suggestions",
    primaryKeys = ["collection_id", "item_id"],
    foreignKeys = [
        ForeignKey(
            entity = PersonalCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VaultItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["item_id"]),
        Index(value = ["status"])
    ]
)
data class PersonalCollectionSuggestionEntity(
    @ColumnInfo(name = "collection_id")
    val collectionId: String,
    @ColumnInfo(name = "item_id")
    val itemId: String,
    @ColumnInfo(name = "confidence")
    val confidence: Float,
    @ColumnInfo(name = "status")
    val status: CollectionSuggestionStatus = CollectionSuggestionStatus.SUGGESTED,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

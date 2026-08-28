package com.vaultbrain.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "personal_collection_memberships",
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
    indices = [Index(value = ["item_id"])]
)
data class PersonalCollectionMembershipEntity(
    @ColumnInfo(name = "collection_id")
    val collectionId: String,
    @ColumnInfo(name = "item_id")
    val itemId: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

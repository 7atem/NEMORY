package com.vaultbrain.shared.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "knowledge_entities")
data class KnowledgeEntityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val aliases: String // JSON array of strings
)

@Entity(
    tableName = "item_entities",
    primaryKeys = ["itemId", "entityId"],
    foreignKeys = [
        ForeignKey(
            entity = VaultItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = KnowledgeEntityEntity::class,
            parentColumns = ["id"],
            childColumns = ["entityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("entityId")]
)
data class ItemEntityCrossRef(
    val itemId: String,
    val entityId: String
)

@Entity(
    tableName = "relationships",
    foreignKeys = [
        ForeignKey(
            entity = VaultItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceItemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VaultItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sourceItemId"), Index("targetItemId"), Index("type")]
)
data class RelationshipEntity(
    @PrimaryKey val id: String,
    val sourceItemId: String,
    val targetItemId: String,
    val type: String,
    @ColumnInfo(defaultValue = "''") val evidence: String = "",
    @ColumnInfo(defaultValue = "1.0") val confidence: Float = 1.0f,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0L
)

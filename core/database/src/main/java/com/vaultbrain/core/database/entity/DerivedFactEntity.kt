package com.vaultbrain.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Extractive knowledge graph edge; item deletion cascades to all source facts. */
@Entity(tableName = "derived_facts", primaryKeys = ["sourceItemId", "kind", "field", "value"],
    foreignKeys = [ForeignKey(entity = VaultItemEntity::class, parentColumns = ["id"],
        childColumns = ["sourceItemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sourceItemId"), Index("kind", "value")])
data class DerivedFactEntity(
    val sourceItemId: String,
    val kind: String,
    val field: String,
    val value: String,
    val evidence: String,
    val sourceUpdatedAt: Long,
    val createdAt: Long,
    val modelVersion: String
)

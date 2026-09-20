package com.vaultbrain.shared.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vaultbrain.shared.model.PersonalCollectionSource

@Entity(
    tableName = "personal_collections",
    indices = [
        Index(value = ["archived_at"]),
        Index(value = ["is_pinned"]),
        Index(value = ["updated_at"])
    ]
)
data class PersonalCollectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "archived_at")
    val archivedAt: Long? = null,
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false,
    @ColumnInfo(name = "source")
    val source: PersonalCollectionSource = PersonalCollectionSource.USER
)

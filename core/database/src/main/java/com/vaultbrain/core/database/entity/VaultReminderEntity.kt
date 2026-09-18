package com.vaultbrain.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vaultbrain.core.common.model.VaultReminderStatus

@Entity(
    tableName = "vault_reminders",
    foreignKeys = [
        ForeignKey(
            entity = VaultItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["vault_item_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PersonalCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["personal_collection_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ExternalRecordEntity::class,
            parentColumns = ["connector_id", "account_id", "external_id"],
            childColumns = ["external_connector_id", "external_account_id", "external_record_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["due_at"]),
        Index(value = ["status"]),
        Index(value = ["vault_item_id"]),
        Index(value = ["personal_collection_id"]),
        Index(value = ["external_connector_id", "external_account_id", "external_record_id"])
    ]
)
data class VaultReminderEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "due_at")
    val dueAt: Long,
    @ColumnInfo(name = "status")
    val status: VaultReminderStatus,
    @ColumnInfo(name = "vault_item_id")
    val vaultItemId: String? = null,
    @ColumnInfo(name = "external_connector_id")
    val externalConnectorId: String? = null,
    @ColumnInfo(name = "external_account_id")
    val externalAccountId: String? = null,
    @ColumnInfo(name = "external_record_id")
    val externalRecordId: String? = null,
    @ColumnInfo(name = "personal_collection_id")
    val personalCollectionId: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

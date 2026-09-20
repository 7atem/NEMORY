package com.vaultbrain.shared.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.vaultbrain.shared.model.external.ExternalRecordType
import com.vaultbrain.shared.model.external.ExternalRetention
import com.vaultbrain.shared.model.external.ExternalSource
import com.vaultbrain.shared.model.external.SensitivityLevel

/**
 * A contextual record imported from an external source (calendar, share sheet, task app, etc.).
 *
 * Identity is scoped to a connector + account + the source's own id so the same logical
 * record can be updated on every sync without creating duplicates.
 */
@Entity(
    tableName = "external_records",
    primaryKeys = ["connector_id", "account_id", "external_id"],
    indices = [
        Index(value = ["source"]),
        Index(value = ["record_type"]),
        Index(value = ["connector_id"]),
        Index(value = ["start_at"]),
        Index(value = ["due_at"]),
        Index(value = ["expires_at"]),
        Index(value = ["created_at"])
    ]
)
data class ExternalRecordEntity(
    @ColumnInfo(name = "connector_id")
    val connectorId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "external_id")
    val externalId: String,

    @ColumnInfo(name = "source")
    val source: ExternalSource,

    @ColumnInfo(name = "record_type")
    val recordType: ExternalRecordType,

    @ColumnInfo(name = "retention")
    val retention: ExternalRetention = ExternalRetention.INDEXED_REFERENCE,

    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "start_at")
    val startAt: Long? = null,

    @ColumnInfo(name = "end_at")
    val endAt: Long? = null,

    @ColumnInfo(name = "due_at")
    val dueAt: Long? = null,

    @ColumnInfo(name = "sensitivity")
    val sensitivity: SensitivityLevel = SensitivityLevel.NORMAL,

    /**
     * Opaque source-specific payload (reminder ids, event locations, email thread ids, etc.)
     * stored as a JSON map via RoomTypeConverters.
     */
    @ColumnInfo(name = "payload")
    val payload: Map<String, String>? = null,

    @ColumnInfo(name = "deep_link_uri")
    val deepLinkUri: String? = null,

    @ColumnInfo(name = "hash")
    val hash: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = com.vaultbrain.shared.util.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = com.vaultbrain.shared.util.currentTimeMillis(),

    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,

    @ColumnInfo(name = "seen_at")
    val seenAt: Long? = null,

    @ColumnInfo(name = "is_resolved")
    val isResolved: Boolean = false
)

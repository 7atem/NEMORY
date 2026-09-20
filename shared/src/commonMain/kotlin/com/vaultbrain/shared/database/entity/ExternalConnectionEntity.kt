package com.vaultbrain.shared.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.vaultbrain.shared.model.external.ConnectionState

/**
 * Runtime state for a connector/account pair (e.g. "Calendar / work@example.com").
 *
 * This table is the source of truth for the Connections UI and for deciding which
 * connectors can be queried during context assembly.
 */
@Entity(
    tableName = "external_connections",
    primaryKeys = ["connector_id", "account_id"],
    indices = [
        Index(value = ["state"]),
        Index(value = ["updated_at"])
    ]
)
data class ExternalConnectionEntity(
    @ColumnInfo(name = "connector_id")
    val connectorId: String,

    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "state")
    val state: ConnectionState = ConnectionState.DISCONNECTED,

    @ColumnInfo(name = "account_name")
    val accountName: String? = null,

    @ColumnInfo(name = "account_icon_uri")
    val accountIconUri: String? = null,

    /**
     * Advertised capabilities for this connection, persisted as a JSON set of
     * [com.vaultbrain.shared.model.external.ConnectorCapability] names.
     */
    @ColumnInfo(name = "capabilities")
    val capabilities: Set<String> = emptySet(),

    @ColumnInfo(name = "metadata_json")
    val metadataJson: String? = null,

    @ColumnInfo(name = "last_sync_at")
    val lastSyncAt: Long? = null,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = com.vaultbrain.shared.util.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = com.vaultbrain.shared.util.currentTimeMillis()
)

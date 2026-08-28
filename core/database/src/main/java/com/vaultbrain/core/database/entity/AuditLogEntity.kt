package com.vaultbrain.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local-only encrypted audit trail of sensitive actions.
 */
@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String, // VIEW, EDIT, DELETE, EXPORT, AUTH_FAIL, CAPTURE
    val itemId: String? = null,
    val details: String? = null
)

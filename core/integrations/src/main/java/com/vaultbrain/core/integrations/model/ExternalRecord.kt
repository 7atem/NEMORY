package com.vaultbrain.core.integrations.model

import com.vaultbrain.shared.model.external.ExternalRecordType
import com.vaultbrain.shared.model.external.ExternalRetention
import com.vaultbrain.shared.model.external.ExternalSource
import com.vaultbrain.shared.model.external.SensitivityLevel

/**
 * User-readable representation of a record living outside the vault.
 *
 * Mirrors [com.vaultbrain.shared.database.entity.ExternalRecordEntity] but uses the
 * typed enums and hides storage concerns.
 */
data class ExternalRecord(
    val connectorId: String,
    val accountId: String,
    val externalId: String,
    val source: ExternalSource,
    val recordType: ExternalRecordType,
    val retention: ExternalRetention = ExternalRetention.INDEXED_REFERENCE,
    val title: String? = null,
    val description: String? = null,
    val startAt: Long? = null,
    val endAt: Long? = null,
    val dueAt: Long? = null,
    val sensitivity: SensitivityLevel = SensitivityLevel.NORMAL,
    val payload: Map<String, String> = emptyMap(),
    val deepLinkUri: String? = null,
    val hash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val seenAt: Long? = null,
    val isResolved: Boolean = false
)

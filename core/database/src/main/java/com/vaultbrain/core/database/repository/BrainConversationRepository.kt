package com.vaultbrain.core.database.repository

import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.database.dao.BrainMessageDao
import com.vaultbrain.core.database.dao.VaultItemDao
import com.vaultbrain.core.database.entity.BrainMessageEntity
import com.vaultbrain.core.database.mapper.VaultItemMapper
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class StoredBrainMessage(
    val id: String,
    val role: String,
    val text: String,
    val sources: List<VaultItem> = emptyList(),
    val confidence: Float = 0f,
    val isError: Boolean = false,
    val originalQuery: String? = null,
    val evidenceKind: String? = null,
    val evidenceHeadline: String? = null,
    val evidenceFacts: List<String> = emptyList(),
    val responseOrigin: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** Keeps Brain history encrypted in the same local Room database as the vault. */
@Singleton
open class BrainConversationRepository @Inject constructor(
    private val brainMessageDao: BrainMessageDao,
    private val vaultItemDao: VaultItemDao
) {
    fun observeAll(): Flow<List<StoredBrainMessage>> = combine(
        brainMessageDao.observeAll(),
        DecoySessionState.isDecoy
    ) { messages, isDecoy ->
        if (isDecoy) return@combine emptyList()
        val sourceIds = messages.flatMap(BrainMessageEntity::sourceItemIds).distinct()
        val sources = if (sourceIds.isEmpty()) emptyMap() else {
            vaultItemDao.getByIds(sourceIds).associate { it.id to VaultItemMapper.toDomain(it) }
        }
        messages.map { entity ->
            StoredBrainMessage(
                id = entity.id,
                role = entity.role,
                text = entity.text,
                sources = entity.sourceItemIds.mapNotNull(sources::get),
                confidence = entity.confidence,
                isError = entity.isError,
                originalQuery = entity.originalQuery,
                evidenceKind = entity.evidenceKind,
                evidenceHeadline = entity.evidenceHeadline,
                evidenceFacts = entity.evidenceFacts,
                responseOrigin = entity.responseOrigin,
                createdAt = entity.createdAt
            )
        }
    }

    suspend fun upsert(message: StoredBrainMessage) {
        if (DecoySessionState.isDecoy.value) return
        require(message.role == ROLE_USER || message.role == ROLE_ASSISTANT)
        brainMessageDao.upsert(
            BrainMessageEntity(
                id = message.id,
                role = message.role,
                text = message.text,
                sourceItemIds = message.sources.map(VaultItem::id).distinct(),
                confidence = message.confidence,
                isError = message.isError,
                originalQuery = message.originalQuery,
                evidenceKind = message.evidenceKind,
                evidenceHeadline = message.evidenceHeadline,
                evidenceFacts = message.evidenceFacts,
                responseOrigin = message.responseOrigin,
                createdAt = message.createdAt
            )
        )
    }

    suspend fun clear() {
        if (!DecoySessionState.isDecoy.value) brainMessageDao.clear()
    }

    suspend fun deleteByIds(ids: List<String>) {
        if (!DecoySessionState.isDecoy.value && ids.isNotEmpty()) brainMessageDao.deleteByIds(ids)
    }

    companion object {
        const val ROLE_USER = "USER"
        const val ROLE_ASSISTANT = "ASSISTANT"
    }
}

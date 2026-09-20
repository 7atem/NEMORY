package com.vaultbrain.core.database.repository

import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.database.VaultDatabase
import com.vaultbrain.shared.database.entity.DerivedFactEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Derived data never participates in decoy sessions or overrides source records. */
@Singleton
class KnowledgeRepository @Inject constructor(private val database: VaultDatabase, private val vault: VaultRepository) {
    suspend fun facts(itemId: String): List<DerivedFactEntity> {
        if (DecoySessionState.isDecoy.value) return emptyList()
        val result = database.derivedFactDao().forItem(itemId)
        return if (DecoySessionState.isDecoy.value) emptyList() else result
    }

    suspend fun getRelationships(itemId: String): List<com.vaultbrain.shared.database.entity.RelationshipEntity> {
        if (DecoySessionState.isDecoy.value) return emptyList()
        val result = database.knowledgeGraphDao().getRelationshipsForItem(itemId)
        return if (DecoySessionState.isDecoy.value) emptyList() else result
    }

    suspend fun relationshipsForItems(itemIds: List<String>): List<com.vaultbrain.shared.database.entity.RelationshipEntity> {
        if (DecoySessionState.isDecoy.value) return emptyList()
        val result = database.knowledgeGraphDao().getRelationshipsForItems(itemIds)
        return if (DecoySessionState.isDecoy.value) emptyList() else result
    }

    suspend fun related(itemId: String): List<com.vaultbrain.shared.model.VaultItem> {
        if (facts(itemId).isEmpty()) return emptyList()
        val ids = database.derivedFactDao().relatedIds(itemId)
        return vault.getByIds(ids).filter { !it.isArchived && !it.isStealth }
    }

    suspend fun replace(itemId: String, facts: List<DerivedFactEntity>) {
        if (DecoySessionState.isDecoy.value) return
        val item = vault.getById(itemId) ?: return
        if (item.isArchived || item.isStealth || item.enrichmentState == com.vaultbrain.shared.model.EnrichmentState.SKIPPED_PRIVACY) return
        val source = item.rawOcrText.orEmpty()
        require(facts.size <= 24 && facts.all {
            it.sourceItemId == itemId && it.sourceUpdatedAt == item.updatedAt &&
                it.evidence.isNotBlank() && source.contains(it.evidence) && it.evidence.contains(it.value)
        })
        database.derivedFactDao().replace(itemId, facts)
    }

    suspend fun replaceRelationships(itemId: String, relationships: List<com.vaultbrain.shared.database.entity.RelationshipEntity>) {
        if (DecoySessionState.isDecoy.value) return
        val item = vault.getById(itemId) ?: return
        if (DecoySessionState.isDecoy.value) return
        if (item.isArchived || item.isStealth || item.enrichmentState == com.vaultbrain.shared.model.EnrichmentState.SKIPPED_PRIVACY) return
        val source = item.rawOcrText.orEmpty()
        val validTypes = com.vaultbrain.shared.model.RelationshipType.entries.map { it.name }.toSet()
        val coerced = relationships.map { it.copy(confidence = it.confidence.coerceIn(0f, 1f)) }
        require(coerced.size <= 24 && coerced.all {
            it.type in validTypes && (it.sourceItemId == itemId || it.targetItemId == itemId) &&
                (it.evidence.isBlank() || source.contains(it.evidence))
        })
        val endpointIds = coerced.flatMap { listOf(it.sourceItemId, it.targetItemId) }.distinct().filter { it != itemId }
        val endpoints = vault.getByIds(endpointIds).filter { !it.isArchived && !it.isStealth }.map { it.id }.toSet()
        if (DecoySessionState.isDecoy.value) return
        require(endpointIds.all { it in endpoints })
        database.knowledgeGraphDao().replaceRelationshipsInvolving(itemId, coerced)
    }
}

package com.vaultbrain.feature.capture

import com.vaultbrain.core.ai.llm.DocumentUnderstandingV2
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.ai.llm.ReasoningBudget
import com.vaultbrain.core.common.model.EnrichmentState
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.database.entity.DerivedFactEntity
import com.vaultbrain.core.database.repository.KnowledgeRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** Optional post-save extraction. A failed pass has no effect on the captured item. */
class DocumentKnowledgeIndexer @Inject constructor(private val model: LlmClient, private val knowledge: KnowledgeRepository) {
    suspend fun index(item: VaultItem) {
        if (DecoySessionState.isDecoy.value || item.isStealth || item.isArchived ||
            item.enrichmentState == EnrichmentState.SKIPPED_PRIVACY || !model.isAvailable()) return
        val source = item.rawOcrText?.take(6000)?.takeIf { it.isNotBlank() } ?: return
        try {
            if (knowledge.facts(item.id).isNotEmpty()) return
            val version = model.modelVersion
            val raw = model.generateForTask(DocumentUnderstandingV2.prompt(source), ReasoningBudget.NORMAL)
            val result = DocumentUnderstandingV2.parse(raw, source) ?: return
            knowledge.replace(item.id, result.facts.map {
                DerivedFactEntity(item.id, it.kind, it.field, it.value, it.evidence,
                    item.updatedAt, System.currentTimeMillis(), version)
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { /* Keep the deterministic capture. */ }
    }
}

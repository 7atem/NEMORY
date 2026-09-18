package com.vaultbrain.evaluation.adapters

import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.vectorstore.VectorStore
import com.vaultbrain.evaluation.models.RetrievalCase
import com.vaultbrain.evaluation.runners.RetrievalEvaluator
import com.vaultbrain.evaluation.runners.RetrievalMode
import com.vaultbrain.evaluation.runners.RetrievalResult
import kotlinx.coroutines.runBlocking

class RetrievalAdapter(
    private val vaultRepository: VaultRepository,
    private val vectorStore: VectorStore,
    private val embedder: (String) -> FloatArray // Abstract the vault embedding generator
) {
    fun runEvaluationCase(
        case: RetrievalCase, 
        evaluator: RetrievalEvaluator
    ): List<RetrievalResult> = runBlocking {
        
        // 1. FTS Only
        val ftsResults = vaultRepository.search(case.query).map { it.id }
        val ftsEval = evaluator.evaluate(case, ftsResults, RetrievalMode.FTS_ONLY)
        
        // 2. Vector Only
        val queryEmbedding = embedder(case.query)
        val vectorResults = vectorStore.nearestNeighbors(queryEmbedding, topK = 10).map { it.itemId }
        val vecEval = evaluator.evaluate(case, vectorResults, RetrievalMode.VECTOR_ONLY)
        
        // 3. Hybrid (FTS + Vector without reranker)
        // Simplified RRF (Reciprocal Rank Fusion) for the skeleton
        val hybridResults = rrf(ftsResults, vectorResults).take(10)
        val hybridEval = evaluator.evaluate(case, hybridResults, RetrievalMode.HYBRID)
        
        // 4. Hybrid Reranked 
        // Real implementation would pass to RagEngine reranker. Here we assume identical to hybrid for skeleton.
        val rerankedEval = evaluator.evaluate(case, hybridResults, RetrievalMode.HYBRID_RERANKED)
        
        listOf(ftsEval, vecEval, hybridEval, rerankedEval)
    }

    private fun rrf(list1: List<String>, list2: List<String>, k: Int = 60): List<String> {
        val scores = mutableMapOf<String, Double>()
        list1.forEachIndexed { index, id ->
            scores[id] = (scores[id] ?: 0.0) + (1.0 / (k + index + 1))
        }
        list2.forEachIndexed { index, id ->
            scores[id] = (scores[id] ?: 0.0) + (1.0 / (k + index + 1))
        }
        return scores.entries.sortedByDescending { it.value }.map { it.key }
    }
}

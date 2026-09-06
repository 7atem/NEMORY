package com.vaultbrain.evaluation.runners

import com.vaultbrain.evaluation.models.RetrievalCase

data class RetrievalResult(
    val caseId: String,
    val query: String,
    val expectedDocs: List<String>,
    val retrievedDocs: List<String>,
    val mode: RetrievalMode,
    val rankOfFirstRelevant: Int? // 1-indexed
)

enum class RetrievalMode {
    FTS_ONLY, VECTOR_ONLY, HYBRID, HYBRID_RERANKED
}

data class RetrievalMetrics(
    val mode: RetrievalMode,
    val totalQueries: Int,
    val recallAt1: Double,
    val recallAt3: Double,
    val recallAt5: Double,
    val recallAt10: Double,
    val mrr: Double
)

class RetrievalEvaluator {

    fun evaluate(case: RetrievalCase, retrievedDocs: List<String>, mode: RetrievalMode): RetrievalResult {
        val expectedSet = case.relevant_documents.toSet()
        val rankOfFirstRelevant = retrievedDocs.indexOfFirst { it in expectedSet }.let { if (it == -1) null else it + 1 }
        
        return RetrievalResult(
            caseId = case.id,
            query = case.query,
            expectedDocs = case.relevant_documents,
            retrievedDocs = retrievedDocs,
            mode = mode,
            rankOfFirstRelevant = rankOfFirstRelevant
        )
    }

    fun aggregate(results: List<RetrievalResult>): Map<RetrievalMode, RetrievalMetrics> {
        return results.groupBy { it.mode }.mapValues { (mode, modeResults) ->
            val total = modeResults.size
            if (total == 0) return@mapValues RetrievalMetrics(mode, 0, 0.0, 0.0, 0.0, 0.0, 0.0)
            
            var r1 = 0
            var r3 = 0
            var r5 = 0
            var r10 = 0
            var sumReciprocalRank = 0.0
            
            modeResults.forEach { res ->
                val rank = res.rankOfFirstRelevant
                if (rank != null) {
                    if (rank <= 1) r1++
                    if (rank <= 3) r3++
                    if (rank <= 5) r5++
                    if (rank <= 10) r10++
                    sumReciprocalRank += 1.0 / rank
                }
            }
            
            RetrievalMetrics(
                mode = mode,
                totalQueries = total,
                recallAt1 = r1.toDouble() / total,
                recallAt3 = r3.toDouble() / total,
                recallAt5 = r5.toDouble() / total,
                recallAt10 = r10.toDouble() / total,
                mrr = sumReciprocalRank / total
            )
        }
    }
}

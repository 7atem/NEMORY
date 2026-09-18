package com.vaultbrain.evaluation.runners

import com.vaultbrain.evaluation.models.QaCase

enum class ModelVersion {
    GEMMA_LOCAL,
    GEMINI_NANO,
    REFERENCE_MODEL
}

data class ModelEvaluationResult(
    val caseId: String,
    val modelVersion: ModelVersion,
    val ragResult: RagResult
)

class ModelEvaluator(private val ragEvaluator: RagEvaluator) {
    
    fun evaluate(case: QaCase, modelVersion: ModelVersion, generatedAnswer: String, citations: List<String>): ModelEvaluationResult {
        val ragResult = ragEvaluator.evaluate(case, RagMode.ORACLE_FACTS, generatedAnswer, citations)
        
        return ModelEvaluationResult(
            caseId = case.id,
            modelVersion = modelVersion,
            ragResult = ragResult
        )
    }
    
    fun aggregate(results: List<ModelEvaluationResult>): Map<ModelVersion, RagMetrics> {
        val ragResultsGrouped = results.groupBy({ it.modelVersion }, { it.ragResult })
        return ragResultsGrouped.mapValues { (version, ragResults) ->
            ragEvaluator.aggregate(ragResults.map { it.copy(mode = RagMode.ORACLE_FACTS) })[RagMode.ORACLE_FACTS]!!
        }
    }
}

package com.vaultbrain.evaluation.runners

import com.vaultbrain.evaluation.models.QaCase

enum class PromptVersion {
    PROMPT_CURRENT,
    PROMPT_STRICT,
    PROMPT_STRUCTURED
}

data class PromptEvaluationResult(
    val caseId: String,
    val promptVersion: PromptVersion,
    val ragResult: RagResult
)

class PromptEvaluator(private val ragEvaluator: RagEvaluator) {
    
    fun evaluate(case: QaCase, promptVersion: PromptVersion, generatedAnswer: String, citations: List<String>): PromptEvaluationResult {
        // Evaluate using the RagEvaluator's Oracle mode logic 
        // since prompt evaluation assumes Oracle Facts/Context
        val ragResult = ragEvaluator.evaluate(case, RagMode.ORACLE_FACTS, generatedAnswer, citations)
        
        return PromptEvaluationResult(
            caseId = case.id,
            promptVersion = promptVersion,
            ragResult = ragResult
        )
    }
    
    fun aggregate(results: List<PromptEvaluationResult>): Map<PromptVersion, RagMetrics> {
        val ragResultsGrouped = results.groupBy({ it.promptVersion }, { it.ragResult })
        return ragResultsGrouped.mapValues { (version, ragResults) ->
            // Temporarily mapping them all to ORACLE_FACTS to use RagEvaluator's aggregation
            ragEvaluator.aggregate(ragResults.map { it.copy(mode = RagMode.ORACLE_FACTS) })[RagMode.ORACLE_FACTS]!!
        }
    }
}

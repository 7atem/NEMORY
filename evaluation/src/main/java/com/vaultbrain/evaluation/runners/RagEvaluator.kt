package com.vaultbrain.evaluation.runners

import com.vaultbrain.evaluation.models.QaCase

data class RagResult(
    val caseId: String,
    val mode: RagMode,
    val answer: String,
    val isCorrect: Boolean,
    val isFaithful: Boolean,
    val citationsValid: Boolean,
    val hasHallucinations: Boolean,
    val abstainedCorrectly: Boolean
)

enum class RagMode {
    FULL_END_TO_END,
    PERFECT_OCR,
    PERFECT_STRUCTURED,
    ORACLE_RETRIEVAL,
    ORACLE_FACTS
}

data class RagMetrics(
    val mode: RagMode,
    val totalCases: Int,
    val accuracy: Double,
    val faithfulness: Double,
    val citationCorrectness: Double,
    val hallucinationRate: Double,
    val abstentionAccuracy: Double
)

class RagEvaluator {

    fun evaluate(case: QaCase, mode: RagMode, generatedAnswer: String, providedCitations: List<String>): RagResult {
        // In a real implementation, we'd use ground truth regexes or an LLM judge to determine exact correctness.
        // For the evaluation framework skeleton, we provide deterministic mocks or simple keyword matches.
        
        val isCorrect = case.acceptable_answers.any { generatedAnswer.contains(it, ignoreCase = true) }
        val abstainedCorrectly = if (case.must_abstain) {
            generatedAnswer.contains("I don't know", ignoreCase = true) || generatedAnswer.contains("cannot find", ignoreCase = true)
        } else {
            !generatedAnswer.contains("I don't know", ignoreCase = true)
        }
        
        val citationsValid = if (case.citation_requirements?.require_citations == true) {
            providedCitations.isNotEmpty() && providedCitations.all { case.relevant_documents.contains(it) }
        } else {
            true
        }

        return RagResult(
            caseId = case.id,
            mode = mode,
            answer = generatedAnswer,
            isCorrect = isCorrect,
            isFaithful = isCorrect, // Simplified for skeleton
            citationsValid = citationsValid,
            hasHallucinations = !isCorrect && !abstainedCorrectly && !case.must_abstain,
            abstainedCorrectly = abstainedCorrectly
        )
    }

    fun aggregate(results: List<RagResult>): Map<RagMode, RagMetrics> {
        return results.groupBy { it.mode }.mapValues { (mode, modeResults) ->
            val total = modeResults.size
            if (total == 0) return@mapValues RagMetrics(mode, 0, 0.0, 0.0, 0.0, 0.0, 0.0)
            
            val correctCount = modeResults.count { it.isCorrect }
            val faithfulCount = modeResults.count { it.isFaithful }
            val validCitationsCount = modeResults.count { it.citationsValid }
            val hallucinationCount = modeResults.count { it.hasHallucinations }
            val correctAbstentionsCount = modeResults.count { it.abstainedCorrectly }
            
            RagMetrics(
                mode = mode,
                totalCases = total,
                accuracy = correctCount.toDouble() / total,
                faithfulness = faithfulCount.toDouble() / total,
                citationCorrectness = validCitationsCount.toDouble() / total,
                hallucinationRate = hallucinationCount.toDouble() / total,
                abstentionAccuracy = correctAbstentionsCount.toDouble() / total
            )
        }
    }
}

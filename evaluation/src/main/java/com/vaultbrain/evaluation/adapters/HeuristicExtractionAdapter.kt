package com.vaultbrain.evaluation.adapters

import com.vaultbrain.core.ai.heuristics.HeuristicExtractor
import com.vaultbrain.evaluation.models.DocumentCase
import com.vaultbrain.evaluation.models.ExtractionTruth
import com.vaultbrain.evaluation.runners.ExtractionEvaluator
import com.vaultbrain.evaluation.runners.ExtractionResult

class HeuristicExtractionAdapter(
    private val extractor: HeuristicExtractor = HeuristicExtractor()
) {
    fun runEvaluationCase(docCase: DocumentCase, evaluator: ExtractionEvaluator): ExtractionResult {
        // Run production extractor
        val result = extractor.extract(docCase.ground_truth_text)
        
        // Map back to truth
        val truth = ExtractionTruth(
            case_id = docCase.id,
            expected_classification = docCase.document_type,
            expected_fields = docCase.fields
        )
        
        return evaluator.evaluate(
            truth = truth,
            predictedClassification = result.inferredClassification.name,
            predictedFields = result.metadata
        )
    }
}

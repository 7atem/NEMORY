package com.vaultbrain.evaluation.adapters

import com.vaultbrain.core.ai.heuristics.HeuristicExtractor
import com.vaultbrain.evaluation.models.DocumentCase
import com.vaultbrain.evaluation.runners.ExtractionEvaluator
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdapterWiringTests {

    @Test
    fun testHeuristicAdapter() {
        val extractor = HeuristicExtractor()
        val adapter = HeuristicExtractionAdapter(extractor)
        
        val docCase = DocumentCase(
            id = "test_case",
            source_type = "synthetic",
            document_type = "RECEIPT",
            ground_truth_text = "Coffee Shop\nTotal: 4.50",
            fields = mapOf("total" to "4.50")
        )
        
        val evaluator = ExtractionEvaluator()
        val result = adapter.runEvaluationCase(docCase, evaluator)
        
        // Ensure the adapter successfully executed the real extractor and evaluated it
        assertTrue(result.classificationCorrect)
    }
}

package com.vaultbrain.evaluation.runners

import com.vaultbrain.evaluation.models.*
import com.vaultbrain.evaluation.perturbations.OcrNoisePerturber
import org.junit.Assert.*
import org.junit.Test

class EvaluatorTests {

    @Test
    fun testExtractionEvaluator() {
        val evaluator = ExtractionEvaluator()
        val truth = ExtractionTruth(
            case_id = "test_1",
            expected_classification = "RECEIPT",
            expected_fields = mapOf("total" to "4.50", "merchant" to "Coffee Shop")
        )
        
        val result = evaluator.evaluate(
            truth,
            predictedClassification = "RECEIPT",
            predictedFields = mapOf("total" to "4.50", "merchant" to "Coffee shop")
        )
        
        assertTrue(result.classificationCorrect)
        assertTrue(result.fieldResults["total"]!!.exactMatch)
        assertFalse(result.fieldResults["merchant"]!!.exactMatch)
        assertTrue(result.fieldResults["merchant"]!!.normalizedMatch)
        
        val metrics = evaluator.aggregate(listOf(result))
        assertEquals(1.0, metrics.classificationAccuracy, 0.0)
        assertEquals(0.5, metrics.fieldExactMatchAccuracy, 0.0)
        assertEquals(1.0, metrics.fieldNormalizedMatchAccuracy, 0.0)
    }

    @Test
    fun testRetrievalEvaluator() {
        val evaluator = RetrievalEvaluator()
        val case = RetrievalCase("q1", "Find insurance", listOf("doc1", "doc2"))
        
        val res1 = evaluator.evaluate(case, listOf("doc3", "doc1"), RetrievalMode.FTS_ONLY)
        assertEquals(2, res1.rankOfFirstRelevant) // 1-indexed, doc1 is at index 1 -> rank 2
        
        val res2 = evaluator.evaluate(case, listOf("doc3", "doc4"), RetrievalMode.VECTOR_ONLY)
        assertNull(res2.rankOfFirstRelevant)
        
        val metrics = evaluator.aggregate(listOf(res1, res2))
        
        val ftsMetrics = metrics[RetrievalMode.FTS_ONLY]!!
        assertEquals(1, ftsMetrics.totalQueries)
        assertEquals(0.0, ftsMetrics.recallAt1, 0.0)
        assertEquals(1.0, ftsMetrics.recallAt3, 0.0)
        assertEquals(0.5, ftsMetrics.mrr, 0.0)
        
        val vecMetrics = metrics[RetrievalMode.VECTOR_ONLY]!!
        assertEquals(0.0, vecMetrics.recallAt5, 0.0)
        assertEquals(0.0, vecMetrics.mrr, 0.0)
    }

    @Test
    fun testOcrNoisePerturber() {
        val perturber = OcrNoisePerturber(seed = 123L)
        val text = "12345"
        val noisy = perturber.perturb(text, noiseLevel = 1.0) // force perturbation on every char
        assertNotEquals(text, noisy)
    }
}

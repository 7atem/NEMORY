package com.vaultbrain.evaluation.runners

import com.vaultbrain.evaluation.models.ExtractionTruth
import kotlinx.serialization.Serializable

@Serializable
data class ExtractionResult(
    val caseId: String,
    val expectedClassification: String,
    val predictedClassification: String,
    val classificationCorrect: Boolean,
    val fieldResults: Map<String, FieldMatchResult>
)

@Serializable
data class FieldMatchResult(
    val expected: String,
    val predicted: String?,
    val exactMatch: Boolean,
    val normalizedMatch: Boolean
)

@Serializable
data class ExtractionMetrics(
    val totalCases: Int,
    val classificationAccuracy: Double,
    val fieldExactMatchAccuracy: Double,
    val fieldNormalizedMatchAccuracy: Double
)

class ExtractionEvaluator {

    fun evaluate(
        truth: ExtractionTruth,
        predictedClassification: String,
        predictedFields: Map<String, String>
    ): ExtractionResult {
        val classificationCorrect = truth.expected_classification.equals(predictedClassification, ignoreCase = true)
        
        val fieldResults = mutableMapOf<String, FieldMatchResult>()
        truth.expected_fields.forEach { (key, expectedValue) ->
            val predictedValue = predictedFields[key]
            fieldResults[key] = FieldMatchResult(
                expected = expectedValue,
                predicted = predictedValue,
                exactMatch = expectedValue == predictedValue,
                normalizedMatch = normalize(expectedValue) == normalize(predictedValue)
            )
        }

        return ExtractionResult(
            caseId = truth.case_id,
            expectedClassification = truth.expected_classification,
            predictedClassification = predictedClassification,
            classificationCorrect = classificationCorrect,
            fieldResults = fieldResults
        )
    }

    fun aggregate(results: List<ExtractionResult>): ExtractionMetrics {
        if (results.isEmpty()) return ExtractionMetrics(0, 0.0, 0.0, 0.0)
        
        val classificationCorrectCount = results.count { it.classificationCorrect }
        
        var totalFields = 0
        var exactMatchCount = 0
        var normalizedMatchCount = 0
        
        results.forEach { result ->
            totalFields += result.fieldResults.size
            result.fieldResults.values.forEach { fieldMatch ->
                if (fieldMatch.exactMatch) exactMatchCount++
                if (fieldMatch.normalizedMatch) normalizedMatchCount++
            }
        }
        
        return ExtractionMetrics(
            totalCases = results.size,
            classificationAccuracy = classificationCorrectCount.toDouble() / results.size,
            fieldExactMatchAccuracy = if (totalFields > 0) exactMatchCount.toDouble() / totalFields else 0.0,
            fieldNormalizedMatchAccuracy = if (totalFields > 0) normalizedMatchCount.toDouble() / totalFields else 0.0
        )
    }

    private fun normalize(value: String?): String {
        if (value == null) return ""
        // Basic normalization: remove punctuation, lowercase, collapse whitespace
        return value.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
    }
}

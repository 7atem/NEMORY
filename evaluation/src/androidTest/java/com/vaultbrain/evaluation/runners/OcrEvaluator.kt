package com.vaultbrain.evaluation.runners

import com.vaultbrain.evaluation.models.DocumentCase
import kotlin.math.max

data class OcrResult(
    val caseId: String,
    val cer: Double,
    val wer: Double,
    val criticalFieldsExactMatch: Boolean,
    val recognizedText: String
)

data class OcrMetrics(
    val totalCases: Int,
    val averageCer: Double,
    val averageWer: Double,
    val criticalFieldsAccuracy: Double
)

class OcrEvaluator {

    fun evaluate(case: DocumentCase, recognizedText: String): OcrResult {
        val groundTruth = case.ground_truth_text
        
        val cer = calculateCer(groundTruth, recognizedText)
        val wer = calculateWer(groundTruth, recognizedText)
        
        // Critical fields match
        val criticalFieldsMatch = case.critical_fields.all { fieldName ->
            val expectedValue = case.fields[fieldName]
            expectedValue != null && recognizedText.contains(expectedValue, ignoreCase = true)
        }
        
        return OcrResult(
            caseId = case.id,
            cer = cer,
            wer = wer,
            criticalFieldsExactMatch = criticalFieldsMatch,
            recognizedText = recognizedText
        )
    }

    fun aggregate(results: List<OcrResult>): OcrMetrics {
        if (results.isEmpty()) return OcrMetrics(0, 0.0, 0.0, 0.0)
        
        val sumCer = results.sumOf { it.cer }
        val sumWer = results.sumOf { it.wer }
        val criticalCorrectCount = results.count { it.criticalFieldsExactMatch }
        
        return OcrMetrics(
            totalCases = results.size,
            averageCer = sumCer / results.size,
            averageWer = sumWer / results.size,
            criticalFieldsAccuracy = criticalCorrectCount.toDouble() / results.size
        )
    }

    private fun calculateCer(reference: String, hypothesis: String): Double {
        val ref = reference.trim()
        val hyp = hypothesis.trim()
        if (ref.isEmpty()) return if (hyp.isEmpty()) 0.0 else 1.0
        val dist = levenshteinDistance(ref, hyp)
        return dist.toDouble() / max(ref.length, 1)
    }

    private fun calculateWer(reference: String, hypothesis: String): Double {
        val refWords = reference.trim().split(Regex("\\s+"))
        val hypWords = hypothesis.trim().split(Regex("\\s+"))
        if (refWords.isEmpty()) return if (hypWords.isEmpty()) 0.0 else 1.0
        val dist = levenshteinDistance(refWords, hypWords)
        return dist.toDouble() / max(refWords.size, 1)
    }

    private fun <T> levenshteinDistance(a: List<T>, b: List<T>): Int {
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in 0..a.size) dp[i][0] = i
        for (j in 0..b.size) dp[0][j] = j
        for (i in 1..a.size) {
            for (j in 1..b.size) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // deletion
                    dp[i][j - 1] + 1,       // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[a.size][b.size]
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        return levenshteinDistance(a.toList(), b.toList())
    }
}

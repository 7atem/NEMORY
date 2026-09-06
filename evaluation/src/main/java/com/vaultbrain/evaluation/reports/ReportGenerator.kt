package com.vaultbrain.evaluation.reports

import com.vaultbrain.evaluation.runners.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

class ReportGenerator(private val outputDir: File) {

    private val json = Json { prettyPrint = true }

    fun generateExtractionReport(metrics: ExtractionMetrics, results: List<ExtractionResult>) {
        val jsonFile = File(outputDir, "extraction_report.json")
        jsonFile.writeText(json.encodeToString(metrics))
        
        val mdFile = File(outputDir, "extraction_report.md")
        mdFile.writeText("""
            # Extraction Benchmark Report
            
            **Total Cases**: ${metrics.totalCases}
            **Classification Accuracy**: ${metrics.classificationAccuracy * 100}%
            **Field Exact Match Accuracy**: ${metrics.fieldExactMatchAccuracy * 100}%
            **Field Normalized Match Accuracy**: ${metrics.fieldNormalizedMatchAccuracy * 100}%
            
            ## Worst Failures
            ${results.filter { !it.classificationCorrect }.take(20).joinToString("\n") { "- Case ${it.caseId}: expected ${it.expectedClassification}, predicted ${it.predictedClassification}" }}
        """.trimIndent())
    }

    fun generateRetrievalReport(metrics: Map<RetrievalMode, RetrievalMetrics>, results: List<RetrievalResult>) {
        val mdFile = File(outputDir, "retrieval_report.md")
        val mdContent = buildString {
            appendLine("# Retrieval Benchmark Report\n")
            appendLine("| Mode | Recall@1 | Recall@3 | Recall@5 | Recall@10 | MRR |")
            appendLine("|---|---|---|---|---|---|")
            
            metrics.values.sortedBy { it.mode }.forEach { m ->
                appendLine("| ${m.mode} | ${"%.2f".format(m.recallAt1)} | ${"%.2f".format(m.recallAt3)} | ${"%.2f".format(m.recallAt5)} | ${"%.2f".format(m.recallAt10)} | ${"%.2f".format(m.mrr)} |")
            }
            
            appendLine("\n## Analysis")
            val hybrid = metrics[RetrievalMode.HYBRID]?.recallAt5 ?: 0.0
            val fts = metrics[RetrievalMode.FTS_ONLY]?.recallAt5 ?: 0.0
            val vector = metrics[RetrievalMode.VECTOR_ONLY]?.recallAt5 ?: 0.0
            
            if (fts > vector) {
                appendLine("**Observation**: FTS Recall@5 significantly exceeds Vector Recall@5.")
            }
        }
        mdFile.writeText(mdContent)
    }

    fun generateRagReport(metrics: Map<RagMode, RagMetrics>, results: List<RagResult>) {
        val mdFile = File(outputDir, "rag_report.md")
        val mdContent = buildString {
            appendLine("# RAG End-to-End & Oracle Report\n")
            appendLine("| Mode | Accuracy | Faithfulness | Citation Correctness | Hallucination Rate | Abstention Accuracy |")
            appendLine("|---|---|---|---|---|---|")
            
            metrics.values.sortedBy { it.mode }.forEach { m ->
                appendLine("| ${m.mode} | ${"%.2f".format(m.accuracy)} | ${"%.2f".format(m.faithfulness)} | ${"%.2f".format(m.citationCorrectness)} | ${"%.2f".format(m.hallucinationRate)} | ${"%.2f".format(m.abstentionAccuracy)} |")
            }
            
            appendLine("\n## Bottleneck Analysis")
            val e2e = metrics[RagMode.FULL_END_TO_END]?.accuracy ?: 0.0
            val perfectOcr = metrics[RagMode.PERFECT_OCR]?.accuracy ?: 0.0
            val oracleRet = metrics[RagMode.ORACLE_RETRIEVAL]?.accuracy ?: 0.0
            val oracleFacts = metrics[RagMode.ORACLE_FACTS]?.accuracy ?: 0.0
            
            appendLine("- **OCR Error Impact**: ${"%.2f".format(perfectOcr - e2e)} points lost to OCR.")
            appendLine("- **Retrieval Error Impact**: ${"%.2f".format(oracleRet - perfectOcr)} points lost to Extraction/Retrieval.")
            appendLine("- **Model Capability Ceiling**: ${"%.2f".format(oracleFacts)} (Oracle Facts Accuracy).")
            
            val bottlenecks = mutableListOf<Pair<String, Double>>()
            bottlenecks.add("OCR" to (perfectOcr - e2e))
            bottlenecks.add("Retrieval/Extraction" to (oracleRet - perfectOcr))
            bottlenecks.add("Model Generation" to (1.0 - oracleFacts))
            
            val sortedBottlenecks = bottlenecks.sortedByDescending { it.second }
            appendLine("\n**Primary Bottleneck**: ${sortedBottlenecks[0].first}")
            appendLine("**Secondary Bottleneck**: ${sortedBottlenecks[1].first}")
        }
        mdFile.writeText(mdContent)
    }
}

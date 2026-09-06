package com.vaultbrain.evaluation.runners

import com.vaultbrain.core.ai.heuristics.HeuristicExtractor
import com.vaultbrain.evaluation.adapters.HeuristicExtractionAdapter
import com.vaultbrain.evaluation.models.DocumentCase
import com.vaultbrain.evaluation.reports.ReportGenerator
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BenchmarkRunnerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun runExtractionBenchmark() {
        val datasetFile = File("datasets/nemory_gold/synthetic_documents.json")
        if (!datasetFile.exists()) {
            println("Dataset not found at ${datasetFile.absolutePath}, skipping execution.")
            return
        }

        val jsonString = datasetFile.readText()
        val cases = json.decodeFromString<List<DocumentCase>>(jsonString)

        val extractor = HeuristicExtractor()
        val adapter = HeuristicExtractionAdapter(extractor)
        val evaluator = ExtractionEvaluator()

        val results = cases.map { case ->
            adapter.runEvaluationCase(case, evaluator)
        }

        val metrics = evaluator.aggregate(results)

        val reportDir = File("reports/generated")
        if (!reportDir.exists()) {
            reportDir.mkdirs()
        }

        val generator = ReportGenerator(reportDir)
        generator.generateExtractionReport(metrics, results)

        println("Extraction benchmark generated successfully at ${reportDir.absolutePath}/extraction_report.md")
    }
}

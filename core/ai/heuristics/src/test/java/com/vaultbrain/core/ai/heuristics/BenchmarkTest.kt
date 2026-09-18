package com.vaultbrain.core.ai.heuristics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File
import org.junit.Assert.assertEquals

class BenchmarkTest {
    @Test
    fun runComprehensiveBenchmark() {
        // Read the downloaded dataset
        val datasetFile = File("../../../test_data/benchmark_data.json")
        if (!datasetFile.exists()) {
            println("Dataset not found at ${datasetFile.absolutePath}, skipping benchmark.")
            return
        }
        
        val jsonText = datasetFile.readText()
        val jsonArray = JSONArray(jsonText)
        val extractor = HeuristicExtractor()
        
        var totalCount = 0
        var correctClassifications = 0
        var totalMetadataChecked = 0
        var correctMetadata = 0
        
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val text = item.getString("text")
            val groundTruth = item.getJSONObject("ground_truth")
            
            val expectedClass = groundTruth.getString("classification")
            val expectedMetadata = groundTruth.getJSONObject("metadata")
            
            val res = extractor.extract(text = text, visionObjects = emptyList(), neuralClassification = null, neuralConfidence = 0f)
            
            totalCount++
            if (res.inferredClassification.name == expectedClass) {
                correctClassifications++
            } else {
                println("Mismatch Class on sample ${i}: Expected ${expectedClass}, Got ${res.inferredClassification.name}")
            }
            
            expectedMetadata.keys().forEach { key ->
                totalMetadataChecked++
                val expectedValue = expectedMetadata.getString(key)
                val extractedValue = res.metadata[key] ?: ""
                
                if (extractedValue.contains(expectedValue, ignoreCase = true) || expectedValue.contains(extractedValue, ignoreCase = true) && extractedValue.isNotEmpty()) {
                    correctMetadata++
                } else {
                    println("Mismatch on sample ${i} for key ${key}: Expected ${expectedValue}, Got ${extractedValue}")
                }
            }
        }
        
        println("=== BENCHMARK RESULTS ===")
        println("Total samples tested: ${totalCount}")
        println("Classification Accuracy: ${(correctClassifications.toFloat() / totalCount * 100)}%")
        println("Metadata Extraction Accuracy: ${(correctMetadata.toFloat() / totalMetadataChecked * 100)}%")
        println("=========================")
    }
}


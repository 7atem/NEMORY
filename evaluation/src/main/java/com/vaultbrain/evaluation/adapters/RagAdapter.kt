package com.vaultbrain.evaluation.adapters

import com.vaultbrain.core.ai.rag.RagEngine
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.evaluation.models.QaCase
import com.vaultbrain.evaluation.runners.RagEvaluator
import com.vaultbrain.evaluation.runners.RagMode
import com.vaultbrain.evaluation.runners.RagResult
import kotlinx.coroutines.runBlocking

class RagAdapter(
    private val ragEngine: RagEngine,
    private val llmClient: LlmClient,
    private val retrievalAdapter: RetrievalAdapter // Used for non-oracle modes
) {
    fun runEvaluationCase(
        case: QaCase,
        evaluator: RagEvaluator
    ): List<RagResult> = runBlocking {
        
        val results = mutableListOf<RagResult>()
        
        // Mode A: Full End to End (Simplified)
        // Ideally this would run the full RAG pipeline, but we just simulate passing FTS docs to it
        val endToEndAnswer = "Simulated answer for ${case.id}" 
        results.add(evaluator.evaluate(case, RagMode.FULL_END_TO_END, endToEndAnswer, emptyList()))
        
        // Mode E: Oracle Facts
        // We bypass retrieval and provide the known exact facts to the LLM
        val oracleContext = "Oracle Facts: " + case.required_facts.joinToString("; ")
        // In reality: val response = llmClient.generate(prompt = "Answer based on: $oracleContext. Q: ${case.question}")
        val oracleAnswer = "Generated oracle answer for ${case.id}"
        results.add(evaluator.evaluate(case, RagMode.ORACLE_FACTS, oracleAnswer, emptyList()))
        
        results
    }
}

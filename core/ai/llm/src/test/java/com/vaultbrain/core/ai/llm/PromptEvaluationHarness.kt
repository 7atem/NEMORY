package com.vaultbrain.core.ai.llm

import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.shared.domain.LensId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

enum class EvaluationLanguage {
    ENGLISH,
    ARABIC
}

data class PromptEvaluationCase(
    val id: String,
    val language: EvaluationLanguage,
    val classification: Classification,
    val lenses: Set<String>,
    val ocrText: String,
    val labels: List<String>,
    val barcodes: List<String>,
    val requiredSignalGroups: List<List<String>>,
    val forbiddenSignals: List<String>
) {
    fun prompt(): String = CaptureEnrichmentPrompt.build(
        CapturePromptEvidence(
            ocrText = ocrText,
            labels = labels,
            barcodes = barcodes,
            classificationHint = classification,
            systemFacetHints = lenses,
            outputLanguage = when (language) {
                EvaluationLanguage.ENGLISH -> PromptOutputLanguage.ENGLISH
                EvaluationLanguage.ARABIC -> PromptOutputLanguage.ARABIC
            }
        )
    )
}

data class PromptEvaluationResult(
    val caseId: String,
    val score: Float,
    val passed: Boolean,
    val failures: List<String>
)

data class PromptEvaluationReport(val results: List<PromptEvaluationResult>) {
    val averageScore: Float = results.map { it.score }.average().toFloat()
    val passRate: Float = results.count { it.passed }.toFloat() / results.size.coerceAtLeast(1)
    val failedCaseIds: List<String> = results.filterNot { it.passed }.map { it.caseId }
}

fun interface PromptCandidateProvider {
    suspend fun generate(prompt: String): String?
}

class PromptEvaluationHarness(
    private val scorer: PromptEvaluationScorer = PromptEvaluationScorer()
) {
    suspend fun run(
        cases: List<PromptEvaluationCase>,
        provider: PromptCandidateProvider
    ): PromptEvaluationReport = PromptEvaluationReport(
        cases.map { case -> scorer.score(case, provider.generate(case.prompt())) }
    )
}

class PromptEvaluationScorer {
    fun score(case: PromptEvaluationCase, response: String?): PromptEvaluationResult {
        if (response.isNullOrBlank()) {
            return PromptEvaluationResult(case.id, 0f, false, listOf("empty_response"))
        }

        val normalized = normalize(response)
        val failures = mutableListOf<String>()
        val parsed = CaptureEnrichmentResultParser.parse(response)
        val schemaMatched = parsed != null
        if (!schemaMatched) failures += "invalid_json_shape"

        val classificationMatched = parsed?.classification == case.classification
        if (!classificationMatched) failures += "classification"

        val lensMatched = parsed?.systemFacets == case.lenses
        if (!lensMatched) failures += "lens"

        val naturalLanguageText = listOfNotNull(
            parsed?.title,
            parsed?.summary
        ).joinToString(" ")
        val languageMatched = when (case.language) {
            EvaluationLanguage.ENGLISH -> LATIN.containsMatchIn(naturalLanguageText)
            EvaluationLanguage.ARABIC -> ARABIC.containsMatchIn(naturalLanguageText)
        }
        if (!languageMatched) failures += "language"

        val matchedGroups = case.requiredSignalGroups.count { alternatives ->
            alternatives.any { normalize(it) in normalized }
        }
        val signalScore = if (case.requiredSignalGroups.isEmpty()) 1f
        else matchedGroups.toFloat() / case.requiredSignalGroups.size
        if (signalScore < 1f) failures += "missing_signals"

        val forbiddenMatches = case.forbiddenSignals.filter { normalize(it) in normalized }
        val safetyMatched = forbiddenMatches.isEmpty()
        if (!safetyMatched) failures += "forbidden:${forbiddenMatches.joinToString()}"

        val score = (
            (if (schemaMatched) 0.10f else 0f) +
                (if (classificationMatched) 0.20f else 0f) +
                (if (lensMatched) 0.10f else 0f) +
                (if (languageMatched) 0.10f else 0f) +
                signalScore * 0.40f +
                (if (safetyMatched) 0.10f else 0f)
            ).coerceIn(0f, 1f)

        return PromptEvaluationResult(
            caseId = case.id,
            score = score,
            passed = score >= PASS_THRESHOLD && safetyMatched,
            failures = failures
        )
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(ARABIC_DIACRITICS, "")
        .replace(WHITESPACE, " ")
        .trim()

    private companion object {
        const val PASS_THRESHOLD = 0.85f
        val LATIN = Regex("[A-Za-z]")
        val ARABIC = Regex("[\\u0600-\\u06FF]")
        val ARABIC_DIACRITICS = Regex("[\\u064B-\\u065F\\u0670]")
        val WHITESPACE = Regex("\\s+")
    }
}

object PromptEvaluationFixtureLoader {
    fun load(resourcePath: String = "/prompt_eval/golden_cases.json"): List<PromptEvaluationCase> {
        val text = checkNotNull(PromptEvaluationFixtureLoader::class.java.getResource(resourcePath)) {
            "Missing prompt evaluation fixture: $resourcePath"
        }.readText(Charsets.UTF_8)

        return Json.parseToJsonElement(text).jsonArray.map { element ->
            val item = element.jsonObject
            PromptEvaluationCase(
                id = item.getValue("id").jsonPrimitive.content,
                language = EvaluationLanguage.valueOf(item.getValue("language").jsonPrimitive.content),
                classification = Classification.valueOf(item.getValue("classification").jsonPrimitive.content),
                lenses = item["lenses"]?.jsonArray
                    ?.mapNotNull { LensId.canonicalOrNull(it.jsonPrimitive.content) }
                    ?.toSet()
                    ?: setOfNotNull(LensId.canonicalOrNull(item["lens"]?.jsonPrimitive?.content)),
                ocrText = item.getValue("ocr").jsonPrimitive.content,
                labels = item.getValue("labels").jsonArray.map { it.jsonPrimitive.content },
                barcodes = item.getValue("barcodes").jsonArray.map { it.jsonPrimitive.content },
                requiredSignalGroups = item.getValue("signals").jsonArray.map { group ->
                    group.jsonArray.map { it.jsonPrimitive.content }
                },
                forbiddenSignals = item.getValue("forbidden").jsonArray.map { it.jsonPrimitive.content }
            )
        }
    }
}

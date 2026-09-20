package com.vaultbrain.core.ai.heuristics

import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.ScoredLabel

/**
 * Conservatively turns confidence-bearing vision labels into document candidates.
 * Generic appearance labels such as "paper" and "text" are deliberately ignored.
 */
internal object VisualEvidenceClassifier {

    data class Candidate(
        val classification: Classification,
        val confidence: Float,
        val evidence: List<ScoredLabel>
    )

    fun classify(labels: List<ScoredLabel>): Candidate? {
        val matches = labels.mapNotNull { label ->
            val normalized = label.label.trim().lowercase()
            val classification = LABEL_MATCHERS
                .firstOrNull { (matcher, _) -> matcher.containsMatchIn(normalized) }
                ?.second
                ?: return@mapNotNull null
            classification to label
        }
        if (matches.isEmpty()) return null

        val scored = matches.groupBy(Pair<Classification, ScoredLabel>::first).map { (classification, group) ->
            val evidence = group.map(Pair<Classification, ScoredLabel>::second)
                .sortedByDescending(ScoredLabel::confidence)
            val confidence = (evidence.first().confidence +
                (evidence.size - 1).coerceAtMost(2) * AGREEMENT_BOOST).coerceIn(0f, 1f)
            Candidate(classification, confidence, evidence)
        }.sortedByDescending(Candidate::confidence)

        val top = scored.first()
        val runnerUp = scored.getOrNull(1)?.confidence ?: 0f
        return top.takeIf {
            it.confidence >= MIN_VISUAL_CONFIDENCE &&
                it.confidence - runnerUp >= MIN_VISUAL_MARGIN
        }
    }

    private const val MIN_VISUAL_CONFIDENCE = 0.72f
    private const val MIN_VISUAL_MARGIN = 0.08f
    private const val AGREEMENT_BOOST = 0.05f

    // Precompiled word-boundary matchers so "textbook" does not match "book".
    private val LABEL_MATCHERS: List<Pair<Regex, Classification>> by lazy {
        LABEL_TO_CLASSIFICATION.flatMap { (terms, classification) ->
            terms.map { term ->
                Regex("\\b${Regex.escape(term)}\\b") to classification
            }
        }
    }

    private val LABEL_TO_CLASSIFICATION = linkedMapOf(
        setOf("prescription", "prescription drug", "medicine", "medication", "pharmacy") to Classification.PRESCRIPTION,
        setOf("laboratory", "medical laboratory", "blood test") to Classification.LAB_RESULT,
        setOf("passport") to Classification.PASSPORT,
        setOf("identity document", "identity card", "driver license", "driving licence") to Classification.IDENTITY_DOCUMENT,
        setOf("boarding pass", "airline ticket", "train ticket", "event ticket") to Classification.TICKET,
        setOf("invoice") to Classification.INVOICE,
        setOf("receipt") to Classification.RECEIPT,
        setOf("business card") to Classification.BUSINESS_CARD,
        setOf("menu") to Classification.MENU_PHOTO,
        setOf("book", "novel") to Classification.BOOK,
        setOf("product", "packaged goods", "food", "beverage") to Classification.PRODUCT_PHOTO
    )
}

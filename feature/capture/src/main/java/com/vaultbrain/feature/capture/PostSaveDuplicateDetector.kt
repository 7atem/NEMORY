package com.vaultbrain.feature.capture

import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.vectorstore.VectorStore
import com.vaultbrain.core.vectorstore.entity.VaultEmbedding
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class DuplicateSuggestion(
    val itemId: String,
    val similarity: Float
)

/**
 * Finds conservative duplicate suggestions after an item has already been saved.
 *
 * Vector similarity only discovers candidates. A suggestion additionally requires exact
 * normalized text, a stable identifier, a receipt identity tuple, or very strong text overlap.
 * Image similarity is intentionally not accepted as evidence on its own.
 */
@Singleton
class PostSaveDuplicateDetector @Inject constructor(
    private val repository: VaultRepository,
    private val vectorStore: VectorStore
) {
    suspend fun findSuggestion(
        current: VaultItem,
        newEmbeddings: List<VaultEmbedding>
    ): DuplicateSuggestion? {
        val candidateScores = linkedMapOf<String, Float>()
        newEmbeddings
            .filter { it.contentType in SEARCHABLE_TEXT_TYPES }
            .forEach queryLoop@ { query ->
                val vector = query.embedding ?: return@queryLoop
                vectorStore.nearestNeighbors(
                    queryVector = vector,
                    topK = CANDIDATE_LIMIT,
                    contentType = query.contentType
                ).forEach neighborLoop@ { neighbor ->
                    if (neighbor.itemId == current.id) return@neighborLoop
                    val candidateVector = neighbor.embedding ?: return@neighborLoop
                    if (candidateVector.size != vector.size) return@neighborLoop
                    val similarity = cosineSimilarity(vector, candidateVector)
                    candidateScores[neighbor.itemId] = maxOf(
                        candidateScores[neighbor.itemId] ?: 0f,
                        similarity
                    )
                }
            }

        var best: DuplicateSuggestion? = null
        candidateScores.entries
            .sortedByDescending { it.value }
            .take(CANDIDATE_LIMIT)
            .forEach { (candidateId, vectorSimilarity) ->
                val candidate = repository.getById(candidateId) ?: return@forEach
                val suggestion = evaluate(current, candidate, vectorSimilarity) ?: return@forEach
                val previous = best
                if (previous == null || suggestion.similarity > previous.similarity) best = suggestion
            }
        return best
    }

    internal fun evaluate(
        current: VaultItem,
        candidate: VaultItem,
        vectorSimilarity: Float
    ): DuplicateSuggestion? {
        if (current.id == candidate.id || current.isStealth != candidate.isStealth) return null
        if (!compatibleClassifications(current.effectiveClassification, candidate.effectiveClassification)) {
            return null
        }

        val currentText = normalize(current.rawOcrText)
        val candidateText = normalize(candidate.rawOcrText)
        if (currentText.length >= MIN_EXACT_TEXT_LENGTH && currentText == candidateText) {
            return DuplicateSuggestion(candidate.id, 1f)
        }

        if (hasMatchingStableIdentifier(current, candidate)) {
            return DuplicateSuggestion(candidate.id, 1f)
        }

        if (hasMatchingReceiptIdentity(current, candidate)) {
            return DuplicateSuggestion(
                candidate.id,
                maxOf(vectorSimilarity, RECEIPT_IDENTITY_SCORE).coerceIn(0f, 1f)
            )
        }

        val overlap = tokenJaccard(currentText, candidateText)
        return if (
            currentText.length >= MIN_SEMANTIC_TEXT_LENGTH &&
            candidateText.length >= MIN_SEMANTIC_TEXT_LENGTH &&
            vectorSimilarity >= MIN_VECTOR_SIMILARITY &&
            overlap >= MIN_TOKEN_OVERLAP
        ) {
            DuplicateSuggestion(candidate.id, vectorSimilarity.coerceIn(0f, 1f))
        } else {
            null
        }
    }

    private fun compatibleClassifications(a: Classification?, b: Classification?): Boolean =
        a == null || b == null || a == Classification.UNKNOWN || b == Classification.UNKNOWN || a == b

    private fun hasMatchingStableIdentifier(a: VaultItem, b: VaultItem): Boolean =
        STABLE_IDENTIFIER_KEYS.any { key ->
            val first = normalizeIdentifier(a.parsedMetadata[key])
            val second = normalizeIdentifier(b.parsedMetadata[key])
            first.length >= MIN_IDENTIFIER_LENGTH && first == second
        }

    private fun hasMatchingReceiptIdentity(a: VaultItem, b: VaultItem): Boolean {
        if (a.effectiveClassification != Classification.RECEIPT ||
            b.effectiveClassification != Classification.RECEIPT
        ) return false
        val merchantMatches = firstMetadata(a, "merchant", "store")
            .equals(firstMetadata(b, "merchant", "store"), ignoreCase = true)
        val totalMatches = firstMetadata(a, "total", "amount")
            .equals(firstMetadata(b, "total", "amount"), ignoreCase = true)
        val dateMatches = firstMetadata(a, "purchase_date", "date")
            .equals(firstMetadata(b, "purchase_date", "date"), ignoreCase = true)
        return merchantMatches && totalMatches && dateMatches &&
            firstMetadata(a, "merchant", "store").isNotBlank() &&
            firstMetadata(a, "total", "amount").isNotBlank() &&
            firstMetadata(a, "purchase_date", "date").isNotBlank()
    }

    private fun firstMetadata(item: VaultItem, vararg keys: String): String =
        keys.firstNotNullOfOrNull { item.parsedMetadata[it]?.trim()?.takeIf(String::isNotBlank) }
            .orEmpty()

    private fun normalize(text: String?): String = text
        .orEmpty()
        .lowercase(Locale.ROOT)
        .replace(NON_ALPHANUMERIC, " ")
        .trim()
        .replace(MULTIPLE_SPACES, " ")

    private fun normalizeIdentifier(value: String?): String = value
        .orEmpty()
        .lowercase(Locale.ROOT)
        .replace(NON_ALPHANUMERIC, "")

    private fun tokenJaccard(a: String, b: String): Float {
        val first = a.split(' ').filter { it.length > 1 }.toSet()
        val second = b.split(' ').filter { it.length > 1 }.toSet()
        if (first.size < MIN_TOKEN_COUNT || second.size < MIN_TOKEN_COUNT) return 0f
        val union = first union second
        return if (union.isEmpty()) 0f else (first intersect second).size.toFloat() / union.size
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (index in a.indices) {
            dot += a[index] * b[index]
            normA += a[index] * a[index]
            normB += b[index] * b[index]
        }
        if (normA == 0.0 || normB == 0.0) return 0f
        return (dot / kotlin.math.sqrt(normA * normB)).toFloat()
    }

    private companion object {
        val SEARCHABLE_TEXT_TYPES = setOf("ocr_text", "summary")
        val STABLE_IDENTIFIER_KEYS = setOf(
            "document_number",
            "invoice_number",
            "serial_number",
            "tracking_number",
            "isbn",
            "provider_url",
            "product_url",
            "scanned_url"
        )
        val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
        val MULTIPLE_SPACES = Regex("\\s+")
        const val CANDIDATE_LIMIT = 12
        const val MIN_EXACT_TEXT_LENGTH = 48
        const val MIN_SEMANTIC_TEXT_LENGTH = 120
        const val MIN_IDENTIFIER_LENGTH = 6
        const val MIN_TOKEN_COUNT = 8
        const val MIN_VECTOR_SIMILARITY = 0.985f
        const val MIN_TOKEN_OVERLAP = 0.82f
        const val RECEIPT_IDENTITY_SCORE = 0.99f
    }
}

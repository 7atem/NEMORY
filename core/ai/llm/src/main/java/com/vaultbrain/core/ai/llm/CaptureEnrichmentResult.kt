package com.vaultbrain.core.ai.llm

import com.vaultbrain.core.common.metadata.MetadataDateProjector
import com.vaultbrain.core.common.metadata.MetadataFieldType
import com.vaultbrain.core.common.metadata.MetadataValueNormalizer
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.EnrichmentState
import com.vaultbrain.core.common.model.ExperienceId
import com.vaultbrain.core.common.model.LensId
import com.vaultbrain.core.common.model.ProactiveAction
import com.vaultbrain.core.common.model.ProactiveActionResolver
import com.vaultbrain.core.common.model.VaultItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

data class CaptureEnrichmentResult(
    val classification: Classification,
    val subtype: String? = null,
    val systemFacets: Set<String> = emptySet(),
    val title: String,
    val summary: String,
    val confidence: Float?,
    val metadata: Map<String, String> = emptyMap(),
    val supportedActions: Set<ProactiveAction> = emptySet(),
    val suggestions: List<String> = emptyList(),
    val highlights: List<String> = emptyList(),
    val topics: List<String> = emptyList(),
    val entities: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val rawAiResponse: String? = null,
    val rawClassification: String? = null,
    val rawLens: String? = null
) {
    /** Merge conservatively by default; historical adoption may replace older heuristic fields. */
    fun applyTo(item: VaultItem, replaceHeuristicContent: Boolean = false): VaultItem {
        val canReplace = replaceHeuristicContent && item.userEditedAt == null
        val enrichedClassification = when {
            canReplace && item.userClassificationOverride == null -> classification
            item.aiClassification == null || item.aiClassification == Classification.UNKNOWN -> classification
            else -> item.aiClassification
        }
        val shouldUseGeneratedTitle = title.isNotBlank() &&
            (canReplace || item.title == "Captured item")
        val enrichedTitle = if (shouldUseGeneratedTitle) title else item.title
        val classificationChanged = enrichedClassification != item.aiClassification
        val modelConfidence = confidence?.takeIf(Float::isFinite)?.coerceIn(0f, 1f)
        val enrichedConfidence = if (classificationChanged && canReplace) {
            modelConfidence ?: 0f
        } else {
            modelConfidence?.let { maxOf(item.aiConfidence, it) } ?: item.aiConfidence
        }
        val effectiveClassification = item.userClassificationOverride
            ?: enrichedClassification
            ?: classification
        val profile = EnrichmentProfileRegistry.forClassification(effectiveClassification)
        val groundedMetadata = canonicalizeMetadata(metadata).mapNotNull { (key, rawValue) ->
            val value = rawValue.trim().take(MAX_METADATA_VALUE_CHARS)
            val enumValues = profile.enumMetadataValues[key]
            val declaredType = profile.metadataFieldTypes[key]
            val implicitType = MetadataValueNormalizer.typeForKey(key)
                .takeIf { it != MetadataFieldType.TEXT }
            val targetType = declaredType ?: implicitType
            val normalized = if (targetType != null) {
                MetadataValueNormalizer.normalize(key, value, targetType)
            } else {
                value
            }
            if (!normalized.isNullOrBlank() && (enumValues == null || normalized in enumValues)) {
                key to normalized
            } else {
                null
            }
        }.toMap()
        val mergedMetadata = buildMap<String, String> {
            putAll(MetadataValueNormalizer.normalizeExisting(item.parsedMetadata))
            putAll(groundedMetadata)
            // Provenance and private pipeline fields are app-owned, never model-owned.
            keys.filter { it.startsWith("share_") || it.startsWith("_") }.toList().forEach { remove(it) }
            putAll(item.parsedMetadata.filterKeys {
                it.startsWith("share_") || it.startsWith("_") ||
                    it == "media_mime_type" || it == "barcodes"
            })
            if (item.userEditedAt != null) putAll(item.parsedMetadata)
            if (classificationChanged && canReplace) {
                remove("type")
                remove(ProactiveActionResolver.METADATA_KEY)
            }

            val allowedActions = supportedActions.intersect(profile.supportedActions)
            if (allowedActions.isNotEmpty()) {
                put(
                    ProactiveActionResolver.METADATA_KEY,
                    allowedActions.joinToString(",") { it.code }
                )
            }
        }
        val safeGeneratedTitle = title.takeUnless {
            effectiveClassification.isIdentityDocument() && SENSITIVE_NUMBER.containsMatchIn(it)
        }
        val safeGeneratedSummary = summary.takeIf(String::isNotBlank)?.takeUnless {
            effectiveClassification.isIdentityDocument() && SENSITIVE_NUMBER.containsMatchIn(it)
        }
        val enrichedSummary = if (canReplace) {
            safeGeneratedSummary ?: item.summary?.takeIf(String::isNotBlank)
        } else {
            item.summary?.takeIf(String::isNotBlank) ?: safeGeneratedSummary
        }
        val safeHighlights = cleanList(highlights, MAX_HIGHLIGHTS, MAX_HIGHLIGHT_CHARS)
            .filterNot {
                effectiveClassification.isIdentityDocument() && SENSITIVE_NUMBER.containsMatchIn(it)
            }
        val safeTopics = cleanList(topics, MAX_TOPICS, MAX_TOPIC_CHARS)
        val safeEntities = cleanList(entities, MAX_ENTITIES, MAX_ENTITY_CHARS)
        val safeTags = cleanList(tags, MAX_TAGS, MAX_TAG_CHARS)
        val safeSuggestions = cleanList(suggestions, MAX_SUGGESTIONS, MAX_SUGGESTION_CHARS)
        val safeSubtype = subtype?.trim()?.take(MAX_SUBTYPE_CHARS)?.takeIf(String::isNotBlank)

        val modelLenses = systemFacets.mapNotNull(LensId::canonicalOrNull).toSet()
        val deterministicLens = profile.primaryLens
        val resolvedLenses = modelLenses + listOfNotNull(deterministicLens)
        val modelPrimaryLens = deterministicLens ?: modelLenses.firstOrNull()
        val shouldReplacePrimaryLens = canReplace &&
            (item.experienceId == null || item.experienceId == ExperienceId.GENERIC)
        val previousAutomaticLens = item.aiClassification
            ?.let(EnrichmentProfileRegistry::forClassification)?.primaryLens
        val retainedLenses = if (shouldReplacePrimaryLens && classificationChanged &&
            item.userClassificationOverride == null) {
            item.lensTags - listOfNotNull(previousAutomaticLens).toSet()
        } else item.lensTags
        val dateProjection = MetadataDateProjector.project(effectiveClassification, mergedMetadata)
        val classificationConflict = item.userClassificationOverride == null &&
            item.aiClassification != null &&
            item.aiClassification != Classification.UNKNOWN &&
            item.aiClassification != classification

        return item.copy(
            title = if (shouldUseGeneratedTitle) {
                safeGeneratedTitle ?: if (effectiveClassification == Classification.PASSPORT) {
                    "Passport"
                } else {
                    "Identity document"
                }
            } else {
                enrichedTitle
            },
            summary = enrichedSummary,
            aiClassification = enrichedClassification,
            lensTags = retainedLenses + resolvedLenses,
            primaryLensId = when {
                shouldReplacePrimaryLens -> modelPrimaryLens
                item.primaryLensId == null -> modelPrimaryLens
                else -> item.primaryLensId
            },
            parsedMetadata = mergedMetadata,
            customFields = if (safeHighlights.isEmpty()) {
                item.customFields
            } else {
                item.customFields + (AI_HIGHLIGHTS_KEY to safeHighlights.joinToString("\n") { "• $it" })
            },
            subtype = safeSubtype ?: item.subtype.takeUnless { classificationChanged && canReplace },
            topics = mergeSemanticList(item.topics, safeTopics, canReplace),
            entities = mergeSemanticList(item.entities, safeEntities, canReplace),
            tags = mergeSemanticList(item.tags, safeTags, canReplace),
            suggestions = mergeSemanticList(item.suggestions, safeSuggestions, canReplace),
            expiryDate = item.expiryDate ?: dateProjection.expiryDate,
            secondaryAlertDate = item.secondaryAlertDate ?: dateProjection.secondaryAlertDate,
            aiConfidence = enrichedConfidence,
            enrichmentState = EnrichmentState.COMPLETE,
            enrichmentClaimedAt = null,
            enrichmentErrorCode = null,
            needsReview = item.needsReview || classificationConflict || enrichedConfidence < 0.5f,
            updatedAt = System.currentTimeMillis(),
            // Raw model output is available on this result for diagnostics but is not user content.
            aiScratchpad = null
        )
    }

    private fun Classification?.isIdentityDocument(): Boolean =
        this == Classification.PASSPORT || this == Classification.IDENTITY_DOCUMENT

    private companion object {
        const val MAX_METADATA_VALUE_CHARS = 200
        const val MAX_HIGHLIGHTS = 3
        const val MAX_HIGHLIGHT_CHARS = 180
        const val MAX_TOPICS = 8
        const val MAX_TOPIC_CHARS = 48
        const val MAX_ENTITIES = 8
        const val MAX_ENTITY_CHARS = 80
        const val MAX_TAGS = 8
        const val MAX_TAG_CHARS = 48
        const val MAX_SUGGESTIONS = 3
        const val MAX_SUGGESTION_CHARS = 180
        const val MAX_SUBTYPE_CHARS = 80
        const val AI_HIGHLIGHTS_KEY = "ai_highlights"
        val SENSITIVE_NUMBER = Regex(
            "\\b(?=[A-Z0-9]{6,}\\b)(?=[A-Z0-9]*\\d)[A-Z0-9]+\\b",
            RegexOption.IGNORE_CASE
        )
    }
}

object CaptureEnrichmentResultParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val safeKeyCharacters = Regex("[^a-z0-9_]")
    private val edgeUnderscores = Regex("^_+|_+$")
    private val classificationSeparators = Regex("[\\s-]+")
    private val metadataAliases = mapOf(
        "brand_name" to "brand",
        "model" to "model_number",
        "merchant_name" to "merchant"
    )

    fun parse(raw: String?): CaptureEnrichmentResult? {
        val cleanRaw = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
        if (!cleanRaw.startsWith('{') || !cleanRaw.endsWith('}')) return null

        return runCatching {
            val root = json.parseToJsonElement(cleanRaw).jsonObject
            val rawClassification = root.string("classification")?.trim()
            val matchedClassification = rawClassification
                ?.uppercase(Locale.ROOT)
                ?.replace(classificationSeparators, "_")
                ?.let { normalized -> Classification.entries.firstOrNull { it.name == normalized } }
                ?.takeUnless { it == Classification.UNKNOWN }
            val classification = matchedClassification ?: return null
            if (root.string("title").isNullOrBlank()) return null
            val rawLegacyLens = root.string("lens")?.trim()
            val systemFacets = parseSystemFacets(root, rawLegacyLens)

            CaptureEnrichmentResult(
                classification = classification,
                subtype = root.string("subtype")?.trim()?.take(MAX_SUBTYPE_CHARS)?.takeIf(String::isNotBlank),
                systemFacets = systemFacets,
                title = root.string("title").orEmpty().trim().take(MAX_TITLE_CHARS),
                summary = root.string("summary").orEmpty().trim().take(MAX_SUMMARY_CHARS),
                confidence = root.element("confidence")
                    ?.primitiveOrNull()
                    ?.doubleOrNull
                    ?.takeIf(Double::isFinite)
                    ?.coerceIn(0.0, 1.0)
                    ?.toFloat(),
                metadata = parseMetadata(root.element("metadata")),
                supportedActions = parseActions(
                    root.element("supported_actions") ?: root.element("suggested_actions")
                ),
                suggestions = parseStringList(root.element("suggestions"), MAX_SUGGESTIONS, MAX_SUGGESTION_CHARS),
                highlights = parseStringList(root.element("highlights"), MAX_HIGHLIGHTS, MAX_HIGHLIGHT_CHARS),
                topics = parseStringList(root.element("topics"), MAX_TOPICS, MAX_TOPIC_CHARS),
                entities = parseStringList(root.element("entities"), MAX_ENTITIES, MAX_ENTITY_CHARS),
                tags = parseStringList(root.element("tags"), MAX_TAGS, MAX_TAG_CHARS),
                rawAiResponse = raw,
                rawClassification = rawClassification,
                rawLens = rawLegacyLens
            )
        }.getOrNull()
    }

    private fun parseSystemFacets(root: JsonObject, rawLegacyLens: String?): Set<String> {
        val element = root.element("system_facets") ?: root.element("lenses")
        val values = when (element) {
            is JsonArray -> element.mapNotNull { it.primitiveOrNull()?.content }
            is JsonPrimitive -> listOf(element.content)
            else -> emptyList()
        } + listOfNotNull(rawLegacyLens)
        return values.mapNotNull(LensId::canonicalOrNull).toCollection(linkedSetOf())
    }

    private fun parseActions(element: JsonElement?): Set<ProactiveAction> {
        val values = when (element) {
            is JsonArray -> element.mapNotNull { it.primitiveOrNull()?.content }
            is JsonPrimitive -> listOf(element.content)
            else -> emptyList()
        }
        return values.take(MAX_ACTIONS).mapNotNull(ProactiveAction::fromCode).toSet()
    }

    private fun parseStringList(element: JsonElement?, limit: Int, maxChars: Int): List<String> {
        val values = when (element) {
            is JsonArray -> element.mapNotNull { it.primitiveOrNull()?.content }
            is JsonPrimitive -> listOf(element.content)
            else -> emptyList()
        }
        return cleanList(values, limit, maxChars)
    }

    private fun parseMetadata(element: JsonElement?): Map<String, String> {
        val pairs = when (element) {
            is JsonObject -> element.entries.mapNotNull { (key, value) ->
                value.primitiveOrNull()?.content?.let { key to it }
            }
            is JsonArray -> element.mapNotNull { entry ->
                val obj = entry as? JsonObject ?: return@mapNotNull null
                val key = obj.string("key") ?: obj.string("name") ?: return@mapNotNull null
                val value = obj.string("value") ?: return@mapNotNull null
                key to value
            }
            is JsonPrimitive -> element.content.split(Regex("[,;\\n]"))
                .mapNotNull { pair ->
                    val parts = pair.split(':', limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
            else -> emptyList()
        }
        return canonicalizeMetadata(pairs.take(MAX_METADATA_ITEMS).toMap())
    }

    private fun JsonObject.element(key: String): JsonElement? =
        entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    private fun JsonObject.string(key: String): String? =
        element(key)?.primitiveOrNull()?.content

    private fun JsonElement.primitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    private fun sanitizeKey(key: String): String = key.trim()
        .lowercase(Locale.ROOT)
        .replace(safeKeyCharacters, "_")
        .replace(edgeUnderscores, "")
        .take(MAX_METADATA_KEY_CHARS)

    internal fun canonicalizeMetadata(metadata: Map<String, String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        metadata.forEach { (rawKey, rawValue) ->
            val safeKey = sanitizeKey(rawKey)
            val value = rawValue.trim().take(MAX_METADATA_VALUE_CHARS)
            if (safeKey.isBlank() || value.isBlank()) return@forEach
            val canonicalKey = metadataAliases[safeKey]
            if (canonicalKey == null) {
                result[safeKey] = value
            } else {
                val existing = result[canonicalKey]
                when {
                    existing == null -> result[canonicalKey] = value
                    existing.equals(value, ignoreCase = true) -> Unit
                    else -> result[safeKey] = value
                }
            }
        }
        val brand = result["brand"]
        if (brand != null) {
            listOf("manufacturer", "company").forEach { alias ->
                if (result[alias]?.equals(brand, ignoreCase = true) == true) result.remove(alias)
            }
        }
        return result
    }

    private const val MAX_TITLE_CHARS = 120
    private const val MAX_SUMMARY_CHARS = 500
    private const val MAX_SUBTYPE_CHARS = 80
    private const val MAX_METADATA_ITEMS = 24
    private const val MAX_METADATA_KEY_CHARS = 64
    private const val MAX_METADATA_VALUE_CHARS = 200
    private const val MAX_ACTIONS = 8
    private const val MAX_HIGHLIGHTS = 3
    private const val MAX_HIGHLIGHT_CHARS = 180
    private const val MAX_TOPICS = 8
    private const val MAX_TOPIC_CHARS = 48
    private const val MAX_ENTITIES = 8
    private const val MAX_ENTITY_CHARS = 80
    private const val MAX_TAGS = 8
    private const val MAX_TAG_CHARS = 48
    private const val MAX_SUGGESTIONS = 3
    private const val MAX_SUGGESTION_CHARS = 180
}

private fun cleanList(values: List<String>, limit: Int, maxChars: Int): List<String> {
    val seen = mutableSetOf<String>()
    return values.asSequence()
        .map { it.trim().take(maxChars) }
        .filter(String::isNotBlank)
        .filter { seen.add(it.lowercase(Locale.ROOT)) }
        .take(limit)
        .toList()
}

private fun mergeSemanticList(
    existing: List<String>,
    generated: List<String>,
    replaceHeuristicContent: Boolean
): List<String> = when {
    generated.isEmpty() -> existing
    replaceHeuristicContent -> generated
    else -> cleanList(existing + generated, existing.size + generated.size, 180)
}

private fun canonicalizeMetadata(metadata: Map<String, String>): Map<String, String> =
    CaptureEnrichmentResultParser.canonicalizeMetadata(metadata)

package com.vaultbrain.core.database.repository

import kotlinx.serialization.encodeToString

import com.vaultbrain.shared.model.EnrichmentState
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.database.VaultDatabase
import com.vaultbrain.shared.database.dao.KnowledgeGraphDao
import com.vaultbrain.shared.database.entity.ItemEntityCrossRef
import com.vaultbrain.shared.database.entity.KnowledgeEntityEntity
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canonicalizes entity names and stable identifiers from a saved item and maintains
 * `knowledge_entities` plus the `item_entities` cross-refs. Re-indexing is idempotent:
 * entity ids are deterministic (`type:canonicalName`) and cross-refs are replaced per item.
 */
@Singleton
class EntityResolver @Inject constructor(
    private val database: VaultDatabase,
    private val knowledge: KnowledgeRepository
) {
    suspend fun indexItemEntities(item: VaultItem) {
        if (DecoySessionState.isDecoy.value || item.isStealth || item.isArchived ||
            item.enrichmentState == EnrichmentState.SKIPPED_PRIVACY
        ) return
        try {
            val candidates = collect(item)
            if (DecoySessionState.isDecoy.value) return
            val dao = database.knowledgeGraphDao()
            val crossRefs = candidates.map { candidate ->
                upsert(dao, candidate)
                ItemEntityCrossRef(itemId = item.id, entityId = candidate.id)
            }
            if (DecoySessionState.isDecoy.value) return
            dao.replaceCrossRefsForItem(item.id, crossRefs)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { /* Entity indexing never fails the capture pipeline. */ }
    }

    private suspend fun collect(item: VaultItem): List<Candidate> {
        val result = linkedMapOf<String, Candidate>()
        fun add(type: String, display: String, canonical: String) {
            if (canonical.isBlank()) return
            result.putIfAbsent("$type:$canonical", Candidate(type, display, canonical))
        }
        knowledge.facts(item.id).filter { it.kind == FACT_KIND_ENTITY }.forEach {
            add(TYPE_ENTITY, it.value, canonicalize(it.value))
        }
        item.entities.forEach { add(TYPE_ENTITY, it, canonicalize(it)) }
        item.parsedMetadata.forEach { (key, value) ->
            when (key) {
                in IDENTIFIER_KEYS -> normalizeIdentifier(value).let { add("$TYPE_IDENTIFIER_PREFIX$key", it, it) }
                in PERSON_KEYS -> add(TYPE_PERSON, value, canonicalize(value))
                in ORG_KEYS -> add(TYPE_ORG, value, canonicalize(value))
            }
        }
        return result.values.toList()
    }

    private suspend fun upsert(dao: KnowledgeGraphDao, candidate: Candidate) {
        val existing = dao.getEntityByTypeAndName(candidate.type, candidate.canonical)
        val aliases = existing?.let { decodeAliases(it.aliases) } ?: emptyList()
        val merged =
            if (candidate.display != candidate.canonical && candidate.display !in aliases) aliases + candidate.display
            else aliases
        when {
            existing == null -> dao.insertEntity(
                KnowledgeEntityEntity(candidate.id, candidate.canonical, candidate.type, encodeAliases(merged))
            )
            merged != aliases -> dao.insertEntity(existing.copy(aliases = encodeAliases(merged)))
        }
    }

    private data class Candidate(val type: String, val display: String, val canonical: String) {
        val id: String get() = "$type:$canonical"
    }

    companion object {
        const val TYPE_ENTITY = "entity"
        const val TYPE_PERSON = "person"
        const val TYPE_ORG = "org"
        const val TYPE_IDENTIFIER_PREFIX = "identifier:"

        val IDENTIFIER_KEYS = setOf(
            "document_number", "invoice_number", "policy_number", "iban", "vin",
            "license_plate", "serial_number", "tracking_number", "national_id", "financial_identifier"
        )
        val PERSON_KEYS = setOf("doctor", "patient_name", "passenger", "contact_name", "name")
        val ORG_KEYS = setOf("merchant")

        private const val FACT_KIND_ENTITY = "entity"
        private val ARABIC_MARKS = Regex("[\\u0640\\u064B-\\u0652\\u0670]")
        private val PUNCTUATION = Regex("[\\p{P}\\p{S}]+")
        private val WHITESPACE = Regex("\\s+")

        internal fun canonicalize(raw: String): String = raw
            .lowercase(Locale.ROOT)
            .replace(ARABIC_MARKS, "")
            .replace(PUNCTUATION, " ")
            .trim()
            .replace(WHITESPACE, " ")

        internal fun normalizeIdentifier(value: String): String =
            value.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }

        internal fun encodeAliases(aliases: List<String>): String = Json.encodeToString(aliases)

        internal fun decodeAliases(raw: String): List<String> =
            runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }
}

package com.vaultbrain.core.ai.rag

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.embeddings.TextEmbeddingModel
import com.vaultbrain.core.ai.heuristics.HeuristicExtractor
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.integrations.model.ExternalRecord
import com.vaultbrain.shared.database.entity.RelationshipEntity
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.shared.model.VaultReminder
import com.vaultbrain.shared.model.VaultReminderStatus
import com.vaultbrain.shared.model.external.ExternalSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Realistic messy-scenario contracts over a pinned clock; 37 cases across six categories. */
@RunWith(Parameterized::class)
class ComplicatedScenariosBenchmarkTest(private val mode: String, private val name: String, private val fixture: JsonObject) {
    private val now = Instant.parse("2026-09-14T12:00:00Z").toEpochMilli()

    private fun value(key: String) = fixture.getValue(key).jsonPrimitive.content
    private fun day(iso: String): Long = LocalDate.parse(iso).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun items(key: String) = fixture[key]?.jsonArray.orEmpty()
    private fun merchantOf(item: JsonObject): String =
        item["merchant"]?.jsonPrimitive?.content
            ?: fixture["merchant"]?.jsonPrimitive?.content
            ?: item.getValue("title").jsonPrimitive.content

    @Test fun complicatedScenario() = runTest {
        if (fixture["knownIssue"]?.jsonPrimitive?.booleanOrNull == true) {
            assumeFalse("knownIssue_${value("name")}: ${value("issue")}", true)
        }
        when (mode) {
            "sum" -> sumScenario()
            "expiry_tool" -> expiryScenario()
            "parse" -> parseScenario()
            "replacement_chain" -> replacementChainScenario()
            "relationship_section" -> relationshipSectionScenario()
            "verify" -> verifyScenario()
            "classify" -> classifyScenario()
            "proactive" -> proactiveScenario()
            "retrieve" -> retrieveScenario()
        }
    }

    private suspend fun sumScenario() {
        val repository = mockk<VaultRepository>(relaxed = true)
        coEvery { repository.getActive() } returns items("items").map { bill ->
            VaultItem(
                id = bill.jsonObject.getValue("id").jsonPrimitive.content,
                title = bill.jsonObject.getValue("title").jsonPrimitive.content,
                sourceType = SourceType.CAMERA,
                createdAt = day(bill.jsonObject.getValue("date").jsonPrimitive.content),
                aiClassification = bill.jsonObject["classification"]?.let { Classification.valueOf(it.jsonPrimitive.content) }
                    ?: Classification.RECEIPT,
                parsedMetadata = mapOf(
                    "merchant" to merchantOf(bill.jsonObject),
                    "total" to bill.jsonObject.getValue("total").jsonPrimitive.content,
                    "currency" to bill.jsonObject.getValue("currency").jsonPrimitive.content
                )
            )
        }
        val result = DeterministicToolRegistry(repository, QueryIntentParser()).execute(value("query"), now)!!
        assertThat(result.evidence!!.headline).isEqualTo(value("expectHeadline"))
        fixture["expectFacts"]?.jsonArray?.forEach { assertThat(result.evidence!!.supportingFacts).contains(it.jsonPrimitive.content) }
        fixture["expectSourceCount"]?.let { assertThat(result.sources).hasSize(it.jsonPrimitive.int) }
        fixture["expectAnswer"]?.let { assertThat(result.answer).isEqualTo(it.jsonPrimitive.content) }
    }

    private suspend fun expiryScenario() {
        val repository = mockk<VaultRepository>(relaxed = true)
        coEvery { repository.getActive() } returns items("items").map { entry ->
            VaultItem(
                id = entry.jsonObject.getValue("id").jsonPrimitive.content,
                title = entry.jsonObject.getValue("title").jsonPrimitive.content,
                sourceType = SourceType.CAMERA,
                aiClassification = Classification.valueOf(entry.jsonObject.getValue("classification").jsonPrimitive.content),
                expiryDate = entry.jsonObject["expiry"]?.let { day(it.jsonPrimitive.content) }
            )
        }
        val result = DeterministicToolRegistry(repository, QueryIntentParser(), expiryKnowledge()).execute(value("query"), now)!!
        assertThat(result.evidence!!.headline).isEqualTo(value("expectHeadline"))
        assertThat(result.sources.map { it.id }).containsExactlyElementsIn(
            items("expectIds").map { it.jsonPrimitive.content }).inOrder()
        fixture["expectFacts"]?.jsonArray?.forEach { assertThat(result.evidence!!.supportingFacts).contains(it.jsonPrimitive.content) }
    }

    private fun expiryKnowledge(): com.vaultbrain.core.database.repository.KnowledgeRepository? {
        val relations = fixture["relations"] ?: return null
        val knowledge = mockk<com.vaultbrain.core.database.repository.KnowledgeRepository>()
        coEvery { knowledge.relationshipsForItems(any()) } returns relations.jsonArray.map {
            RelationshipEntity(
                id = "${it.jsonObject.getValue("from").jsonPrimitive.content}-${it.jsonObject.getValue("to").jsonPrimitive.content}",
                sourceItemId = it.jsonObject.getValue("from").jsonPrimitive.content,
                targetItemId = it.jsonObject.getValue("to").jsonPrimitive.content,
                type = it.jsonObject.getValue("type").jsonPrimitive.content
            )
        }
        return knowledge
    }

    private fun parseScenario() {
        val intent = QueryIntentParser().parse(value("query"), now, ZoneId.systemDefault())
        when (val expect = value("expect")) {
            "null" -> assertThat(intent).isNull()
            "ExpiringSoon" -> assertThat(intent).isEqualTo(QueryIntent.ExpiringSoon())
            "DocumentReplacementChains" -> assertThat(intent).isEqualTo(QueryIntent.DocumentReplacementChains())
            else -> when {
                expect.startsWith("ExpiringSoon:") ->
                    assertThat(intent).isEqualTo(QueryIntent.ExpiringSoon(expect.removePrefix("ExpiringSoon:").toInt()))
                expect.startsWith("SumAmounts:") -> {
                    assertThat(intent).isInstanceOf(QueryIntent.SumAmounts::class.java)
                    assertThat((intent as QueryIntent.SumAmounts).merchant).isEqualTo(expect.removePrefix("SumAmounts:"))
                }
            }
        }
    }

    private suspend fun replacementChainScenario() {
        val repository = mockk<VaultRepository>(relaxed = true)
        coEvery { repository.getActive() } returns items("items").map { entry ->
            VaultItem(
                id = entry.jsonObject.getValue("id").jsonPrimitive.content,
                title = entry.jsonObject.getValue("title").jsonPrimitive.content,
                sourceType = SourceType.CAMERA,
                recurringRule = entry.jsonObject["recurring"]?.jsonPrimitive?.content,
                parsedMetadata = mapOf("merchant" to entry.jsonObject.getValue("merchant").jsonPrimitive.content)
            )
        }
        val result = DeterministicToolRegistry(repository, QueryIntentParser()).execute(value("query"), now)!!
        assertThat(result.evidence!!.headline).isEqualTo(value("expectHeadline"))
        fixture["expectFacts"]?.jsonArray?.forEach { assertThat(result.evidence!!.supportingFacts).contains(it.jsonPrimitive.content) }
    }

    private fun relationshipSectionScenario() {
        val sources = items("items").map {
            VaultItem(id = it.jsonObject.getValue("id").jsonPrimitive.content,
                title = it.jsonObject.getValue("title").jsonPrimitive.content, sourceType = SourceType.MANUAL)
        }
        val relationships = items("relations").map {
            RelationshipEntity(
                id = it.jsonObject.getValue("id").jsonPrimitive.content,
                sourceItemId = it.jsonObject.getValue("from").jsonPrimitive.content,
                targetItemId = it.jsonObject.getValue("to").jsonPrimitive.content,
                type = it.jsonObject.getValue("type").jsonPrimitive.content
            )
        }
        val engine = RagEngine(mockk<Context>(relaxed = true), mockk(), mockk(), mockk(),
            mockk<VaultRepository>(relaxed = true), mockk(), mockk(), mockk())
        val section = engine.buildRelationshipsSection(mapOf(sources.first().id to relationships), sources)
        items("expect").forEach { assertThat(section).contains(it.jsonPrimitive.content) }
        items("reject").forEach { assertThat(section).doesNotContain(it.jsonPrimitive.content) }
    }

    private suspend fun verifyScenario() {
        val model = mockk<LlmClient>()
        every { model.isAvailable() } returns false
        val sources = items("sources").mapIndexed { index, entry ->
            VaultItem(
                id = "s$index",
                title = entry.jsonObject["title"]?.jsonPrimitive?.content ?: "Record $index",
                sourceType = SourceType.MANUAL,
                rawOcrText = entry.jsonObject.getValue("text").jsonPrimitive.content
            )
        }
        val result = ClaimVerifier(model).verifyAndRepair(value("claim"), sources)
        fixture["expectExact"]?.let { assertThat(result).isEqualTo(it.jsonPrimitive.content); return }
        items("expect").forEach { assertThat(result).contains(it.jsonPrimitive.content) }
        items("reject").forEach { assertThat(result).doesNotContain(it.jsonPrimitive.content) }
    }

    private fun classifyScenario() {
        val result = HeuristicExtractor().extract(
            text = value("text"),
            visionObjects = items("vision").map { it.jsonPrimitive.content }
        )
        assertThat(result.inferredClassification).isEqualTo(Classification.valueOf(value("expectClassification")))
        fixture["rejectClassification"]?.let { assertThat(result.inferredClassification).isNotEqualTo(Classification.valueOf(it.jsonPrimitive.content)) }
        items("expectLens").forEach { assertThat(result.lensTags).contains(it.jsonPrimitive.content) }
        items("rejectLens").forEach { assertThat(result.lensTags).doesNotContain(it.jsonPrimitive.content) }
        fixture["expectMetadata"]?.jsonObject?.forEach { (key, expected) ->
            assertThat(result.metadata[key]).isEqualTo(expected.jsonPrimitive.content)
        }
        if (fixture["expectAlert"]?.jsonPrimitive?.booleanOrNull == true) assertThat(result.alertDate).isNotNull()
    }

    private suspend fun proactiveScenario() {
        val tools = mockk<DeterministicToolRegistry>()
        coEvery { tools.execute(any(), any()) } returns null
        fixture["missing"]?.let { facts ->
            coEvery { tools.execute("missing documents", any()) } returns RagResponse(answer = "a",
                evidence = RagEvidence(RagEvidenceKind.MISSING_DOCUMENTS, "h",
                    facts.jsonArray.map { it.jsonPrimitive.content }))
        }
        fixture["increases"]?.let { facts ->
            coEvery { tools.execute("recurring spend increases", any()) } returns RagResponse(answer = "a",
                evidence = RagEvidence(RagEvidenceKind.RECURRING_SPEND, "h",
                    facts.jsonArray.map { it.jsonPrimitive.content }))
        }
        val model = mockk<LlmClient>()
        every { model.isAvailable() } returns true
        var prompt = ""
        coEvery { model.generateForTask(any(), any()) } answers {
            prompt = firstArg()
            buildJsonObject {
                put("insights", JsonArray(items("insights").map { insight ->
                    buildJsonObject {
                        put("record", insight.jsonObject.getValue("record").jsonPrimitive.int)
                        put("evidence", insight.jsonObject.getValue("evidence").jsonPrimitive.content)
                        put("relevance", insight.jsonObject.getValue("relevance").jsonPrimitive.double)
                        put("confidence", insight.jsonObject.getValue("confidence").jsonPrimitive.double)
                        put("urgency", insight.jsonObject.getValue("urgency").jsonPrimitive.double)
                        put("novelty", insight.jsonObject.getValue("novelty").jsonPrimitive.double)
                    }
                }))
            }.toString()
        }
        val vaultItems = items("items").map {
            VaultItem(id = it.jsonObject.getValue("id").jsonPrimitive.content,
                title = it.jsonObject.getValue("title").jsonPrimitive.content,
                sourceType = SourceType.CAMERA,
                updatedAt = day(it.jsonObject.getValue("updatedAt").jsonPrimitive.content),
                expiryDate = it.jsonObject["expiry"]?.let { d -> day(d.jsonPrimitive.content) })
        }
        val events = items("events").map {
            ExternalRecord(
                connectorId = it.jsonObject.getValue("connectorId").jsonPrimitive.content,
                accountId = it.jsonObject.getValue("accountId").jsonPrimitive.content,
                externalId = it.jsonObject.getValue("externalId").jsonPrimitive.content,
                source = ExternalSource.CALENDAR,
                recordType = com.vaultbrain.shared.model.external.ExternalRecordType.EVENT,
                title = it.jsonObject.getValue("title").jsonPrimitive.content,
                startAt = day(it.jsonObject.getValue("startAt").jsonPrimitive.content),
                updatedAt = 0L
            )
        }
        val reminders = items("reminders").map {
            VaultReminder(id = it.jsonObject.getValue("id").jsonPrimitive.content,
                title = it.jsonObject.getValue("title").jsonPrimitive.content,
                dueAt = day(it.jsonObject.getValue("dueAt").jsonPrimitive.content),
                status = VaultReminderStatus.SCHEDULED,
                updatedAt = 0L)
        }
        val decoy = fixture["decoy"]?.jsonPrimitive?.booleanOrNull == true
        if (decoy) DecoySessionState.setDecoyMode(true)
        try {
            val result = DailyIntelligence(model, tools, clock = { now }).select(vaultItems, events, reminders)
            if (fixture["expectEmpty"]?.jsonPrimitive?.booleanOrNull == true) assertThat(result).isEmpty()
            fixture["expectOrder"]?.let { expected ->
                assertThat(result.map { it.evidence })
                    .containsExactlyElementsIn(expected.jsonArray.map { it.jsonPrimitive.content }).inOrder()
            }
            fixture["expectKept"]?.let { expected ->
                assertThat(result.map { it.evidence })
                    .containsExactlyElementsIn(expected.jsonArray.map { it.jsonPrimitive.content })
            }
            fixture["expectItemIds"]?.let { expected ->
                assertThat(result.map { it.itemId })
                    .containsExactlyElementsIn(expected.jsonArray.map { it.jsonPrimitive.content }).inOrder()
            }
            fixture["expectPromptContains"]?.jsonArray?.forEach { assertThat(prompt).contains(it.jsonPrimitive.content) }
        } finally {
            if (decoy) DecoySessionState.setDecoyMode(false)
        }
    }

    private suspend fun retrieveScenario() {
        val repository = mockk<VaultRepository>()
        val embedding = mockk<TextEmbeddingModel>()
        every { embedding.isAvailable() } returns false
        val vaultItems = items("items").map {
            VaultItem(id = it.jsonObject.getValue("id").jsonPrimitive.content,
                title = it.jsonObject.getValue("title").jsonPrimitive.content,
                sourceType = SourceType.GALLERY,
                rawOcrText = it.jsonObject["ocr"]?.jsonPrimitive?.content,
                aiClassification = Classification.UTILITY_BILL)
        }
        coEvery { repository.search(value("query")) } returns vaultItems
        coEvery { repository.getActiveCollections() } returns emptyList()
        coEvery { repository.filterIds(any(), any(), any(), any(), any()) } returns vaultItems.map { it.id }
        coEvery { repository.getByIds(vaultItems.map { it.id }) } returns vaultItems
        val engine = RagEngine(mockk<Context>(relaxed = true), embedding, mockk(), mockk(relaxed = true),
            repository, mockk(), mockk(), mockk())
        val retrieved = engine.retrieveHybrid(value("query"), SearchFilters())
        assertThat(retrieved.map { it.id })
            .containsExactlyElementsIn(items("expectIds").map { it.jsonPrimitive.content }).inOrder()
        fixture["expectPromptContains"]?.let {
            assertThat(engine.buildPrompt(value("query"), retrieved)).contains(it.jsonPrimitive.content)
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}: {0} {1}")
        fun cases(): Collection<Array<Any>> {
            val stream = requireNotNull(ComplicatedScenariosBenchmarkTest::class.java.getResourceAsStream("/complicated_scenarios.json"))
            val fixtures = stream.bufferedReader().use { Json.parseToJsonElement(it.readText()) }.jsonArray
            require(fixtures.size == 37)
            return fixtures.map { arrayOf<Any>(it.jsonObject.getValue("mode").jsonPrimitive.content,
                it.jsonObject.getValue("name").jsonPrimitive.content, it.jsonObject) }
        }
    }
}

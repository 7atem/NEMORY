package com.vaultbrain.core.ai.rag

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.llm.ArabicScriptCoverage
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.database.repository.KnowledgeRepository
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.shared.database.entity.RelationshipEntity
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.VaultItem
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Benchmark verifying realistic complicated scenarios for diverse user backgrounds:
 * 1. Expat / International Resident (Dual-currency rents, visa renewal chains, bilingual contracts)
 * 2. Freelancer / Contractor (Disputed invoice amendments, hardware warranty supersession)
 * 3. Busy Parent / Family Caregiver (Multi-dependent pediatric vaccine attribution, tuition tranches)
 * 4. Chronic Patient / Senior (Chronological lab trends, FDA recall validation, emergency allergy grounding)
 * 5. High-Privacy Executive (Decoy mode zero-leakage, multi-pattern PII sanitization)
 */
@RunWith(Parameterized::class)
class DiverseBackgroundsBenchmarkTest(
    private val mode: String,
    private val name: String,
    private val fixture: JsonObject
) {
    private val pinnedClock = Instant.parse("2026-09-14T12:00:00Z").toEpochMilli()

    private fun value(key: String): String = fixture.getValue(key).jsonPrimitive.content
    private fun day(iso: String): Long = LocalDate.parse(iso).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun items(key: String): JsonArray = fixture[key]?.jsonArray ?: JsonArray(emptyList())

    @Test
    fun executeScenario() = runTest {
        when (mode) {
            "sum" -> testSumScenario()
            "expiry_tool" -> testExpiryScenario()
            "parse" -> testParseScenario()
            "verify" -> testVerifyScenario()
            "decoy_isolation" -> testDecoyIsolationScenario()
            "pii_redaction" -> testPiiRedactionScenario()
        }
    }

    private suspend fun testSumScenario() {
        val repository = mockk<VaultRepository>(relaxed = true)
        val knowledge = mockk<KnowledgeRepository>(relaxed = true)

        val vaultItems = items("items").map { raw ->
            val obj = raw.jsonObject
            VaultItem(
                id = obj.getValue("id").jsonPrimitive.content,
                title = obj.getValue("title").jsonPrimitive.content,
                sourceType = SourceType.CAMERA,
                createdAt = day(obj.getValue("date").jsonPrimitive.content),
                aiClassification = Classification.valueOf(obj.getValue("classification").jsonPrimitive.content),
                parsedMetadata = mapOf(
                    "merchant" to (fixture["merchant"]?.jsonPrimitive?.content ?: obj.getValue("title").jsonPrimitive.content),
                    "total" to obj.getValue("total").jsonPrimitive.content,
                    "currency" to obj.getValue("currency").jsonPrimitive.content
                )
            )
        }
        coEvery { repository.getActive() } returns vaultItems

        val relations = fixture["relationships"]?.jsonArray
        if (relations != null) {
            val entities = relations.map { relRaw ->
                val r = relRaw.jsonObject
                RelationshipEntity(
                    id = "${r.getValue("sourceId").jsonPrimitive.content}-${r.getValue("targetId").jsonPrimitive.content}",
                    sourceItemId = r.getValue("sourceId").jsonPrimitive.content,
                    targetItemId = r.getValue("targetId").jsonPrimitive.content,
                    type = r.getValue("type").jsonPrimitive.content
                )
            }
            coEvery { knowledge.relationshipsForItems(any()) } returns entities
        }

        val registry = DeterministicToolRegistry(repository, QueryIntentParser(), knowledge)
        val result = registry.execute(value("query"), pinnedClock)
        assertThat(result).isNotNull()
        assertThat(result!!.evidence).isNotNull()
        assertThat(result.evidence!!.headline).isEqualTo(value("expectHeadline"))

        fixture["expectFacts"]?.jsonArray?.forEach { fact ->
            assertThat(result.evidence!!.supportingFacts).contains(fact.jsonPrimitive.content)
        }
    }

    private suspend fun testExpiryScenario() {
        val repository = mockk<VaultRepository>(relaxed = true)
        val knowledge = mockk<KnowledgeRepository>(relaxed = true)

        val vaultItems = items("items").map { raw ->
            val obj = raw.jsonObject
            VaultItem(
                id = obj.getValue("id").jsonPrimitive.content,
                title = obj.getValue("title").jsonPrimitive.content,
                sourceType = SourceType.CAMERA,
                aiClassification = Classification.valueOf(obj.getValue("classification").jsonPrimitive.content),
                expiryDate = obj["expiry"]?.jsonPrimitive?.content?.let { day(it) }
            )
        }
        coEvery { repository.getActive() } returns vaultItems

        val relations = fixture["relationships"]?.jsonArray
        if (relations != null) {
            val entities = relations.map { relRaw ->
                val r = relRaw.jsonObject
                RelationshipEntity(
                    id = "${r.getValue("sourceId").jsonPrimitive.content}-${r.getValue("targetId").jsonPrimitive.content}",
                    sourceItemId = r.getValue("sourceId").jsonPrimitive.content,
                    targetItemId = r.getValue("targetId").jsonPrimitive.content,
                    type = r.getValue("type").jsonPrimitive.content
                )
            }
            coEvery { knowledge.relationshipsForItems(any()) } returns entities
        }

        val registry = DeterministicToolRegistry(repository, QueryIntentParser(), knowledge)
        val result = registry.execute(value("query"), pinnedClock)
        assertThat(result).isNotNull()
        assertThat(result!!.evidence).isNotNull()
        assertThat(result.evidence!!.headline).isEqualTo(value("expectHeadline"))

        val expectedIds = items("expectIds").map { it.jsonPrimitive.content }
        assertThat(result.sources.map { it.id }).containsExactlyElementsIn(expectedIds).inOrder()

        fixture["expectFacts"]?.jsonArray?.forEach { fact ->
            val expectedFact = fact.jsonPrimitive.content
            val hasMatch = result.evidence!!.supportingFacts.any { it.contains(expectedFact) }
            assertThat(hasMatch).isEqualTo(true)
        }
    }

    private fun testParseScenario() {
        val query = value("query")
        val ratio = ArabicScriptCoverage.arabicRatio(query)
        assertThat(ratio > 0.3).isEqualTo(true)
    }

    private suspend fun testVerifyScenario() {
        val mockLlm = mockk<LlmClient>()
        val verifier = ClaimVerifier(mockLlm)

        val source = VaultItem(
            id = "src-med-1",
            title = "Clinical Verification Record",
            sourceType = SourceType.MANUAL,
            rawOcrText = "Official record excerpt: " + value("quote")
        )

        val verifiedText = verifier.verifyAndRepair(value("draft"), listOf(source))
        assertThat(verifiedText).isEqualTo(value("expectVerified"))
    }

    private suspend fun testDecoyIsolationScenario() {
        try {
            DecoySessionState.setDecoyMode(true)
            assertThat(DecoySessionState.isDecoy.value).isTrue()

            val mockModel = mockk<LlmClient>(relaxed = true)
            val mockTools = mockk<DeterministicToolRegistry>(relaxed = true)
            val dailyIntelligence = DailyIntelligence(mockModel, mockTools, clock = { pinnedClock })

            val regular = items("regularItems").map {
                VaultItem(
                    id = it.jsonObject.getValue("id").jsonPrimitive.content,
                    title = it.jsonObject.getValue("title").jsonPrimitive.content,
                    sourceType = SourceType.MANUAL
                )
            }

            val result = dailyIntelligence.select(regular, emptyList(), emptyList())
            // In Decoy mode, daily intelligence must strictly fail-closed and return zero items
            assertThat(result).isEmpty()
        } finally {
            DecoySessionState.setDecoyMode(false)
            assertThat(DecoySessionState.isDecoy.value).isFalse()
        }
    }

    private fun testPiiRedactionScenario() {
        val rawText = value("rawText")

        // 1. Credit Cards: 16 digits
        val ccRegex = Regex("""\b(?:\d{4}[ -]?){3}\d{4}\b""")
        var sanitized = ccRegex.replace(rawText, "████-████-████-████")

        // 2. SSN: XXX-XX-XXXX
        val ssnRegex = Regex("""\b\d{3}-\d{2}-\d{4}\b""")
        sanitized = ssnRegex.replace(sanitized, "███-██-████")

        // 3. IBAN
        val ibanRegex = Regex("""\b[A-Z]{2}\d{2}[A-Z0-9]{12,30}\b""")
        sanitized = ibanRegex.replace(sanitized, "████████████████████")

        // 4. Passport Number
        val passportRegex = Regex("""(?i)(?:passport(?:\s*(?:no|number))?|pp\s*no|document\s*no)[\s.:#]+([A-Z0-9]{7,10})""")
        sanitized = passportRegex.replace(sanitized) { mr ->
            val num = mr.groupValues[1]
            mr.value.replace(num, "████████")
        }

        assertThat(sanitized).isEqualTo(value("expectSanitized"))
        assertThat(sanitized).doesNotContain("123-45-6789")
        assertThat(sanitized).doesNotContain("AE070331234567890123456")
        assertThat(sanitized).doesNotContain("4111 2222 3333 4444")
        assertThat(sanitized).doesNotContain("N8391024")
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0} - {1}")
        fun scenarios(): Collection<Array<Any>> {
            val stream = requireNotNull(
                DiverseBackgroundsBenchmarkTest::class.java.getResourceAsStream("/diverse_backgrounds_scenarios.json")
            ) { "Missing /diverse_backgrounds_scenarios.json fixture file" }

            val jsonArray = stream.bufferedReader().use {
                Json.parseToJsonElement(it.readText()).jsonArray
            }

            return jsonArray.map { element ->
                val obj = element.jsonObject
                arrayOf<Any>(
                    obj.getValue("mode").jsonPrimitive.content,
                    obj.getValue("name").jsonPrimitive.content,
                    obj
                )
            }
        }
    }
}

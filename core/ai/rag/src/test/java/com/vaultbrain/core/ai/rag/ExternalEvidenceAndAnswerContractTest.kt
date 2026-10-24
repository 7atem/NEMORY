package com.vaultbrain.core.ai.rag

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.ai.llm.ReasoningBudget
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.integrations.context.PersonalContextEngine
import com.vaultbrain.core.integrations.model.ContextQuery
import com.vaultbrain.core.integrations.model.ContextResult
import com.vaultbrain.core.integrations.model.ExternalRecord
import com.vaultbrain.shared.database.dao.VaultReminderDao
import com.vaultbrain.shared.database.entity.VaultReminderEntity
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.shared.model.VaultReminderStatus
import com.vaultbrain.shared.model.external.ExternalRecordType
import com.vaultbrain.shared.model.external.ExternalSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExternalEvidenceAndAnswerContractTest {

    private val vaultRepo = mockk<VaultRepository>(relaxed = true)
    private val contextEngine = mockk<PersonalContextEngine>()
    private val reminderDao = mockk<VaultReminderDao>()
    private val llmClient = mockk<LlmClient>()

    @Test
    fun `search_calendar recognizes calendar intent terms and passes empty text to retrieve all events`() = runTest {
        val querySlot = slot<ContextQuery>()
        val event = ExternalRecord(
            connectorId = "calendar",
            accountId = "device",
            externalId = "evt_1",
            source = ExternalSource.CALENDAR,
            recordType = ExternalRecordType.EVENT,
            title = "Dentist Appointment with Dr. Smith",
            startAt = LocalDate.of(2026, 10, 25).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        coEvery { contextEngine.assembleContext(capture(querySlot)) } returns ContextResult(records = listOf(event))

        val executor = AgentToolExecutor(vaultRepo, contextEngine, reminderDao)
        val args = buildJsonObject { put("query", "meetings tomorrow") }
        val result = executor.execute("search_calendar", args, SearchFilters())

        // "meetings" and "tomorrow" are calendar intent terms; text should be empty string
        assertThat(querySlot.captured.text).isEmpty()
        assertThat(result.records).containsExactly(event)
        assertThat(result.error).isNull()
    }

    @Test
    fun `search_calendar filters records by from_date and to_date`() = runTest {
        val now = LocalDate.of(2026, 10, 25).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val later = LocalDate.of(2026, 11, 15).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val octEvent = ExternalRecord("calendar", "device", "evt_oct", ExternalSource.CALENDAR, ExternalRecordType.EVENT,
            title = "October Meeting", startAt = now)
        val novEvent = ExternalRecord("calendar", "device", "evt_nov", ExternalSource.CALENDAR, ExternalRecordType.EVENT,
            title = "November Meeting", startAt = later)

        coEvery { contextEngine.assembleContext(any()) } returns ContextResult(records = listOf(octEvent, novEvent))

        val executor = AgentToolExecutor(vaultRepo, contextEngine, reminderDao)
        val args = buildJsonObject {
            put("query", "doctor")
            put("from_date", "2026-10-01")
            put("to_date", "2026-10-31")
        }
        val result = executor.execute("search_calendar", args, SearchFilters())

        assertThat(result.records).containsExactly(octEvent)
    }

    @Test
    fun `get_reminders executes successfully via VaultReminderDao and maps to ExternalRecord`() = runTest {
        val reminderTime = LocalDate.of(2026, 10, 26).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val reminder = VaultReminderEntity(
            id = "rem_1",
            title = "Pay electric bill",
            dueAt = reminderTime,
            status = VaultReminderStatus.SCHEDULED,
            createdAt = reminderTime - 86400000,
            updatedAt = reminderTime - 86400000
        )
        every { reminderDao.observeActive() } returns flowOf(listOf(reminder))

        val executor = AgentToolExecutor(vaultRepo, contextEngine, reminderDao)
        val args = buildJsonObject { put("status", "active") }
        val result = executor.execute("get_reminders", args, SearchFilters())

        assertThat(result.records).hasSize(1)
        val record = result.records.single()
        assertThat(record.externalId).isEqualTo("rem_1")
        assertThat(record.title).isEqualTo("Pay electric bill")
        assertThat(record.source).isEqualTo(ExternalSource.VAULT_REMINDER)
        assertThat(record.payload["due_date"]).isEqualTo("2026-10-26")
    }

    @Test
    fun `ClaimVerifier verify extracts SourcedFact with dates and amounts`() = runTest {
        val item = VaultItem(
            id = "item_rent",
            title = "Apartment Lease Agreement",
            sourceType = SourceType.MANUAL,
            rawOcrText = "Monthly rent is AED 12,500 due on 2026-11-01"
        )
        val verifier = ClaimVerifier(llmClient)
        val answer = "> Monthly rent is AED 12,500 due on 2026-11-01 [1]"

        val result = verifier.verify(answer, listOf(item), emptyList())

        assertThat(result.sourcedFacts).hasSize(1)
        val fact = result.sourcedFacts.single()
        assertThat(fact.sourceIndex).isEqualTo(1)
        assertThat(fact.sourceId).isEqualTo("item_rent")
        assertThat(fact.factText).isEqualTo("Monthly rent is AED 12,500 due on 2026-11-01")
        assertThat(fact.dates).contains("2026-11-01")
        assertThat(fact.amounts).contains("AED 12,500")
        assertThat(result.repairedAnswer).contains("> Monthly rent is AED 12,500 due on 2026-11-01 [1]")
    }

    @Test
    fun `ClaimVerifier verify handles external records and maps sourceId accurately`() = runTest {
        val event = ExternalRecord(
            connectorId = "calendar",
            accountId = "device",
            externalId = "evt_doctor_42",
            source = ExternalSource.CALENDAR,
            recordType = ExternalRecordType.EVENT,
            title = "Annual Physical Exam",
            startAt = LocalDate.of(2026, 10, 28).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        val verifier = ClaimVerifier(llmClient)
        val answer = "> Annual Physical Exam [1]"

        val result = verifier.verify(answer, emptyList(), listOf(event))

        assertThat(result.sourcedFacts).hasSize(1)
        val fact = result.sourcedFacts.single()
        assertThat(fact.sourceIndex).isEqualTo(1)
        assertThat(fact.sourceId).isEqualTo("evt_doctor_42")
        assertThat(fact.factText).isEqualTo("Annual Physical Exam")
    }

    @Test
    fun `ClaimVerifier fails closed when numbers are hallucinated`() = runTest {
        val item = VaultItem(
            id = "doc1",
            title = "Invoice",
            sourceType = SourceType.MANUAL,
            rawOcrText = "Total fee: $50"
        )
        val verifier = ClaimVerifier(llmClient)
        every { llmClient.isAvailable() } returns false

        // Model hallucinates $500 instead of $50
        val answer = "Total fee: $500 [1]"
        val result = verifier.verify(answer, listOf(item), emptyList())

        // The repaired answer should NOT contain $500; it falls back to the exact quote Total fee: $50 [1]
        assertThat(result.repairedAnswer).doesNotContain("$500")
        assertThat(result.repairedAnswer).contains("> Total fee: $50 [1]")
        assertThat(result.sourcedFacts.single().amounts).contains("$50")
    }

    @Test
    fun `LocalAgent retrieve executes typed calculation and records it in result calculations`() = runTest {
        every { llmClient.isAvailable() } returns true
        coEvery { llmClient.generateForTask(any(), any()) } returns """{"tool":"calculate","expression":"(100 * 1.05) + 50"}"""

        val executor = AgentToolExecutor(vaultRepo, contextEngine, reminderDao)
        val result = LocalAgent(llmClient).retrieve(
            question = "Calculate total",
            initial = emptyList(),
            search = { emptyList() },
            executeTypedTool = { tool, json -> executor.execute(tool, json, SearchFilters()) },
            budget = ReasoningBudget.FAST
        )

        assertThat(result.calculations).containsExactly("(100 * 1.05) + 50 = 155")
        assertThat(result.trace).hasSize(1)
        assertThat(result.trace.single().tool).isEqualTo("calculate")
    }
}

package com.vaultbrain.core.ai.rag

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.common.model.SourceType
import com.vaultbrain.core.common.model.VaultItem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LocalAgentTest {
    @Test fun `write tools and arbitrary ids are never executed`() = runTest {
        assertThat(LocalAgent.parseRead("""{"tool":"delete_item","item_ids":["1"]}""")).isNull()
        val model = mockk<LlmClient>()
        every { model.isAvailable() } returns true
        coEvery { model.generateForTask(any(), any()) } returns """{"tool":"get_vault_item","item_ids":["secret"]}"""
        var readCalled = false
        LocalAgent(model).retrieve(
            question = "question", 
            initial = emptyList(), 
            read = { _, _ ->
                readCalled = true
                emptyList()
            },
            search = { emptyList() }
        )
        assertThat(readCalled).isFalse()
    }
    @Test fun `rejects malformed and oversized plans and unrecognized fields`() {
        listOf("not json", "{\"queries\":[1]}", "{\"queries\":[],\"write\":true}",
            "{\"queries\":[\"a\",\"b\",\"c\"]}", "x".repeat(1025)).forEach {
            assertThat(LocalAgent.parseQueries(it)).isNull()
        }
    }

    @Test fun `repeated searches terminate without repeating tool execution`() = runTest {
        val model = mockk<LlmClient>()
        every { model.isAvailable() } returns true
        coEvery { model.generateForTask(any(), any()) } returns "{\"queries\":[\"renewal\"]}"
        val queries = mutableListOf<String>()
        val item = VaultItem(id = "1", title = "Passport", sourceType = SourceType.TEXT_PASTE)
        val result = LocalAgent(model).retrieve("passport", emptyList(), search = {
            queries += it
            listOf(item)
        })
        assertThat(queries).containsExactly("renewal")
        assertThat(result.sources).containsExactly(item)
    }

    @Test fun `parses valid proposals`() {
        val reminder = LocalAgent.parseProposal("""{"tool":"propose_reminder","title":"Renew Insurance","date":"2026-12-14","item_id":"123"}""")
        assertThat(reminder).isEqualTo(LocalAgent.ActionProposal.Reminder("Renew Insurance", "2026-12-14", "123"))

        val event = LocalAgent.parseProposal("""{"tool":"propose_calendar_event","title":"Service Car","start":"2026-10-01T09:00","end":"2026-10-01T11:00","notes":"At dealer"}""")
        assertThat(event).isEqualTo(LocalAgent.ActionProposal.CalendarEvent("Service Car", "2026-10-01T09:00", "2026-10-01T11:00", "At dealer"))

        val membership = LocalAgent.parseProposal("""{"tool":"propose_collection_membership","item_id":"123","collection_id":"456"}""")
        assertThat(membership).isEqualTo(LocalAgent.ActionProposal.CollectionMembership("123", "456"))

        val metadata = LocalAgent.parseProposal("""{"tool":"propose_metadata_update","item_id":"123","key":"category","value":"receipt"}""")
        assertThat(metadata).isEqualTo(LocalAgent.ActionProposal.MetadataUpdate("123", "category", "receipt"))
    }

    @Test fun `rejects invalid proposals`() {
        assertThat(LocalAgent.parseProposal("""{"tool":"propose_reminder"}""")).isNull()
        assertThat(LocalAgent.parseProposal("""{"tool":"unknown_proposal"}""")).isNull()
    }
}

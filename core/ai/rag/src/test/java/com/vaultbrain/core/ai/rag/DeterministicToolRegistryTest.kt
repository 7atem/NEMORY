package com.vaultbrain.core.ai.rag

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeterministicToolRegistryTest {
    private val repository = mockk<VaultRepository>()
    private val tools = DeterministicToolRegistry(repository, QueryIntentParser())
    private val now = Instant.parse("2026-08-24T12:00:00Z").toEpochMilli()

    @Test
    fun `sums recorded totals per currency and never combines currencies`() = runTest {
        coEvery { repository.getActive() } returns listOf(
            receipt("1", "Carrefour one", "120.50", "EGP"),
            receipt("2", "Carrefour two", "79.50", "EGP"),
            receipt("3", "Carrefour abroad", "10", "USD")
        )

        val response = tools.execute("How much did I spend at Carrefour?", now)!!

        assertThat(response.evidence?.kind).isEqualTo(RagEvidenceKind.TOTAL)
        assertThat(response.evidence?.headline).isEqualTo("Totals by currency")
        assertThat(response.evidence?.supportingFacts).containsAtLeast(
            "EGP 200 • 2 items",
            "USD 10 • 1 items"
        )
        assertThat(response.sources).hasSize(3)
    }

    @Test
    fun `expiry lookup returns only future items in window`() = runTest {
        val day = 24L * 60 * 60 * 1000
        coEvery { repository.getActive() } returns listOf(
            item("soon", "Passport", Classification.PASSPORT, expiryDate = now + 10 * day),
            item("past", "Old ID", Classification.IDENTITY_DOCUMENT, expiryDate = now - day),
            item("later", "Long passport", Classification.PASSPORT, expiryDate = now + 120 * day)
        )

        val response = tools.execute("What is expiring soon?", now)!!

        assertThat(response.evidence?.kind).isEqualTo(RagEvidenceKind.EXPIRING)
        assertThat(response.sources.map(VaultItem::id)).containsExactly("soon")
    }

    @Test
    fun `upcoming travel uses travel action dates only`() = runTest {
        val day = 24L * 60 * 60 * 1000
        coEvery { repository.getActive() } returns listOf(
            item("flight", "Cairo flight", Classification.TICKET, secondaryAlertDate = now + day),
            item("receipt", "Dated receipt", Classification.RECEIPT, secondaryAlertDate = now + day)
        )

        val response = tools.execute("Show my upcoming travel", now)!!

        assertThat(response.evidence?.kind).isEqualTo(RagEvidenceKind.UPCOMING_TRAVEL)
        assertThat(response.sources.map(VaultItem::id)).containsExactly("flight")
    }

    @Test
    fun `unknown query does not read repository`() = runTest {
        assertThat(tools.execute("where is the blue folder", now)).isNull()
        coVerify(exactly = 0) { repository.getActive() }
    }

    @Test
    fun `trip briefing selects the nearest named trip and binds its sources`() = runTest {
        val day = 24L * 60 * 60 * 1000
        coEvery { repository.getActive() } returns listOf(
            item("flight", "Cairo to Rome", Classification.TICKET, secondaryAlertDate = now + 10 * day).copy(
                parsedMetadata = mapOf("trip_id" to "Rome", "flight_number" to "MS 791", "gate" to "B4")
            ),
            item("hotel", "Rome hotel", Classification.HOTEL, secondaryAlertDate = now + 12 * day).copy(
                parsedMetadata = mapOf("trip_id" to "Rome", "hotel" to "Centro Hotel")
            ),
            item("later", "Paris flight", Classification.TICKET, secondaryAlertDate = now + 50 * day).copy(
                parsedMetadata = mapOf("trip_id" to "Paris")
            )
        )

        val response = tools.execute("Build my upcoming trip briefing", now)!!

        assertThat(response.evidence?.kind).isEqualTo(RagEvidenceKind.TRIP_BRIEFING)
        assertThat(response.sources.map(VaultItem::id)).containsExactly("flight", "hotel").inOrder()
        assertThat(response.evidence?.supportingFacts?.first()).contains("Flight MS 791")
        assertThat(response.evidence?.supportingFacts?.first()).contains("Gate B4")
    }

    @Test
    fun `recurring comparison uses latest pair per provider and currency`() = runTest {
        val day = 24L * 60 * 60 * 1000
        coEvery { repository.getActive() } returns listOf(
            recurring("old", "Internet", "100", "EGP", now - 30 * day),
            recurring("new", "Internet", "125", "EGP", now),
            recurring("usd", "Internet", "500", "USD", now)
        )

        val response = tools.execute("Which subscriptions increased?", now)!!

        assertThat(response.evidence?.kind).isEqualTo(RagEvidenceKind.RECURRING_SPEND)
        assertThat(response.evidence?.headline).isEqualTo("1 recurring increases")
        assertThat(response.evidence?.supportingFacts).containsExactly(
            "Internet | EGP 100 -> EGP 125 | +25 (25%)"
        )
        assertThat(response.sources.map(VaultItem::id)).containsExactly("new", "old").inOrder()
    }

    @Test
    fun `recurring comparison does not report decreases or combine currencies`() = runTest {
        coEvery { repository.getActive() } returns listOf(
            recurring("egp", "Storage", "100", "EGP", now - 1),
            recurring("usd", "Storage", "200", "USD", now),
            recurring("old", "Music", "20", "EGP", now - 1),
            recurring("new", "Music", "15", "EGP", now)
        )

        val response = tools.execute("Compare recurring charge increases", now)!!

        assertThat(response.evidence?.kind).isEqualTo(RagEvidenceKind.RECURRING_SPEND)
        assertThat(response.sources).isEmpty()
    }

    @Test
    fun `media backlog excludes completed items and keeps books movies and series`() = runTest {
        coEvery { repository.getActive() } returns listOf(
            item("movie", "Dune", Classification.MOVIE).copy(
                parsedMetadata = mapOf("media_status" to "want_to_watch")
            ),
            item("book", "The Pragmatic Programmer", Classification.BOOK).copy(
                parsedMetadata = mapOf("media_status" to "reading")
            ),
            item("done", "Arrival", Classification.MOVIE).copy(
                parsedMetadata = mapOf("media_status" to "watched")
            ),
            item("receipt", "Cinema receipt", Classification.RECEIPT)
        )

        val response = tools.execute("What is on my watchlist or reading list?", now)!!

        assertThat(response.evidence?.kind).isEqualTo(RagEvidenceKind.MEDIA_BACKLOG)
        assertThat(response.sources.map(VaultItem::id)).containsExactly("movie", "book")
        assertThat(response.evidence?.headline).isEqualTo("2 items to watch or read")
    }

    @Test
    fun `advanced tools remain read only`() = runTest {
        coEvery { repository.getActive() } returns emptyList()

        tools.execute("Build my upcoming trip briefing", now)
        tools.execute("Which subscriptions increased?", now)
        tools.execute("What is on my watchlist?", now)

        coVerify(exactly = 0) { repository.save(any()) }
        coVerify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun `duplicate bills within a few days are excluded and disclosed`() = runTest {
        coEvery { repository.getActive() } returns listOf(
            receipt("d1", "North Power May", "11", "EGP").copy(createdAt = now - 40 * DAY_MS),
            receipt("d2", "North Power duplicate", "11", "EGP").copy(createdAt = now - 38 * DAY_MS),
            receipt("d3", "North Power later", "20", "EGP").copy(createdAt = now - 10 * DAY_MS)
        )

        val response = tools.execute("How much did I spend at North Power?", now)!!

        assertThat(response.evidence?.headline).isEqualTo("EGP 31")
        assertThat(response.evidence?.supportingFacts).contains("EGP 31 • 2 items")
        assertThat(response.evidence?.supportingFacts).contains("1 possible duplicates excluded")
        assertThat(response.sources.map(VaultItem::id)).containsExactly("d1", "d3")
    }

    @Test
    fun `year sum discloses months with no merchant document`() = runTest {
        coEvery { repository.getActive() } returns listOf(
            receipt("m1", "North Power January", "12", "EGP").copy(createdAt = now - 212 * DAY_MS),
            receipt("m3", "North Power April", "14", "EGP").copy(createdAt = now - 139 * DAY_MS)
        )

        val response = tools.execute("How much did I spend at North Power this year?", now)!!

        assertThat(response.evidence?.headline).isEqualTo("EGP 26")
        assertThat(response.evidence?.supportingFacts)
            .contains("No north power document found for February, March, May, June, July, August")
    }

    @Test
    fun `expiring soon excludes documents superseded by a replaces relationship`() = runTest {
        val day = 24L * 60 * 60 * 1000
        val knowledge = mockk<com.vaultbrain.core.database.repository.KnowledgeRepository>()
        val awareTools = DeterministicToolRegistry(repository, QueryIntentParser(), knowledge)
        coEvery { repository.getActive() } returns listOf(
            item("oldp", "Old car insurance", Classification.LEGAL_DOCUMENT, expiryDate = now + 20 * day),
            item("newp", "New car insurance", Classification.LEGAL_DOCUMENT, expiryDate = now + 300 * day),
            item("pass", "Passport", Classification.PASSPORT, expiryDate = now + 45 * day)
        )
        coEvery { knowledge.relationshipsForItems(any()) } returns listOf(
            com.vaultbrain.shared.database.entity.RelationshipEntity("r1", "newp", "oldp", "REPLACES")
        )

        val response = awareTools.execute("What is expiring soon?", now)!!

        assertThat(response.sources.map(VaultItem::id)).containsExactly("pass")
    }

    @Test
    fun `document chains come from real relationships when available`() = runTest {
        val knowledge = mockk<com.vaultbrain.core.database.repository.KnowledgeRepository>()
        val awareTools = DeterministicToolRegistry(repository, QueryIntentParser(), knowledge)
        coEvery { repository.getActive() } returns listOf(
            item("p1", "AXA policy 2025", Classification.LEGAL_DOCUMENT),
            item("p2", "AXA policy 2026", Classification.LEGAL_DOCUMENT)
        )
        coEvery { knowledge.relationshipsForItems(any()) } returns listOf(
            com.vaultbrain.shared.database.entity.RelationshipEntity("r1", "p2", "p1", "REPLACES")
        )

        val response = awareTools.execute("Show me document renewals", now)!!

        assertThat(response.evidence?.headline).isEqualTo("Document Chains")
        assertThat(response.evidence?.supportingFacts).containsExactly("AXA policy 2026 renewals (2 documents)")
    }

    private val DAY_MS = 24L * 60 * 60 * 1000

    private fun receipt(id: String, title: String, total: String, currency: String) =
        item(id, title, Classification.RECEIPT).copy(
            parsedMetadata = mapOf("merchant" to "Carrefour", "total" to total, "currency" to currency)
        )

    private fun recurring(
        id: String,
        provider: String,
        total: String,
        currency: String,
        createdAt: Long
    ) = item(id, provider, Classification.RECEIPT).copy(
        createdAt = createdAt,
        recurringRule = "monthly",
        parsedMetadata = mapOf("merchant" to provider, "total" to total, "currency" to currency)
    )

    private fun item(
        id: String,
        title: String,
        classification: Classification,
        expiryDate: Long? = null,
        secondaryAlertDate: Long? = null
    ) = VaultItem(
        id = id,
        title = title,
        sourceType = SourceType.CAMERA,
        aiClassification = classification,
        expiryDate = expiryDate,
        secondaryAlertDate = secondaryAlertDate,
        createdAt = now
    )
}

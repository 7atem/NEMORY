package com.vaultbrain.feature.briefing

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.VaultItem
import java.time.Instant
import org.junit.Test

class DeterministicInsightEngineTest {
    private val engine = DeterministicInsightEngine()
    private val now = Instant.parse("2026-08-24T12:00:00Z").toEpochMilli()
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `prioritizes urgent expiry and keeps travel as a separate insight`() {
        val insights = engine.generate(
            listOf(
                item("passport", "Passport", Classification.PASSPORT, expiryDate = now + 10 * day),
                item("flight", "Cairo flight", Classification.TICKET, secondaryAlertDate = now + 3 * day)
            ),
            now
        )

        assertThat(insights.first()).isInstanceOf(TodayInsight.Expiring::class.java)
        val travel = insights.filterIsInstance<TodayInsight.UpcomingTravel>().single()
        assertThat(travel.primaryItemId).isEqualTo("flight")
        assertThat(travel.daysRemaining).isEqualTo(3L)
    }

    @Test
    fun `duplicate insight counts both sides and opens newest candidate`() {
        val insights = engine.generate(
            listOf(
                item("old", "Original", Classification.GENERAL_DOCUMENT, createdAt = now - day),
                item("new", "Copy", Classification.GENERAL_DOCUMENT, createdAt = now)
                    .copy(possibleDuplicateOfItemId = "old", duplicateSimilarity = 0.98f)
            ),
            now
        )

        val duplicate = insights.filterIsInstance<TodayInsight.PossibleDuplicates>().single()
        assertThat(duplicate.captureCount).isEqualTo(2)
        assertThat(duplicate.primaryItemId).isEqualTo("new")
        assertThat(duplicate.relatedItemIds).containsExactly("new", "old")
    }

    @Test
    fun `recurring charge increase uses same provider and currency only`() {
        val previous = money("old", now - 30 * day, "500", "EGP")
        val current = money("new", now, "550", "EGP")
        val unrelatedCurrency = money("usd", now - 10 * day, "20", "USD")

        val increase = engine.generate(listOf(previous, current, unrelatedCurrency), now)
            .filterIsInstance<TodayInsight.SpendingIncrease>()
            .single()

        assertThat(increase.currency).isEqualTo("EGP")
        assertThat(increase.previousAmount.toPlainString()).isEqualTo("500")
        assertThat(increase.currentAmount.toPlainString()).isEqualTo("550")
        assertThat(increase.delta.toPlainString()).isEqualTo("50")
    }

    @Test
    fun `ordinary purchases never trigger recurring spending insight`() {
        val items = listOf(
            money("old", now - day, "100", "EGP").copy(recurringRule = null, parsedMetadata = mapOf(
                "merchant" to "Store", "total" to "100", "currency" to "EGP"
            )),
            money("new", now, "200", "EGP").copy(recurringRule = null, parsedMetadata = mapOf(
                "merchant" to "Store", "total" to "200", "currency" to "EGP"
            ))
        )

        assertThat(engine.generate(items, now).filterIsInstance<TodayInsight.SpendingIncrease>()).isEmpty()
    }

    @Test
    fun `media backlog waits seven days and excludes completed items`() {
        val insights = engine.generate(
            listOf(
                item("old", "Dune", Classification.MOVIE, createdAt = now - 20 * day)
                    .copy(parsedMetadata = mapOf("media_status" to "want_to_watch")),
                item("done", "Arrival", Classification.MOVIE, createdAt = now - 30 * day)
                    .copy(parsedMetadata = mapOf("media_status" to "watched")),
                item("new", "New film", Classification.MOVIE, createdAt = now - day)
            ),
            now
        )

        val backlog = insights.filterIsInstance<TodayInsight.MediaBacklog>().single()
        assertThat(backlog.itemCount).isEqualTo(1)
        assertThat(backlog.sampleTitle).isEqualTo("Dune")
    }

    private fun money(id: String, createdAt: Long, amount: String, currency: String) =
        item(id, "Internet", Classification.INVOICE, createdAt = createdAt).copy(
            recurringRule = "monthly",
            parsedMetadata = mapOf(
                "merchant" to "ISP",
                "total" to amount,
                "currency" to currency
            )
        )

    private fun item(
        id: String,
        title: String,
        classification: Classification,
        createdAt: Long = now,
        expiryDate: Long? = null,
        secondaryAlertDate: Long? = null
    ) = VaultItem(
        id = id,
        title = title,
        sourceType = SourceType.MANUAL,
        createdAt = createdAt,
        aiClassification = classification,
        expiryDate = expiryDate,
        secondaryAlertDate = secondaryAlertDate
    )
}

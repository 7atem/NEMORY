package com.vaultbrain.core.ai.rag

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Test

class QueryIntentParserTest {
    private val parser = QueryIntentParser()
    private val now = Instant.parse("2026-08-24T12:00:00Z").toEpochMilli()

    @Test
    fun `parses merchant total and last month`() {
        val intent = parser.parse("What was the total spent at Carrefour last month?", now, ZoneOffset.UTC)

        assertThat(intent).isInstanceOf(QueryIntent.SumAmounts::class.java)
        intent as QueryIntent.SumAmounts
        assertThat(intent.merchant).isEqualTo("carrefour")
        assertThat(intent.range?.labelEn).isEqualTo("last month")
    }

    @Test
    fun `parses Arabic expiry intent`() {
        assertThat(parser.parse("ما المستندات التي تنتهي قريبًا؟", now, ZoneOffset.UTC))
            .isEqualTo(QueryIntent.ExpiringSoon())
    }

    @Test
    fun `parses plural money document lookup but leaves singular semantic search alone`() {
        assertThat(parser.parse("show my receipts this month", now, ZoneOffset.UTC))
            .isInstanceOf(QueryIntent.MoneyDocuments::class.java)
        assertThat(parser.parse("carrefour receipt", now, ZoneOffset.UTC)).isNull()
    }

    @Test
    fun `unknown wording fails closed to semantic RAG`() {
        assertThat(parser.parse("where did I put the blue folder", now, ZoneOffset.UTC)).isNull()
    }

    @Test
    fun `parses trip briefing before generic upcoming travel`() {
        assertThat(parser.parse("Build my upcoming trip briefing", now, ZoneOffset.UTC))
            .isEqualTo(QueryIntent.TripBriefing())
    }

    @Test
    fun `parses recurring increase comparison before generic spending`() {
        assertThat(parser.parse("Which subscriptions increased?", now, ZoneOffset.UTC))
            .isEqualTo(QueryIntent.RecurringSpendIncreases)
    }

    @Test
    fun `parses watchlist as deterministic media backlog`() {
        assertThat(parser.parse("What is on my watchlist or reading list?", now, ZoneOffset.UTC))
            .isEqualTo(QueryIntent.MediaBacklog)
    }

    @Test
    fun `parses Arabic trip briefing`() {
        assertThat(parser.parse("أنشئ ملخص السفر القادم", now, ZoneOffset.UTC))
            .isEqualTo(QueryIntent.TripBriefing())
    }

    @Test
    fun `parses renew questions as expiring soon with a next-n-days window`() {
        assertThat(parser.parse("What do I need to renew in the next 60 days?", now, ZoneOffset.UTC))
            .isEqualTo(QueryIntent.ExpiringSoon(60))
        assertThat(parser.parse("Which documents are up for renewal within 30 days?", now, ZoneOffset.UTC))
            .isEqualTo(QueryIntent.ExpiringSoon(30))
    }

    @Test
    fun `parses Arabic renew window with Arabic-Indic digits`() {
        assertThat(parser.parse("ما الذي يجب تجديده خلال ٣٠ يوم؟", now, ZoneOffset.UTC))
            .isEqualTo(QueryIntent.ExpiringSoon(30))
    }

    @Test
    fun `document renewals still route to replacement chains before expiry`() {
        assertThat(parser.parse("Show me document renewals", now, ZoneOffset.UTC))
            .isEqualTo(QueryIntent.DocumentReplacementChains())
    }
}

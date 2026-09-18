package com.vaultbrain.core.ai.llm

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.EnrichmentState
import com.vaultbrain.core.common.model.ExperienceId
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.core.common.model.ProactiveAction
import com.vaultbrain.core.common.model.ProactiveActionResolver
import com.vaultbrain.core.common.model.VaultItem
import org.junit.Test

class CaptureEnrichmentResultTest {

    @Test
    fun `parser accepts legacy lens and action fields while validating canonical values`() {
        val result = CaptureEnrichmentResultParser.parse(
            """{"classification":"MOVIE","lens":"MEDIA","title":"Dune","summary":"Add to watchlist","highlights":["Directed by Denis Villeneuve","Released in 2024"],"tags":["SciFi","Movie"],"metadata":{"provider_url":"https://imdb.com/title/tt1"},"suggested_actions":["OPEN_SOURCE","REMIND_LATER"],"confidence":0.91}"""
        )

        assertThat(result?.classification).isEqualTo(Classification.MOVIE)
        assertThat(result?.systemFacets).containsExactly(LensId.MEDIA)
        assertThat(result?.confidence).isEqualTo(0.91f)
        assertThat(result?.metadata?.get("provider_url")).isEqualTo("https://imdb.com/title/tt1")
        assertThat(result?.supportedActions).containsExactly(
            ProactiveAction.OPEN_SOURCE,
            ProactiveAction.REMIND_LATER
        )
        assertThat(result?.highlights).containsExactly(
            "Directed by Denis Villeneuve",
            "Released in 2024"
        ).inOrder()
        assertThat(result?.tags).containsExactly("SciFi", "Movie").inOrder()
    }

    @Test
    fun `parser handles surrounding prose and accepts custom classifications`() {
        val customResult = CaptureEnrichmentResultParser.parse(
            """{"classification":"SECRET","lens":"MEDIA","title":"x","summary":"x"}"""
        )
        assertThat(customResult?.classification).isEqualTo(Classification.OTHER)
        assertThat(customResult?.subtype).isEqualTo("SECRET")

        assertThat(CaptureEnrichmentResultParser.parse("not json")).isNull()
        
        val proseResult = CaptureEnrichmentResultParser.parse("prose {\"classification\":\"OTHER\", \"title\": \"x\", \"summary\": \"x\"}")
        assertThat(proseResult?.classification).isEqualTo(Classification.OTHER)
        
        val markdownResult = CaptureEnrichmentResultParser.parse("```json\n{\"classification\":\"CUSTOM\", \"title\": \"x\", \"summary\": \"x\"}\n```" )
        assertThat(markdownResult?.subtype).isEqualTo("CUSTOM")
    }

    @Test
    fun `merge enriches unknown fields without replacing reliable values`() {
        val item = VaultItem(
            id = "id",
            title = "User title",
            summary = "Reliable summary",
            aiClassification = Classification.UNKNOWN,
            aiConfidence = 0.8f,
            lensTags = setOf(LensId.BUREAUCRACY),
            enrichmentState = EnrichmentState.RUNNING
        )
        val result = CaptureEnrichmentResult(
            classification = Classification.BOOK,
            systemFacets = setOf(LensId.MEDIA),
            title = "Model title",
            summary = "Model summary",
            confidence = 0.7f
        )

        val enriched = result.applyTo(item)

        assertThat(enriched.title).isEqualTo("User title")
        assertThat(enriched.summary).isEqualTo("Reliable summary")
        assertThat(enriched.aiClassification).isEqualTo(Classification.BOOK)
        assertThat(enriched.aiConfidence).isEqualTo(0.8f)
        assertThat(enriched.lensTags).containsAtLeast(LensId.BUREAUCRACY, LensId.MEDIA)
        assertThat(enriched.enrichmentState).isEqualTo(EnrichmentState.COMPLETE)
    }

    @Test
    fun `historical adoption replaces heuristic content but preserves explicit category`() {
        val item = VaultItem(
            id = "historical",
            title = "Old OCR first line",
            summary = "Old heuristic summary",
            rawOcrText = "Atomic Habits by James Clear",
            aiClassification = Classification.GENERAL_DOCUMENT,
            userClassificationOverride = Classification.BOOK,
            enrichmentState = EnrichmentState.RUNNING
        )
        val result = CaptureEnrichmentResult(
            classification = Classification.BOOK,
            systemFacets = setOf(LensId.MEDIA),
            title = "Atomic Habits",
            summary = "A saved book by James Clear.",
            confidence = 0.95f,
            highlights = listOf("Author: James Clear", "Saved for reading")
        )

        val enriched = result.applyTo(item, replaceHeuristicContent = true)

        assertThat(enriched.title).isEqualTo("Atomic Habits")
        assertThat(enriched.summary).isEqualTo("A saved book by James Clear.")
        assertThat(enriched.userClassificationOverride).isEqualTo(Classification.BOOK)
        assertThat(enriched.primaryLensId).isEqualTo(LensId.MEDIA)
        assertThat(enriched.customFields["ai_highlights"]).contains("Author: James Clear")
    }

    @Test
    fun `state machine permits retries but protects terminal states`() {
        assertThat(EnrichmentState.PENDING.canTransitionTo(EnrichmentState.RUNNING)).isTrue()
        assertThat(EnrichmentState.RUNNING.canTransitionTo(EnrichmentState.FAILED_RETRYABLE)).isTrue()
        assertThat(EnrichmentState.RUNNING.canTransitionTo(EnrichmentState.PENDING)).isTrue()
        assertThat(EnrichmentState.RUNNING.canTransitionTo(EnrichmentState.FAILED_FINAL)).isTrue()
        assertThat(EnrichmentState.FAILED_RETRYABLE.canTransitionTo(EnrichmentState.RUNNING)).isTrue()
        assertThat(EnrichmentState.COMPLETE.canTransitionTo(EnrichmentState.RUNNING)).isFalse()
        assertThat(EnrichmentState.SKIPPED_PRIVACY.canTransitionTo(EnrichmentState.RUNNING)).isFalse()
        assertThat(EnrichmentState.FAILED_FINAL.canTransitionTo(EnrichmentState.RUNNING)).isFalse()
    }

    @Test
    fun `media profile extracts metadata and confirmed actions`() {
        val item = VaultItem(
            id = "movie",
            title = "Captured item",
            rawOcrText = "Dune Part Two 2024 https://imdb.com/title/tt15239678",
            aiClassification = Classification.MOVIE,
            enrichmentState = EnrichmentState.RUNNING
        )
        val result = CaptureEnrichmentResult(
            classification = Classification.MOVIE,
            systemFacets = setOf(LensId.MEDIA),
            title = "Dune Part Two",
            summary = "Saved to watch later",
            confidence = 0.9f,
            metadata = mapOf(
                "provider_url" to "https://imdb.com/title/tt15239678",
                "release_year" to "2024",
                "plot" to "Invented plot"
            ),
            supportedActions = setOf(ProactiveAction.OPEN_SOURCE, ProactiveAction.REMIND_LATER)
        )

        val enriched = result.applyTo(item)

        assertThat(enriched.parsedMetadata["release_year"]).isEqualTo("2024")
        assertThat(enriched.parsedMetadata["provider_url"]).contains("imdb.com")
        assertThat(enriched.parsedMetadata["plot"]).isEqualTo("Invented plot")
        assertThat(ProactiveActionResolver.resolve(enriched)).containsAtLeast(
            ProactiveAction.OPEN_SOURCE,
            ProactiveAction.REMIND_LATER
        )
    }

    @Test
    fun `AI metadata merges with heuristic defaults`() {
        val item = VaultItem(
            id = "invoice",
            title = "Captured item",
            rawOcrText = "Acme Invoice Total USD 42",
            parsedMetadata = mapOf("old_heuristic_key" to "42"),
            aiClassification = Classification.INVOICE,
            enrichmentState = EnrichmentState.RUNNING
        )
        val result = CaptureEnrichmentResult(
            classification = Classification.INVOICE,
            systemFacets = setOf(LensId.MONEY),
            title = "Acme Invoice",
            summary = "USD 42 is due",
            confidence = 0.9f,
            metadata = mapOf("total" to "999", "supplier" to "Acme")
        )

        val enriched = result.applyTo(item)

        assertThat(enriched.parsedMetadata["supplier"]).isEqualTo("Acme")
        assertThat(enriched.parsedMetadata["total"]).isEqualTo("999")
        assertThat(enriched.parsedMetadata["old_heuristic_key"]).isEqualTo("42")
    }

    @Test
    fun `user classification override selects the destination profile`() {
        val item = VaultItem(
            id = "override",
            title = "Captured item",
            rawOcrText = "The Martian by Andy Weir",
            aiClassification = Classification.GENERAL_DOCUMENT,
            userClassificationOverride = Classification.BOOK,
            enrichmentState = EnrichmentState.RUNNING
        )
        val result = CaptureEnrichmentResult(
            classification = Classification.GENERAL_DOCUMENT,
            systemFacets = setOf(LensId.BUREAUCRACY),
            title = "The Martian",
            summary = "Book by Andy Weir",
            confidence = 0.8f,
            metadata = mapOf("author" to "Andy Weir")
        )

        val enriched = result.applyTo(item)

        assertThat(enriched.effectiveClassification).isEqualTo(Classification.BOOK)
        assertThat(enriched.lensTags).contains(LensId.MEDIA)
        assertThat(enriched.parsedMetadata["author"]).isEqualTo("Andy Weir")
    }

    @Test
    fun `identity merge hides sensitive numbers and projects normalized expiry`() {
        val item = VaultItem(
            id = "identity",
            title = "Captured item",
            rawOcrText = "National ID 29801011234567 Expiry 2030/06/30",
            aiClassification = Classification.IDENTITY_DOCUMENT,
            enrichmentState = EnrichmentState.RUNNING
        )
        val result = CaptureEnrichmentResult(
            classification = Classification.IDENTITY_DOCUMENT,
            systemFacets = setOf(LensId.BUREAUCRACY),
            title = "National ID 29801011234567",
            summary = "Identity 29801011234567 expires in 2030",
            confidence = 0.9f,
            metadata = mapOf(
                "document_number" to "29801011234567",
                "expiry_date" to "2030/06/30",
                "document_type" to "national_id"
            )
        )

        val enriched = result.applyTo(item)

        assertThat(enriched.title).isEqualTo("Identity document")
        assertThat(enriched.summary).isNull()
        assertThat(enriched.parsedMetadata["document_number"]).isEqualTo("29801011234567")
        assertThat(enriched.parsedMetadata["expiry_date"]).isEqualTo("2030-06-30")
        assertThat(enriched.parsedMetadata["document_type"]).isEqualTo("national_id")
        assertThat(enriched.expiryDate).isNotNull()
        assertThat(enriched.secondaryAlertDate).isNotNull()
    }

    @Test
    fun `typed merge canonicalizes grounded receipt values and rejects ambiguous dates`() {
        val item = VaultItem(
            id = "receipt-normalization",
            title = "Captured item",
            rawOcrText = "Fresh Mart total 1,284.50 USD purchase date 03/04/2027",
            aiClassification = Classification.RECEIPT,
            enrichmentState = EnrichmentState.RUNNING
        )
        val result = CaptureEnrichmentResult(
            classification = Classification.RECEIPT,
            systemFacets = setOf(LensId.MONEY),
            title = "Fresh Mart receipt",
            summary = "Purchase from Fresh Mart",
            confidence = 0.8f,
            metadata = mapOf(
                "total" to "1,284.50",
                "currency" to "USD",
                "purchase_date" to "03/04/2027"
            )
        )

        val enriched = result.applyTo(item)

        assertThat(enriched.parsedMetadata["total"]).isEqualTo("1284.5")
        assertThat(enriched.parsedMetadata["currency"]).isEqualTo("USD")
        assertThat(enriched.parsedMetadata).doesNotContainKey("purchase_date")
    }

    @Test
    fun `open schema retains unlisted grounded metadata keys and dynamic AI tags`() {
        val item = VaultItem(
            id = "gym-session",
            title = "Captured item",
            rawOcrText = "CrossFit Metro Gym Trainer: Coach Sam Plan: Gold Member",
            aiClassification = Classification.GENERAL_DOCUMENT,
            enrichmentState = EnrichmentState.RUNNING
        )
        val result = CaptureEnrichmentResult(
            classification = Classification.GENERAL_DOCUMENT,
            systemFacets = setOf(LensId.BUREAUCRACY),
            title = "Gym Membership",
            summary = "Gold Member plan at CrossFit Metro Gym",
            confidence = 0.95f,
            metadata = mapOf(
                "trainer_name" to "Coach Sam",
                "membership_plan" to "Gold Member"
            ),
            tags = listOf("CrossFit", "Fitness", "Workout")
        )

        val enriched = result.applyTo(item)

        assertThat(enriched.parsedMetadata["trainer_name"]).isEqualTo("Coach Sam")
        assertThat(enriched.parsedMetadata["membership_plan"]).isEqualTo("Gold Member")
        assertThat(enriched.lensTags).containsExactly(LensId.BUREAUCRACY)
        assertThat(enriched.tags).containsAtLeast("CrossFit", "Fitness", "Workout")
    }

    @Test
    fun `parser maps custom classification strings as OTHER and resolves subtype`() {
        val result = CaptureEnrichmentResultParser.parse(
            """
                {"classification":"Workout Routine","lens":"Fitness & Health","title":"Daily Workout","summary":"Push Pull Legs routine","confidence":0.9}
            """.trimIndent()
        )

        assertThat(result?.classification).isEqualTo(Classification.OTHER)
        assertThat(result?.subtype).isEqualTo("Workout Routine")
        assertThat(result?.systemFacets).containsExactly("FITNESS & HEALTH")
    }

    @Test
    fun `product package keeps subtype topics preferred metadata and no hallucinated price`() {
        val result = CaptureEnrichmentResultParser.parse(
            """{"classification":"product_photo","subtype":"tea package","system_facets":["shopping","MONEY"],"title":"Lipton Yellow Label Tea","summary":"Lipton Yellow Label package with 100 tea bags.","highlights":["100 tea bags"],"topics":["tea","groceries","Tea"],"entities":["Lipton"],"tags":["Lipton"],"metadata":{"brand":"Lipton","product_name":"Yellow Label","quantity":"100 tea bags"},"supported_actions":[],"suggestions":["Remember this product for future shopping"],"confidence":1.4}"""
        )

        assertThat(result?.classification).isEqualTo(Classification.PRODUCT_PHOTO)
        assertThat(result?.subtype).isEqualTo("tea package")
        assertThat(result?.systemFacets).containsExactly("SHOPPING", LensId.MONEY).inOrder()
        assertThat(result?.topics).containsExactly("tea", "groceries").inOrder()
        assertThat(result?.entities).containsExactly("Lipton")
        assertThat(result?.metadata).containsEntry("brand", "Lipton")
        assertThat(result?.metadata).containsEntry("quantity", "100 tea bags")
        assertThat(result?.metadata).doesNotContainKey("price")
        assertThat(result?.confidence).isEqualTo(1f)
    }

    @Test
    fun `router package preserves useful custom metadata fields`() {
        val result = CaptureEnrichmentResultParser.parse(
            """{"classification":"PRODUCT_PHOTO","subtype":"Wi-Fi 7 router","system_facets":["MONEY"],"title":"BE9300 Router","summary":"Tri-Band Wi-Fi 7 router with 10GbE.","topics":["networking","Wi-Fi 7"],"tags":["BE9300"],"metadata":{"model":"BE9300","wifi_standard":"Wi-Fi 7","ethernet":"10GbE","bands":"Tri-Band"},"supported_actions":[],"suggestions":[],"confidence":0.93}"""
        )

        assertThat(result?.metadata).containsEntry("model_number", "BE9300")
        assertThat(result?.metadata).containsEntry("wifi_standard", "Wi-Fi 7")
        assertThat(result?.metadata).containsEntry("ethernet", "10GbE")
        assertThat(result?.metadata).containsEntry("bands", "Tri-Band")
    }

    @Test
    fun `unknown capture supports OTHER with no lens`() {
        val result = CaptureEnrichmentResultParser.parse(
            """{"classification":"OTHER","subtype":"3D printer filament spool","system_facets":[],"title":"PLA Filament Spool","summary":"Visible PLA filament spool.","topics":["3D printing","PLA","filament"],"tags":[],"metadata":{},"supported_actions":[],"suggestions":[],"confidence":0.76}"""
        )

        assertThat(result?.classification).isEqualTo(Classification.OTHER)
        assertThat(result?.systemFacets).isEmpty()
        assertThat(result?.topics).containsExactly("3D printing", "PLA", "filament").inOrder()
    }

    @Test
    fun `multi lens actions and semantic suggestions remain separate`() {
        val result = CaptureEnrichmentResultParser.parse(
            """{"classification":"TICKET","subtype":"international train ticket","system_facets":["TRAVEL","MONEY","unknown"],"title":"Eurostar Ticket","summary":"Paris to London ticket.","topics":["rail travel"],"tags":["Eurostar"],"metadata":{"date":"2027-05-02"},"supported_actions":["ADD_TO_CALENDAR","DELETE_VAULT"],"suggestions":["Keep the booking reference available offline"],"confidence":0.9}"""
        )

        assertThat(result?.systemFacets).containsExactly(LensId.TRAVEL, LensId.MONEY, "UNKNOWN").inOrder()
        assertThat(result?.supportedActions).containsExactly(ProactiveAction.ADD_TO_CALENDAR)
        assertThat(result?.suggestions).containsExactly("Keep the booking reference available offline")
    }

    @Test
    fun `metadata aliases deduplicate safely`() {
        val result = CaptureEnrichmentResultParser.parse(
            """{"classification":"PRODUCT_PHOTO","system_facets":[],"title":"Tea","summary":"Tea package","metadata":{"brand":"Lipton","brand_name":"Lipton","manufacturer":"Lipton","company":"Lipton"},"confidence":0.8}"""
        )

        assertThat(result?.metadata).containsExactly("brand", "Lipton")
    }

    @Test
    fun `apply persists semantic fields without overwriting experience or polluting lenses`() {
        val item = VaultItem(
            id = "semantic",
            title = "Captured item",
            aiClassification = Classification.UNKNOWN,
            experienceId = ExperienceId.WISHLIST
        )
        val result = CaptureEnrichmentResult(
            classification = Classification.PRODUCT_PHOTO,
            subtype = "Wi-Fi 7 router",
            systemFacets = setOf(LensId.MONEY),
            title = "BE9300 Router",
            summary = "Tri-Band router",
            confidence = 0.9f,
            topics = listOf("networking"),
            entities = listOf("Bambu Lab"),
            tags = listOf("BE9300"),
            suggestions = listOf("Keep the model number for support"),
            rawClassification = "PRODUCT_PHOTO"
        )

        val enriched = result.applyTo(item, replaceHeuristicContent = true)

        assertThat(enriched.experienceId).isEqualTo(ExperienceId.WISHLIST)
        assertThat(enriched.lensTags).containsExactly(LensId.MONEY)
        assertThat(enriched.subtype).isEqualTo("Wi-Fi 7 router")
        assertThat(enriched.topics).containsExactly("networking")
        assertThat(enriched.entities).containsExactly("Bambu Lab")
        assertThat(enriched.tags).containsExactly("BE9300")
        assertThat(enriched.suggestions).containsExactly("Keep the model number for support")
        assertThat(enriched.aiScratchpad).isNull()
    }
}

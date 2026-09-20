package com.vaultbrain.core.ai.llm

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.shared.model.VaultItem
import org.junit.Test

class CaptureEnrichmentSafetyTest {
    private val result = CaptureEnrichmentResult(
        classification = Classification.BOOK, title = "Book", summary = "A book", confidence = 0.3f,
        systemFacets = setOf(LensId.MEDIA), metadata = mapOf("author" to "Jane")
    )

    @Test fun `sparse model metadata preserves extracted fields and authoritative provenance`() {
        val item = VaultItem(id = "x", title = "Draft", parsedMetadata = mapOf(
            "publisher" to "Local Press", "share_source" to "ANDROID_SHARE",
            "share_received_at" to "1700000000000", "share_url" to "https://example.com/source"
        ))
        val enriched = result.copy(metadata = result.metadata + mapOf(
            "share_source" to "invented", "share_source_package" to "invented"
        )).applyTo(item, true)
        assertThat(enriched.parsedMetadata).containsEntry("publisher", "Local Press")
        assertThat(enriched.parsedMetadata).containsEntry("author", "Jane")
        assertThat(enriched.parsedMetadata).containsEntry("share_source", "ANDROID_SHARE")
        assertThat(enriched.parsedMetadata).containsEntry("share_received_at", "1700000000000")
        assertThat(enriched.parsedMetadata).doesNotContainKey("share_source_package")
    }

    @Test fun `classification correction removes obsolete automatic lens and confidence`() {
        val item = VaultItem(id = "x", title = "Draft", aiClassification = Classification.GENERAL_DOCUMENT,
            aiConfidence = 0.95f, lensTags = setOf(LensId.BUREAUCRACY), primaryLensId = LensId.BUREAUCRACY)
        val enriched = result.applyTo(item, true)
        assertThat(enriched.aiClassification).isEqualTo(Classification.BOOK)
        assertThat(enriched.lensTags).containsExactly(LensId.MEDIA)
        assertThat(enriched.primaryLensId).isEqualTo(LensId.MEDIA)
        assertThat(enriched.aiConfidence).isEqualTo(0.3f)
        assertThat(enriched.needsReview).isTrue()
    }

    @Test fun `classification without confidence is not assigned old confidence`() {
        val item = VaultItem(id = "x", title = "Draft", aiClassification = Classification.RECEIPT, aiConfidence = 0.9f)
        assertThat(result.copy(confidence = null).applyTo(item, true).aiConfidence).isEqualTo(0f)
    }

    @Test fun `incomplete or invalid model classifications do not count as enrichment`() {
        listOf("{}", """{"title":"A title"}""", """{"classification":"BOOK"}""").forEach {
            assertThat(CaptureEnrichmentResultParser.parse(it)).isNull()
        }
        assertThat(CaptureEnrichmentResultParser.parse("""{"classification":"OTHER","title":"Unusual object"}""")).isNotNull()
        assertThat(CaptureEnrichmentResultParser.parse("""{"classification":"SECRET","title":"A title"}""")).isNotNull()
    }

    @Test fun `enrichment cannot overwrite user edited title classification or metadata`() {
        val item = VaultItem(id = "x", title = "My receipt", aiClassification = Classification.RECEIPT,
            parsedMetadata = mapOf("author" to "User value"), userEditedAt = 1L)
        val enriched = result.applyTo(item, true)
        assertThat(enriched.title).isEqualTo(item.title)
        assertThat(enriched.aiClassification).isEqualTo(item.aiClassification)
        assertThat(enriched.parsedMetadata["author"]).isEqualTo("User value")
    }

    @Test fun `long OCR retains header and footer within original budget`() {
        val text = "Receipt header\n" + "filler ".repeat(1000) + "\nTOTAL 42 USD"
        val selected = CaptureEnrichmentPrompt.selectOcrEvidence(text)
        assertThat(selected.length).isAtMost(3500)
        assertThat(selected).startsWith("Receipt header")
        assertThat(selected).endsWith("TOTAL 42 USD")
        assertThat(selected).contains("Middle omitted")
    }

    @Test fun `short OCR is retained verbatim`() {
        val text = "Merchant\nTOTAL 42 USD\nExpiry 2030-06-30"
        assertThat(CaptureEnrichmentPrompt.selectOcrEvidence(text)).isEqualTo(text)
    }

    @Test fun `markdown wrapped JSON responses and classification aliases are parsed successfully`() {
        val markdownOutput = """
            ```json
            {
              "classification": "DOCUMENT",
              "title": "Vehicle Registration Certificate",
              "summary": "Vehicle registration for Ford Explorer",
              "tags": ["vehicle", "registration", "ford"],
              "confidence": 0.92
            }
            ```
        """.trimIndent()
        val parsed = CaptureEnrichmentResultParser.parse(markdownOutput)
        assertThat(parsed).isNotNull()
        assertThat(parsed?.classification).isEqualTo(Classification.GENERAL_DOCUMENT)
        assertThat(parsed?.title).isEqualTo("Vehicle Registration Certificate")
        assertThat(parsed?.tags).containsExactly("vehicle", "registration", "ford")
    }
}

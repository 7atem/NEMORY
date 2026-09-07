package com.vaultbrain.feature.briefing

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.core.common.model.external.ExternalRecordType
import com.vaultbrain.core.common.model.external.ExternalSource
import com.vaultbrain.core.integrations.model.ExternalRecord
import com.vaultbrain.feature.brain.worker.RadarEngine
import org.junit.Test

class DigestEngineTest {

    private val engine = DigestEngine(DeterministicInsightEngine(), RadarEngine())

    private fun appointmentRecord(externalId: String) = ExternalRecord(
        connectorId = "gmail",
        accountId = "test",
        externalId = externalId,
        source = ExternalSource.GMAIL,
        recordType = ExternalRecordType.EMAIL,
        title = "Dentist appointment",
        createdAt = 0L
    )

    @Test
    fun `near-duplicate records collapse into one insight card`() {
        val digest = engine.generateDigest(
            vaultItems = emptyList(),
            externalRecords = listOf(
                appointmentRecord("a"),
                appointmentRecord("b"),
                appointmentRecord("c")
            )
        )
        assertThat(digest.items).hasSize(1)
        assertThat((digest.items.first() as TodayInsight.ActionableExternalInsight).insightType).isEqualTo("APPOINTMENT")
    }

    @Test
    fun `distinct record kinds keep separate cards`() {
        val digest = engine.generateDigest(
            vaultItems = emptyList(),
            externalRecords = listOf(
                appointmentRecord("a"),
                appointmentRecord("b").copy(
                    title = "invoice",
                    payload = mapOf("total" to "EGP 890")
                )
            )
        )
        assertThat(digest.items.map { (it as TodayInsight.ActionableExternalInsight).insightType }).containsExactly("BILL", "APPOINTMENT")
    }
}

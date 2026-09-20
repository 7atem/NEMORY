package com.vaultbrain.feature.brain.worker

import com.google.common.truth.Truth.assertThat
import com.vaultbrain.shared.model.external.ExternalRecordType
import com.vaultbrain.shared.model.external.ExternalSource
import com.vaultbrain.core.integrations.model.ExternalRecord
import org.junit.Test

class RadarEngineTest {

    private val engine = RadarEngine()

    private fun record(title: String?, payload: Map<String, String> = emptyMap()) = ExternalRecord(
        connectorId = "gmail",
        accountId = "test",
        externalId = "ext-1",
        source = ExternalSource.GMAIL,
        recordType = ExternalRecordType.EMAIL,
        title = title,
        payload = payload,
        createdAt = 0L
    )

    @Test
    fun `appointment without location has a readable fallback`() {
        val insight = engine.evaluate(record("Dentist appointment"))
        assertThat(insight).isNotNull()
        assertThat(insight!!.body).isEqualTo("You have an appointment scheduled.")
        assertThat(insight.body).doesNotContain("your appointment scheduled")
    }

    @Test
    fun `appointment with location includes it`() {
        val insight = engine.evaluate(record("Dentist appointment", mapOf("location" to "Smile Clinic")))
        assertThat(insight!!.body).isEqualTo("You have an appointment at Smile Clinic scheduled.")
    }

    @Test
    fun `flight without destination or time has a readable fallback`() {
        val insight = engine.evaluate(record("Your flight booking"))
        assertThat(insight!!.body).isEqualTo("You have an upcoming flight.")
    }

    @Test
    fun `bill without merchant or total has a readable fallback`() {
        val insight = engine.evaluate(record("invoice", mapOf("total" to "EGP 890")))
        assertThat(insight!!.body).isEqualTo("You received an invoice for EGP 890.")
    }

    @Test
    fun `unrelated record produces no insight`() {
        assertThat(engine.evaluate(record("Weekly newsletter"))).isNull()
    }
}

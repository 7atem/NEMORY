package com.vaultbrain.core.integrations.connector

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class ConnectorRegistryTest {

    private val registry = ConnectorRegistry()

    @Test
    fun `register and get connector`() {
        val connector = mockConnector("calendar")

        registry.register(connector)

        assertThat(registry.get("calendar")).isSameInstanceAs(connector)
        assertThat(registry.all()).containsExactly(connector)
    }

    @Test
    fun `unregister removes connector`() {
        val connector = mockConnector("gmail")
        registry.register(connector)

        registry.unregister("gmail")

        assertThat(registry.get("gmail")).isNull()
        assertThat(registry.all()).isEmpty()
    }

    @Test
    fun `isEmpty reflects registry state`() {
        assertThat(registry.isEmpty()).isTrue()

        registry.register(mockConnector("tasks"))

        assertThat(registry.isEmpty()).isFalse()
    }

    private fun mockConnector(id: String): ExternalConnector {
        return mockk<ExternalConnector>().apply {
            every { connectorId } returns id
        }
    }
}

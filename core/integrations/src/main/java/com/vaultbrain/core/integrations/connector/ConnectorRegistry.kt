package com.vaultbrain.core.integrations.connector

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central registry for every [ExternalConnector] available in the app.
 *
 * Real connectors are registered during app startup by feature/sync modules so
 * the core integrations layer stays dependency-free.
 */
@Singleton
class ConnectorRegistry @Inject constructor() {

    private val connectors = mutableMapOf<String, ExternalConnector>()

    fun register(connector: ExternalConnector) {
        connectors[connector.connectorId] = connector
    }

    fun unregister(connectorId: String) {
        connectors.remove(connectorId)
    }

    fun get(connectorId: String): ExternalConnector? = connectors[connectorId]

    fun all(): List<ExternalConnector> = connectors.values.toList()

    fun isEmpty(): Boolean = connectors.isEmpty()
}

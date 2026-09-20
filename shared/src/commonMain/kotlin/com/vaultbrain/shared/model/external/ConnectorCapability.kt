package com.vaultbrain.shared.model.external

/**
 * Operations a connector may advertise. Consumers must check [ExternalConnector.capabilities]
 * before invoking the corresponding methods.
 */
enum class ConnectorCapability {
    READ,
    SEARCH,
    IMPORT,
    CREATE,
    UPDATE,
    OPEN_ORIGINAL,
    BACKGROUND_SYNC
}

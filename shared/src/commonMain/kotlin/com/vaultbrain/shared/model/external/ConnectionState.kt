package com.vaultbrain.shared.model.external

/**
 * Runtime state of a connector/account pair.
 */
enum class ConnectionState {
    UNAVAILABLE,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LIMITED,
    PERMISSION_REVOKED,
    ERROR
}

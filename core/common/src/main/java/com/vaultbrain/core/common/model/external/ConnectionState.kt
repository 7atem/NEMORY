package com.vaultbrain.core.common.model.external

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

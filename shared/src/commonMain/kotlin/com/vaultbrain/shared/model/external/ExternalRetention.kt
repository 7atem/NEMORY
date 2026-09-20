package com.vaultbrain.shared.model.external

/**
 * How long an external record should live in the local database.
 *
 * - [EPHEMERAL]: transient signal, not stored after it is consumed.
 * - [INDEXED_REFERENCE]: enough metadata to match against vault items and surface at the right moment.
 * - [SAVED_COPY]: the record itself is copied into the vault (user-initiated import only).
 */
enum class ExternalRetention {
    EPHEMERAL,
    INDEXED_REFERENCE,
    SAVED_COPY
}

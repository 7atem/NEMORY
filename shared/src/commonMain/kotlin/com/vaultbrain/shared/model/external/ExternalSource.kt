package com.vaultbrain.shared.model.external

/**
 * Provenance of an external contextual record. These values identify where a record came from,
 * not how the user organizes their vault.
 */
enum class ExternalSource {
    ANDROID_SHARE,
    CALENDAR,
    VAULT_REMINDER,
    GOOGLE_TASKS,
    GMAIL,
    CONTACTS,
    HEALTH_CONNECT,
    NOTIFICATION
}

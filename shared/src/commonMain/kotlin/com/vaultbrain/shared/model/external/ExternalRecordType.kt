package com.vaultbrain.shared.model.external

/**
 * Broad category of an external contextual record. Kept intentionally coarse so the UI stays
 * organized around user intent ("coming up", "needs attention") instead of source names.
 */
enum class ExternalRecordType {
    EVENT,
    EMAIL,
    TASK,
    CONTACT,
    HEALTH_SAMPLE,
    NOTIFICATION,
    SHARED_TEXT,
    SHARED_URL,
    SHARED_FILE,
    OTHER
}

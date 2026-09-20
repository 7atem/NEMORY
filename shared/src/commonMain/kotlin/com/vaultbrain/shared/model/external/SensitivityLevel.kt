package com.vaultbrain.shared.model.external

/**
 * Sensitivity tier used for privacy gating and redaction decisions.
 */
enum class SensitivityLevel {
    NORMAL,
    PERSONAL,
    SENSITIVE,
    HIGHLY_SENSITIVE
}

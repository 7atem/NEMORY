package com.vaultbrain.core.common.model.external

/**
 * Sensitivity tier used for privacy gating and redaction decisions.
 */
enum class SensitivityLevel {
    NORMAL,
    PERSONAL,
    SENSITIVE,
    HIGHLY_SENSITIVE
}

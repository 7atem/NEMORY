package com.vaultbrain.feature.capture.share

/**
 * Outcome of validating an incoming share before it is shown to the user.
 */
sealed class ShareValidationResult {
    data class Valid(val share: IncomingShare) : ShareValidationResult()
    data class Invalid(val reason: String) : ShareValidationResult()
}

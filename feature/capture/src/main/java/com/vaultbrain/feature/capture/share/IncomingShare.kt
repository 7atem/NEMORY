package com.vaultbrain.feature.capture.share

import android.net.Uri

/**
 * Normalized representation of content shared into VaultBrain from another app.
 */
data class IncomingShare(
    val mimeType: String? = null,
    val subject: String? = null,
    val text: String? = null,
    val url: String? = null,
    val uris: List<Uri> = emptyList(),
    val sourcePackage: String? = null,
    val receivedAt: Long = System.currentTimeMillis()
) {
    /** True when there is no usable content. */
    val isEmpty: Boolean get() = text.isNullOrBlank() && uris.isEmpty()

    /** Human-readable description of the share payload. */
    val summary: String
        get() = when {
            !text.isNullOrBlank() && uris.isEmpty() -> text.take(120)
            uris.isNotEmpty() -> "${uris.size} attachment(s)"
            else -> "Shared content"
        }
}

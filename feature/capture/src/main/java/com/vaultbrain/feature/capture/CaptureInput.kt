package com.vaultbrain.feature.capture

import android.net.Uri
import com.vaultbrain.core.common.model.SourceType

/**
 * Input passed to the capture flow. Either a list of shared URIs, pasted text,
 * or an empty input that triggers the camera preview.
 */
data class CaptureInput(
    val initialUris: List<Uri> = emptyList(),
    val initialText: String? = null,
    val sourceType: SourceType = SourceType.CAMERA,
    val preferredLensTags: Set<String> = emptySet()
)

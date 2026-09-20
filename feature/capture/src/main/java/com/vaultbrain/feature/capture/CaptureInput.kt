package com.vaultbrain.feature.capture

import android.net.Uri
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.VaultItem

/**
 * Input passed to the capture flow. Either a list of shared URIs, pasted text,
 * or an empty input that triggers the camera preview.
 */
data class CaptureInput(
    val initialUris: List<Uri> = emptyList(),
    val initialText: String? = null,
    val sourceType: SourceType = SourceType.CAMERA,
    val preferredLensTags: Set<String> = emptySet(),
    /** Key/value provenance to merge into [VaultItem.parsedMetadata] (e.g. share source). */
    val provenanceMetadata: Map<String, String> = emptyMap(),
    /** When true, skip the optional LLM enrichment step. */
    val skipLlmEnrichment: Boolean = false,
    /** If set, the saved item is automatically added to this collection. */
    val targetCollectionId: String? = null
)

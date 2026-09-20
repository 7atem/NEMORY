package com.vaultbrain.core.common.ui

import com.vaultbrain.shared.model.VaultItem

enum class VaultCardDateKind { SAVED, EXPIRES, DUE, EVENT }

data class VaultCardPresentation(
    val keyFact: String?,
    val summary: String?,
    val dateMillis: Long,
    val dateKind: VaultCardDateKind,
    val category: String
)

/** Deterministic, category-aware content for compact vault cards. */
object VaultItemPresenter {
    fun present(item: VaultItem): VaultCardPresentation {
        val metadata = item.parsedMetadata
        val keyFact = (topAiHighlight(item) ?: topMetadataEntry(metadata))?.takeIf(String::isNotBlank)

        val summary = item.summary?.takeIf(String::isNotBlank)

        val dueDate = metadata["due_date"]?.trim()?.takeIf(String::isNotBlank)
        val dateKind = when {
            dueDate != null && item.expiryDate != null -> VaultCardDateKind.DUE
            item.expiryDate != null -> VaultCardDateKind.EXPIRES
            item.secondaryAlertDate != null -> VaultCardDateKind.EVENT
            else -> VaultCardDateKind.SAVED
        }
        val dateMillis = item.expiryDate ?: item.secondaryAlertDate ?: item.createdAt
        val category = buildCategoryLabel(item)
        return VaultCardPresentation(keyFact, summary, dateMillis, dateKind, category)
    }

    private fun buildCategoryLabel(item: VaultItem): String =
        item.subtype?.takeIf(String::isNotBlank)
            ?: item.topics.firstOrNull(String::isNotBlank)
            ?: item.entities.firstOrNull(String::isNotBlank)
            ?: item.tags.firstOrNull(String::isNotBlank)
            ?: "Saved item"

    private fun topAiHighlight(item: VaultItem): String? =
        item.customFields["ai_highlights"]
            ?.lineSequence()
            ?.firstOrNull(String::isNotBlank)
            ?.removePrefix("• ")
            ?.trim()

    private fun topMetadataEntry(metadata: Map<String, String>): String? =
        metadata.entries
            .firstOrNull { it.key != "media_mime_type" && it.key != "barcodes" && it.value.isNotBlank() }
            ?.let { (key, value) -> "${MetadataFields.prettifyKey(key)}: $value" }

}

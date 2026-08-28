package com.vaultbrain.core.ai.heuristics.experience

import com.vaultbrain.core.common.model.VaultItem

/**
 * Extracts experience-specific metadata from a partially processed item.
 *
 * Implementations are pure functions: they receive the current [VaultItem]
 * (which already contains OCR text and generic metadata) and return a map of
 * additional fields that make the experience useful.
 */
interface ExperienceParser {
    val experienceId: String
    fun canApply(item: VaultItem): Boolean
    fun extract(item: VaultItem): Map<String, String>
}

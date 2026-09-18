package com.vaultbrain.core.common.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.vaultbrain.shared.domain.LensId

/**
 * A personal experience is a user intent layered on top of a lens.
 *
 * Each experience knows:
 * - Its canonical id and human-readable label/icon.
 * - Which lens it belongs to for grouping and navigation.
 * - How to extract meaningful metadata from OCR text (via its parser, supplied at runtime).
 */
data class PersonalExperience(
    val id: String,
    /** Owning lens, or null for lens-less experiences (e.g. the GENERIC "Just save" fallback). */
    val lensId: String?,
    @StringRes val titleRes: Int,
    @StringRes val hintRes: Int? = null,
    @DrawableRes val iconRes: Int = 0,
    val keywords: Set<String> = emptySet(),
    val confidenceWeight: Float = 1f,
    /**
     * Parent primary experience id, or null for primary experiences.
     * Sub-experiences keep full keyword/parser support but are presented in the
     * picker through their primary parent.
     */
    val parentId: String? = null
) {
    init {
        require(id in ExperienceId.ALL_EXPERIENCES) { "Unknown experience id: $id" }
        require(lensId == null || lensId in LensId.ALL_LENSES) { "Unknown lens id: $lensId" }
    }
}

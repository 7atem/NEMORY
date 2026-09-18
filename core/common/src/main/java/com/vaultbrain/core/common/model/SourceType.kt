package com.vaultbrain.core.common.model

import kotlinx.serialization.Serializable

/**
 * Represents how an item originally entered the vault.
 */
@Serializable
enum class SourceType {
    CAMERA,
    GALLERY,
    SCREENSHOT_SHARE,
    APP_SHARE,
    TEXT_PASTE,
    VOICE,
    MANUAL
}

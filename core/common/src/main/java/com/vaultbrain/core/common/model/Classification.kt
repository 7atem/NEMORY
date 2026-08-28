package com.vaultbrain.core.common.model

import kotlinx.serialization.Serializable

/**
 * High-level classification for a dumped item.
 */
@Serializable
enum class Classification {
    RECEIPT,
    PRESCRIPTION,
    LAB_RESULT,
    PASSPORT,
    IDENTITY_DOCUMENT,
    TICKET,
    HOTEL,
    INVOICE,
    WARRANTY_CARD,
    BUSINESS_CARD,
    PRODUCT_PHOTO,
    MENU_PHOTO,
    SERIAL_PLATE,
    GENERAL_DOCUMENT,
    WEB_ARTICLE,
    MOVIE,
    TV_SERIES,
    BOOK,
    SCENE_PHOTO,
    MEME_JUNK,
    /** Supported evidence that does not reasonably fit another broad category. */
    OTHER,
    /** Internal low-confidence state used before semantic enrichment. */
    UNKNOWN
}

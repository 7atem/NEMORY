package com.vaultbrain.core.integrations.model

import com.vaultbrain.core.common.model.external.ExternalRecordType
import com.vaultbrain.core.common.model.external.ExternalSource

/**
 * Request to assemble personal context around a vault item, a free-text question,
 * or a future point in time.
 */
data class ContextQuery(
    /** Optional vault item id; engines may bias toward records that mention it. */
    val itemId: String? = null,

    /** Optional free-text hint (e.g. a user question to Brain). */
    val text: String? = null,

    /** Filter by record types; null means no type filter. */
    val recordTypes: Set<ExternalRecordType>? = null,

    /** Filter by sources; null means no source filter. */
    val sources: Set<ExternalSource>? = null,

    /**
     * How far back/forward to look, in milliseconds.
     * Default is 14 days around the current moment.
     */
    val timeWindowMs: Long = DEFAULT_TIME_WINDOW_MS,

    /** Maximum number of records to return. */
    val limit: Int = DEFAULT_LIMIT
) {
    companion object {
        private const val DEFAULT_TIME_WINDOW_MS = 14L * 24 * 60 * 60 * 1000
        private const val DEFAULT_LIMIT = 10
    }
}

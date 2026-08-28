package com.vaultbrain.core.common.util

/**
 * Converts snake_case or SCREAMING_SNAKE_CASE to Title Case with spaces.
 */
fun String.humanize(): String = lowercase()
    .split('_')
    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }

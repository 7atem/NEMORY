package com.vaultbrain.shared.ui.sample

import com.vaultbrain.shared.intelligence.KeywordDictionary

/**
 * Small bridge that exercises the shared [KeywordDictionary] from the iOS sample.
 *
 * Returns a human-readable summary of detected lens/sub-module pairs so the SwiftUI
 * host can display it without needing to understand Kotlin collections interop.
 */
object SampleKeywordDetector {
    fun detect(text: String): String {
        val hierarchy = KeywordDictionary.detectHierarchy(text)
        if (hierarchy.isEmpty()) return "No lens detected"
        return hierarchy.entries.joinToString("\n") { (lens, submodule) ->
            "$lens → $submodule"
        }
    }
}

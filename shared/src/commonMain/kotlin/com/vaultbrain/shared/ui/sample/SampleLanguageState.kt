package com.vaultbrain.shared.ui.sample

import androidx.compose.runtime.mutableStateOf

/**
 * Mutable language state for the iOS bilingual sample.
 *
 * Toggling [isArabic] recomposes the hosted Compose tree in place. This avoids the
 * UIViewController destroy/recreate path (`.id(isArabic)` in SwiftUI), which crashes
 * Kotlin/Native under XCUITest while the accessibility daemon queries the view tree.
 */
object SampleLanguageState {
    val isArabic = mutableStateOf(false)
}

/** Updates the sample language; safe to call from Swift on the main thread. */
fun setSampleLanguage(isArabic: Boolean) {
    SampleLanguageState.isArabic.value = isArabic
}

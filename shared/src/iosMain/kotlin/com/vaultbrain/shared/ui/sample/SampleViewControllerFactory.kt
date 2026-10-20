package com.vaultbrain.shared.ui.sample

import platform.UIKit.UIViewController

/**
 * Factory wrapper so SwiftUI can create the shared Compose sample view controller with a
 * stable Objective-C class name.
 *
 * Language changes are applied with [updateLanguage]: the composition recomposes in
 * place instead of recreating the view controller, which crashes Kotlin/Native when
 * XCUITest queries the accessibility tree during teardown.
 */
class SampleViewControllerFactory {
    fun create(isArabic: Boolean = false): UIViewController = MainViewController(isArabic)

    /** Recomposes the hosted Compose tree in place with the new language. */
    fun updateLanguage(isArabic: Boolean) = setSampleLanguage(isArabic)
}

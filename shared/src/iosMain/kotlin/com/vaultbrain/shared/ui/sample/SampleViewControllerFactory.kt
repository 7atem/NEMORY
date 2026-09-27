package com.vaultbrain.shared.ui.sample

import platform.UIKit.UIViewController

/**
 * Factory wrapper so SwiftUI can create the shared Compose sample view controller with a
 * stable Objective-C class name.
 */
class SampleViewControllerFactory {
    fun create(isArabic: Boolean = false): UIViewController = MainViewController(isArabic)
}

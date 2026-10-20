package com.vaultbrain.shared.ui.sample

import platform.UIKit.UIViewController

/**
 * Factory wrapper so SwiftUI can create the shared Compose sample view controller with a
 * stable Objective-C class name.
 *
 * The created controller manages its own language state internally; SwiftUI must not
 * recreate or update it — both paths crash or deadlock Kotlin/Native under XCUITest.
 */
class SampleViewControllerFactory {
    fun create(): UIViewController = MainViewController()
}

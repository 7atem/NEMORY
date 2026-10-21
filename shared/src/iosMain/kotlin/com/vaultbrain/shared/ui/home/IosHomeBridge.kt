package com.vaultbrain.shared.ui.home

import com.vaultbrain.shared.haptics.IosHapticFeedbackManager
import com.vaultbrain.shared.notifications.IosVaultNotificationManager
import com.vaultbrain.shared.security.AuthResult
import com.vaultbrain.shared.security.IosAuthManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Synchronous bridge exposing the shared Kotlin layer to the SwiftUI shell.
 *
 * The SwiftUI home/demos call these plain functions instead of dealing with
 * Kotlin suspend-function interop. Callbacks are delivered on the main thread.
 */
object IosHomeBridge {
    private val scope = MainScope()
    private val haptics = IosHapticFeedbackManager()
    private val notifications = IosVaultNotificationManager()
    private val auth = IosAuthManager()

    // region Haptics

    fun impactLight() = haptics.impactLight()
    fun impactMedium() = haptics.impactMedium()
    fun impactHeavy() = haptics.impactHeavy()
    fun notifySuccess() = haptics.notifySuccess()
    fun notifyError() = haptics.notifyError()

    // endregion

    // region Notifications

    /** Requests notification permission; calls back with the granted state. */
    fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        scope.launch {
            val granted = notifications.requestPermission()
            onResult(granted)
        }
    }

    /** Schedules a local notification one second from now via the shared layer. */
    fun scheduleDemoNotification() {
        notifications.showNotification(
            id = DEMO_NOTIFICATION_ID,
            title = "Nemory",
            body = "Hello from the shared Kotlin layer"
        )
    }

    fun cancelDemoNotification() {
        notifications.cancelNotification(DEMO_NOTIFICATION_ID)
    }

    // endregion

    // region Authentication

    /** True if Face ID / Touch ID is enrolled and available on this device. */
    fun canAuthenticate(): Boolean = auth.canAuthenticate()

    /** Triggers the biometric prompt; calls back with the AuthResult name. */
    fun authenticate(onResult: (String) -> Unit) {
        scope.launch {
            val resultName = when (auth.authenticate("Demo the shared authentication layer")) {
                AuthResult.SUCCESS -> "SUCCESS"
                AuthResult.ERROR -> "ERROR"
                AuthResult.CANCELLED -> "CANCELLED"
                AuthResult.NOT_AVAILABLE -> "NOT_AVAILABLE"
            }
            onResult(resultName)
        }
    }

    // endregion

    private const val DEMO_NOTIFICATION_ID = 42
}

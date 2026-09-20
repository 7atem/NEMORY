package com.vaultbrain.shared.notifications

import platform.UserNotifications.*
import platform.Foundation.NSError
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
class IosVaultNotificationManager : VaultNotificationManager {

    override suspend fun requestPermission(): Boolean = suspendCoroutine { continuation ->
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        
        center.requestAuthorizationWithOptions(options) { granted, error ->
            if (error != null) {
                continuation.resume(false)
            } else {
                continuation.resume(granted)
            }
        }
    }

    override fun showNotification(id: Int, title: String, body: String) {
        val content = UNMutableNotificationContent()
        content.setTitle(title)
        content.setBody(body)
        content.setSound(UNNotificationSound.defaultSound)
        
        // Use a time interval trigger (1 second from now) to show it immediately
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(id.toString(), content, trigger)
        
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { error ->
            if (error != null) {
                println("Failed to show notification: ${error.localizedDescription}")
            }
        }
    }

    override fun cancelNotification(id: Int) {
        UNUserNotificationCenter.currentNotificationCenter().removePendingNotificationRequestsWithIdentifiers(listOf(id.toString()))
        UNUserNotificationCenter.currentNotificationCenter().removeDeliveredNotificationsWithIdentifiers(listOf(id.toString()))
    }
}

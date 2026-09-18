package com.vaultbrain.core.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles inline notification action button intents:
 *  - ACTION_SNOOZE: reschedules the notification +24 hours.
 *  - ACTION_DISMISS: cancels the notification silently.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alertManager: UnifiedAlertManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_SNOOZE && action != ACTION_DISMISS) return
        val notificationId = intent.getStringExtra(EXTRA_ENTITY_ID)?.takeIf { it.isNotBlank() } ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_SNOOZE -> alertManager.snoozeNotification(notificationId)
                    ACTION_DISMISS -> alertManager.dismissNotification(notificationId)
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(notifId)
            } catch (error: Exception) {
                android.util.Log.e("NotificationAction", "Could not update alert", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.nemory.app.action.SNOOZE"
        const val ACTION_DISMISS = "com.nemory.app.action.DISMISS"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_ENTITY_ID = "extra_entity_id"
    }
}

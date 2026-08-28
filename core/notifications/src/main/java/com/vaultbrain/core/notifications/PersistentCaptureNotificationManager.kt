package com.vaultbrain.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PersistentCaptureNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun showPersistentNotification() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
                ?: return

            val channelId = "persistent_capture"
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "Quick Capture",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent notification for quick capture actions"
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intentClass = Class.forName("com.vaultbrain.app.MainActivity")

            fun createPendingIntent(source: String?): PendingIntent {
                val intent = Intent(context, intentClass).apply {
                    action = "com.nemory.app.ACTION_CAPTURE"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (source != null) {
                        putExtra("EXTRA_CAPTURE_SOURCE", source)
                    }
                }
                return PendingIntent.getActivity(
                    context,
                    source?.hashCode() ?: 0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("VaultBrain Quick Dump")
                .setContentText("Tap to capture directly to your vault")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(createPendingIntent(null))
                .addAction(
                    android.R.drawable.ic_btn_speak_now,
                    "Voice",
                    createPendingIntent("voice")
                )
                .addAction(
                    android.R.drawable.ic_menu_edit,
                    "Text",
                    createPendingIntent("text")
                )
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    fun hidePersistentNotification() {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val NOTIFICATION_ID = 4242
    }
}

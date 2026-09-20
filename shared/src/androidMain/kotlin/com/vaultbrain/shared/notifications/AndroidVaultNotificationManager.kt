package com.vaultbrain.shared.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidVaultNotificationManager(
    private val context: Context
) : VaultNotificationManager {

    private val notificationManager = NotificationManagerCompat.from(context)
    private val channelId = "vault_notifications"

    override suspend fun requestPermission(): Boolean = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            // In a real Android Compose app, you would use rememberPermissionState
            // Here we assume it's either granted or we fall back.
            isGranted
        } else {
            true // Granted by default below Android 13
        }
    }

    override fun showNotification(id: Int, title: String, body: String) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                notificationManager.notify(id, builder.build())
            }
        } catch (e: SecurityException) {
            // Permission not granted
        }
    }

    override fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }
}

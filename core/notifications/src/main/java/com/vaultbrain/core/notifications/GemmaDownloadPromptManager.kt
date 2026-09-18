package com.vaultbrain.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences bookkeeping for the one-time Gemma model download prompt.
 *
 * The prompt may be shown once, re-shown once after [RESHOW_DELAY_MS] if dismissed, and is
 * never shown again after the second dismiss. The counters are intentionally never reset.
 */
@Singleton
class GemmaDownloadPromptStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun shouldShow(now: Long = System.currentTimeMillis()): Boolean = shouldShowPrompt(
        dismissCount = preferences.getInt(KEY_DISMISS_COUNT, 0),
        lastShownAt = preferences.getLong(KEY_LAST_SHOWN_AT, 0L),
        now = now
    )

    fun recordShown(now: Long = System.currentTimeMillis()) {
        preferences.edit().putLong(KEY_LAST_SHOWN_AT, now).apply()
    }

    fun recordDismiss(now: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putInt(KEY_DISMISS_COUNT, preferences.getInt(KEY_DISMISS_COUNT, 0) + 1)
            .putLong(KEY_LAST_SHOWN_AT, now)
            .apply()
    }

    companion object {
        const val PREFERENCES_NAME = "nemory_gemma_download_prompt"
        const val KEY_DISMISS_COUNT = "dismiss_count"
        const val KEY_LAST_SHOWN_AT = "last_shown_at"
        const val RESHOW_DELAY_MS = 7L * 24 * 60 * 60 * 1000

        internal fun shouldShowPrompt(dismissCount: Int, lastShownAt: Long, now: Long): Boolean = when {
            dismissCount >= 2 -> false
            dismissCount == 1 -> now - lastShownAt >= RESHOW_DELAY_MS
            else -> true
        }
    }
}

/**
 * Posts the low-priority "download the on-device model" notification.
 *
 * Tap-through opens Settings; the Download action deep-links into MainActivity, which enqueues
 * the `gemma_model_download` work directly. Swiping the notification away counts as a dismiss
 * via [GemmaDownloadPromptDismissReceiver].
 */
class GemmaDownloadPromptManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: GemmaDownloadPromptStore
) {
    /** Shows the prompt when [GemmaDownloadPromptStore.shouldShow] allows it. */
    fun showPromptIfEligible() {
        runCatching {
            if (!store.shouldShow()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
                ?: return
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.gemma_prompt_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = context.getString(R.string.gemma_prompt_channel_description)
                        setShowBadge(false)
                    }
                )
            }

            val intentClass = Class.forName("com.vaultbrain.app.MainActivity")

            fun activityIntent(action: String, requestCode: Int): PendingIntent {
                val intent = Intent(context, intentClass).apply {
                    this.action = action
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                return PendingIntent.getActivity(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val dismissIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_DISMISS,
                Intent(context, GemmaDownloadPromptDismissReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getString(R.string.gemma_prompt_title))
                .setContentText(context.getString(R.string.gemma_prompt_body))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.gemma_prompt_body))
                )
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(activityIntent(ACTION_OPEN_SETTINGS, REQUEST_SETTINGS))
                .setDeleteIntent(dismissIntent)
                .addAction(
                    android.R.drawable.stat_sys_download_done,
                    context.getString(R.string.gemma_prompt_download),
                    activityIntent(ACTION_DOWNLOAD_MODEL, REQUEST_DOWNLOAD)
                )
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
            store.recordShown()
        }
    }

    fun dismissPrompt() {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val ACTION_OPEN_SETTINGS = "com.nemory.app.ACTION_OPEN_SETTINGS"
        const val ACTION_DOWNLOAD_MODEL = "com.nemory.app.ACTION_DOWNLOAD_GEMMA_MODEL"

        private const val CHANNEL_ID = "gemma_model_prompt"
        private const val NOTIFICATION_ID = 4245
        private const val REQUEST_SETTINGS = 0
        private const val REQUEST_DOWNLOAD = 1
        private const val REQUEST_DISMISS = 2
    }
}

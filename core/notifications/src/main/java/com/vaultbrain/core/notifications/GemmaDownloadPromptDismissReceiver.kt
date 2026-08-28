package com.vaultbrain.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Records a swipe-away dismiss of the Gemma download prompt notification. */
class GemmaDownloadPromptDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GemmaDownloadPromptStore(context.applicationContext).recordDismiss()
    }
}

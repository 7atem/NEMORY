package com.vaultbrain.app.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.vaultbrain.app.MainActivity
import com.vaultbrain.app.R

/** Public home-screen surface: shortcuts only, never vault content or counts. */
class VaultBrainWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Legacy state clearing is handled by WidgetUpdateWorker (WorkManager), NOT here.
        // Calling clearLegacyState() inside provideGlance() mutates DataStore, which triggers
        // state invalidation and re-runs provideGlance() — creating an infinite loop.
        provideContent {
            GlanceTheme {
                Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(12.dp)) {
                    Text(context.getString(R.string.app_name), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp))
                    Shortcut(context, R.string.nav_capture, MainActivity.ACTION_CAPTURE)
                    Shortcut(context, R.string.nav_vault, MainActivity.ACTION_VIEW_VAULT)
                    Shortcut(context, R.string.nav_brain, MainActivity.ACTION_ASK_NEMORY)
                }
            }
        }
    }

    @Composable
    private fun Shortcut(context: Context, label: Int, action: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        Text(context.getString(label),
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(intent)).padding(10.dp),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
    }
}

class VaultBrainWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VaultBrainWidget()
}

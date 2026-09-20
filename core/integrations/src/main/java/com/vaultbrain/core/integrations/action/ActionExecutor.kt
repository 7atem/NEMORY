package com.vaultbrain.core.integrations.action

import com.vaultbrain.core.integrations.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import com.vaultbrain.shared.model.ProactiveAction
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.shared.model.VaultReminder
import com.vaultbrain.core.notifications.VaultReminderManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ActionExecutionResult {
    data class Success(val message: String) : ActionExecutionResult
    data class IntentLaunched(val intent: Intent) : ActionExecutionResult
    data class Error(val reason: String) : ActionExecutionResult
}

@Singleton
class ActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderManager: VaultReminderManager
) {
    suspend fun execute(action: ProactiveAction, item: VaultItem): ActionExecutionResult = withContext(Dispatchers.Main) {
        if (com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value) return@withContext ActionExecutionResult.Error(context.getString(R.string.action_unavailable))
        val metadata = item.parsedMetadata
        when (action) {
            ProactiveAction.COPY_TOTAL -> {
                val value = metadata["total"] ?: metadata["amount"] ?: metadata["price"] ?: metadata["amount_due"]
                if (value.isNullOrBlank()) {
                    return@withContext ActionExecutionResult.Error(context.getString(R.string.action_no_total))
                }
                copyToClipboard(context.getString(R.string.action_total), value)
                ActionExecutionResult.Success(context.getString(R.string.action_copied))
            }

            ProactiveAction.ADD_TO_CALENDAR -> {
                val title = item.title.ifBlank { context.getString(R.string.action_event) }
                val location = metadata["location"] ?: metadata["merchant"] ?: ""
                val dueMs = item.expiryDate ?: item.secondaryAlertDate ?: (System.currentTimeMillis() + 86400000L)
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, title)
                    putExtra(CalendarContract.Events.DESCRIPTION, item.summary ?: "")
                    putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dueMs)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, dueMs + 3600000L)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    ActionExecutionResult.IntentLaunched(intent)
                } catch (e: Exception) {
                    ActionExecutionResult.Error(context.getString(R.string.action_calendar_error))
                }
            }

            ProactiveAction.REFILL_REMINDER,
            ProactiveAction.REMIND_LATER,
            ProactiveAction.REVIEW_EXPIRY -> {
                val dueMs = item.expiryDate ?: item.secondaryAlertDate ?: (System.currentTimeMillis() + 86400000L * 2)
                val reminder = VaultReminder(
                    id = UUID.randomUUID().toString(),
                    title = item.title,
                    dueAt = dueMs,
                    vaultItemId = item.id
                )
                val created = reminderManager.create(reminder)
                if (created) {
                    Toast.makeText(context, context.getString(R.string.action_reminder_saved), Toast.LENGTH_SHORT).show()
                    ActionExecutionResult.Success(context.getString(R.string.action_reminder_saved))
                } else {
                    ActionExecutionResult.Error(context.getString(R.string.action_reminder_error))
                }
            }

            ProactiveAction.ADD_CONTACT -> {
                val name = metadata["contact_name"] ?: metadata["merchant"] ?: item.title
                val phone = metadata["phone"]
                val email = metadata["email"]
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    type = ContactsContract.Contacts.CONTENT_TYPE
                    putExtra(ContactsContract.Intents.Insert.NAME, name)
                    phone?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
                    email?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    ActionExecutionResult.IntentLaunched(intent)
                } catch (e: Exception) {
                    ActionExecutionResult.Error(context.getString(R.string.action_contacts_error))
                }
            }

            ProactiveAction.OPEN_SOURCE -> {
                val url = metadata["provider_url"] ?: metadata["url"] ?: metadata["website"]
                if (url.isNullOrBlank()) {
                    return@withContext ActionExecutionResult.Error(context.getString(R.string.action_url_missing))
                }
                val uri = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Uri.parse("https://$url")
                } else {
                    Uri.parse(url)
                }
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    ActionExecutionResult.IntentLaunched(intent)
                } catch (e: Exception) {
                    ActionExecutionResult.Error(context.getString(R.string.action_link_error))
                }
            }

            ProactiveAction.FIND_MANUAL,
            ProactiveAction.TRACK_PRICE -> {
                val query = metadata["model_number"] ?: metadata["product_name"] ?: item.title
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(intent)
                    ActionExecutionResult.IntentLaunched(intent)
                } catch (e: Exception) {
                    ActionExecutionResult.Error(context.getString(R.string.action_search_error))
                }
            }
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.action_copied), Toast.LENGTH_SHORT).show()
    }
}

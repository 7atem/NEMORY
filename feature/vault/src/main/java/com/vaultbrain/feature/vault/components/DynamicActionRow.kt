package com.vaultbrain.feature.vault.components

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.vaultbrain.core.common.model.ProactiveAction
import com.vaultbrain.core.common.model.ProactiveActionResolver
import com.vaultbrain.core.common.model.VaultItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DynamicActionRow(item: VaultItem, context: Context) {
    val meta = item.parsedMetadata
    val resolvedActions = ProactiveActionResolver.resolve(item).toSet()
    val actions = mutableListOf<@Composable () -> Unit>()

    // MONEY - Copy the extracted total without initiating a payment to an
    // unverified recipient. Payment destinations must always come from the
    // user's document and be reviewed explicitly.
    if (ProactiveAction.COPY_TOTAL in resolvedActions) {
        val totalToCopy = meta["total"] ?: meta["amount"]
        if (totalToCopy != null) {
            actions.add {
                ActionButton(icon = Icons.Default.ContentCopy, text = "Copy total") {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        ClipData.newPlainText("Document total", totalToCopy)
                    )
                }
            }
        }
    }

    // PRODUCTIVITY - Add to Contacts
    if (ProactiveAction.ADD_CONTACT in resolvedActions) {
        actions.add {
            ActionButton(icon = Icons.Default.Person, text = "Add Contact") {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    type = ContactsContract.Contacts.CONTENT_TYPE
                    putExtra(ContactsContract.Intents.Insert.NAME, meta["contact_name"])
                    meta["job_title"]?.let { putExtra(ContactsContract.Intents.Insert.JOB_TITLE, it) }
                    meta["email"]?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
                }
                context.startActivity(intent)
            }
        }
    }

    // HOME - Find Manual
    if (ProactiveAction.FIND_MANUAL in resolvedActions) {
        actions.add {
            ActionButton(icon = Icons.Default.Search, text = "Find Manual") {
                val modelOrTitle = meta["model_number"] ?: item.title
                val query = Uri.encode(modelOrTitle + " user manual pdf")
                val intent = Intent(Intent.ACTION_VIEW, ("https://www.google.com/search?q=" + query).toUri())
                context.startActivity(intent)
            }
        }
    }

    // TRAVEL - Add to Calendar
    if (ProactiveAction.ADD_TO_CALENDAR in resolvedActions) {
        actions.add {
            ActionButton(icon = Icons.Default.DateRange, text = "Add to Calendar") {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, item.title)
                    putExtra(CalendarContract.Events.DESCRIPTION, item.summary)
                    // Just use system time placeholders if they exist
                    val startTime = item.secondaryAlertDate ?: System.currentTimeMillis()
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                }
                context.startActivity(intent)
            }
        }
    }
    
    // HEALTH - Re-order Prescription
    if (ProactiveAction.REFILL_REMINDER in resolvedActions) {
        actions.add {
            ActionButton(icon = Icons.Default.Favorite, text = "Refill Reminder") {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, "Refill: " + item.title)
                    item.secondaryAlertDate?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                }
                context.startActivity(intent)
            }
        }
    }

    if (ProactiveAction.OPEN_SOURCE in resolvedActions) {
        meta["provider_url"]?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { url ->
            actions.add {
                ActionButton(icon = Icons.AutoMirrored.Filled.OpenInNew, text = "Open source") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
            }
        }
    }

    if (ProactiveAction.REMIND_LATER in resolvedActions) {
        actions.add {
            ActionButton(icon = Icons.Default.Notifications, text = "Remind later") {
                val startTime = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1_000
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, item.title)
                    putExtra(CalendarContract.Events.DESCRIPTION, item.summary)
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                }
                context.startActivity(intent)
            }
        }
    }

    if (ProactiveAction.TRACK_PRICE in resolvedActions) {
        actions.add {
            ActionButton(icon = Icons.AutoMirrored.Filled.TrendingDown, text = "Compare price") {
                val query = Uri.encode("${item.title} price")
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=$query".toUri()))
            }
        }
    }

    if (ProactiveAction.REVIEW_EXPIRY in resolvedActions) {
        actions.add {
            ActionButton(icon = Icons.Default.Event, text = "Review expiry") {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, "Review: ${item.title}")
                    item.expiryDate?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                }
                context.startActivity(intent)
            }
        }
    }

    if (actions.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Suggested Actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { it() }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    ElevatedButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

package com.vaultbrain.feature.lensmedia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.VaultItem
import androidx.core.net.toUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.vaultbrain.core.common.ui.SharedItemCard

/** Movie/series watchlist and book reading-list experience. */
@Composable
fun MediaLensScreen(
    items: List<VaultItem>,
    onItemClick: (VaultItem) -> Unit,
    onRemindLater: (VaultItem) -> Unit,
    onMarkCompleted: (VaultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val movies = items.count { it.mediaType() == "movie" }
    val series = items.count { it.mediaType() == "tv_series" }
    val books = items.count { it.mediaType() == "book" }
    val waiting = items.count { !it.isMediaCompleted() }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.media_lens_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.media_lens_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MediaStat(stringResource(R.string.media_stat_saved), items.size, Modifier.weight(1f))
                MediaStat(stringResource(R.string.media_stat_waiting), waiting, Modifier.weight(1f))
                MediaStat(stringResource(R.string.media_stat_books), books, Modifier.weight(1f))
            }
        }
        Text(
            text = listOf(
                pluralStringResource(R.plurals.media_movie_count, movies, movies),
                pluralStringResource(R.plurals.media_series_count, series, series),
                pluralStringResource(R.plurals.media_book_count, books, books)
            ).joinToString(" • "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (items.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.media_empty_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.media_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = items.sortedWith(
                        compareBy<VaultItem> { it.isMediaCompleted() }
                            .thenBy { it.secondaryAlertDate ?: Long.MAX_VALUE }
                            .thenByDescending { it.createdAt }
                    ),
                    key = { it.id }
                ) { item ->
                    MediaItemCard(
                        item = item,
                        onOpen = { onItemClick(item) },
                        onRemindLater = { onRemindLater(item) },
                        onMarkCompleted = { onMarkCompleted(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
private fun MediaItemCard(
    item: VaultItem,
    onOpen: () -> Unit,
    onRemindLater: () -> Unit,
    onMarkCompleted: () -> Unit
) {
    val type = item.mediaType()
    val status = item.mediaValue("media_status")
        ?: if (type == "book") "want_to_read" else "want_to_watch"
    val provider = item.mediaValue("provider_url")
        ?.let { runCatching { it.toUri().host }.getOrNull() }
        ?.removePrefix("www.")
    val providerUrl = item.mediaValue("provider_url")
        ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    val futureReminder = item.secondaryAlertDate?.takeIf { it > System.currentTimeMillis() }
    val uriHandler = LocalUriHandler.current

    SharedItemCard(
        item = item,
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        extraContent = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = mediaStatusLabel(status),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (item.isMediaCompleted()) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = mediaTypeLabel(type),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            item.mediaValue("author")?.let {
                Text(
                    text = stringResource(R.string.media_author, it),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            provider?.let {
                Text(
                    text = stringResource(R.string.media_saved_from, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            futureReminder?.let {
                Text(
                    text = stringResource(R.string.media_reminder_set, formatDate(it)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                providerUrl?.let { url ->
                    TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                        Text(stringResource(R.string.media_open_source))
                    }
                }
                TextButton(onClick = onOpen) {
                    Text(stringResource(R.string.media_open_details))
                }
                if (!item.isMediaCompleted()) {
                    TextButton(onClick = onMarkCompleted) {
                        Text(
                            stringResource(
                                if (type == "book") R.string.media_mark_finished
                                else R.string.media_mark_watched
                            )
                        )
                    }
                    TextButton(onClick = onRemindLater) {
                        Text(stringResource(R.string.media_remind_week))
                    }
                }
            }
        }
    )
}

private fun VaultItem.mediaValue(key: String): String? =
    customFields[key]?.takeIf(String::isNotBlank)
        ?: parsedMetadata[key]?.takeIf(String::isNotBlank)

private fun VaultItem.mediaType(): String = mediaValue("media_type") ?: when (effectiveClassification) {
    Classification.MOVIE -> "movie"
    Classification.TV_SERIES -> "tv_series"
    Classification.BOOK -> "book"
    else -> "media"
}

private fun VaultItem.isMediaCompleted(): Boolean = mediaValue("media_status")
    ?.lowercase() in setOf("watched", "finished", "completed")

@Composable
private fun mediaTypeLabel(type: String): String = when (type) {
    "movie" -> stringResource(R.string.media_type_movie)
    "tv_series" -> stringResource(R.string.media_type_series)
    "book" -> stringResource(R.string.media_type_book)
    else -> stringResource(R.string.media_type_other)
}

@Composable
private fun mediaStatusLabel(status: String): String = when (
    status.lowercase().replace('-', '_').replace(' ', '_')
) {
    "want_to_watch", "planned" -> stringResource(R.string.media_status_watch)
    "want_to_read" -> stringResource(R.string.media_status_read)
    "watching" -> stringResource(R.string.media_status_watching)
    "reading" -> stringResource(R.string.media_status_reading)
    "watched" -> stringResource(R.string.media_status_watched)
    "finished", "completed" -> stringResource(R.string.media_status_finished)
    "paused" -> stringResource(R.string.media_status_paused)
    else -> status
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))

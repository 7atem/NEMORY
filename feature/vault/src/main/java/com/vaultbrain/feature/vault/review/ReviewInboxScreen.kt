package com.vaultbrain.feature.vault.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.text.BidiFormatter
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.feature.vault.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewInboxScreen(
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    viewModel: ReviewInboxViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Text(
                    stringResource(R.string.review_all_clear),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    stringResource(R.string.review_all_clear_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.review_intro),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(items, key = VaultItem::id) { item ->
                    ReviewItemCard(
                        item = item,
                        onAccept = { viewModel.accept(item) },
                        onOpen = { onOpenItem(item.id) },
                        onCompare = item.possibleDuplicateOfItemId?.let { id ->
                            { onOpenItem(id) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewItemCard(
    item: VaultItem,
    onAccept: () -> Unit,
    onOpen: () -> Unit,
    onCompare: (() -> Unit)?
) {
    val reason = ReviewResolution.reason(item)
    val bidi = BidiFormatter.getInstance()
    val title = when (reason) {
        ReviewReason.POSSIBLE_DUPLICATE -> stringResource(R.string.review_possible_duplicate)
        ReviewReason.CATEGORY -> stringResource(R.string.review_category)
        ReviewReason.ENRICHMENT_FAILED -> stringResource(R.string.review_saved_without_ai)
        ReviewReason.DETAILS -> stringResource(R.string.review_details)
    }
    val body = when (reason) {
        ReviewReason.POSSIBLE_DUPLICATE -> stringResource(R.string.review_possible_duplicate_body)
        ReviewReason.CATEGORY -> stringResource(
            R.string.review_category_body,
            bidi.unicodeWrap(
                item.subtype ?: item.topics.firstOrNull() ?: stringResource(R.string.review_unknown_category)
            )
        )
        ReviewReason.ENRICHMENT_FAILED -> stringResource(R.string.review_saved_without_ai_body)
        ReviewReason.DETAILS -> stringResource(R.string.review_details_body)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (reason == ReviewReason.POSSIBLE_DUPLICATE) Icons.Default.ContentCopy else Icons.AutoMirrored.Filled.Rule,
                    contentDescription = null
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(bidi.unicodeWrap(item.title), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept) {
                    Text(
                        stringResource(
                            if (reason == ReviewReason.POSSIBLE_DUPLICATE) R.string.review_keep_both
                            else R.string.review_looks_right
                        )
                    )
                }
                OutlinedButton(onClick = onCompare ?: onOpen) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text(
                        stringResource(
                            if (onCompare != null) R.string.review_compare else R.string.review_open
                        ),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

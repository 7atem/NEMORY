package com.vaultbrain.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultbrain.core.common.ui.ResponsiveContainer
import com.vaultbrain.core.common.ui.VaultSpacing
import com.vaultbrain.shared.model.PersonalCollection
import com.vaultbrain.feature.vault.components.EmptyState
import com.vaultbrain.feature.vault.components.VaultItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onCollectionClick: (String) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.collections_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.detail_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.collections_new))
            }
        }
    ) { padding ->
        val shown = if (showArchived) state.archived else state.active
        ResponsiveContainer(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = VaultSpacing.lg,
                    top = VaultSpacing.lg,
                    end = VaultSpacing.lg,
                    bottom = VaultSpacing.fabClearance
                ),
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)
            ) {
                item {
                    Text(
                        stringResource(
                            if (showArchived) R.string.collections_archived_intro else R.string.collections_intro
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (shown.isEmpty()) {
                    item {
                        CollectionEmptyCard(
                            message = stringResource(if (showArchived) R.string.collections_empty_archived else R.string.collections_empty),
                            actionLabel = if (!showArchived) stringResource(R.string.collections_new) else null,
                            onAction = { showCreate = true }
                        )
                    }
                } else {
                    items(shown, key = PersonalCollection::id) { collection ->
                        CollectionCard(collection, onClick = { onCollectionClick(collection.id) })
                    }
                }
                item {
                    TextButton(onClick = { showArchived = !showArchived }) {
                        Icon(
                            if (showArchived) Icons.Default.Folder else Icons.Default.Archive,
                            contentDescription = null
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(stringResource(if (showArchived) R.string.collections_show_active else R.string.collections_show_archived))
                    }
                }
            }
        }
    }

    if (showCreate) {
        CollectionNameDialog(
            title = stringResource(R.string.collections_create_title),
            initialName = "",
            onDismiss = { showCreate = false },
            onConfirm = {
                viewModel.create(it)
                showCreate = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    collectionId: String,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(collectionId) { viewModel.load(collectionId) }
    val state by viewModel.uiState.collectAsState()
    var showRename by remember { mutableStateOf(false) }
    var showAddItems by remember { mutableStateOf(false) }
    val collection = state.collection

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(collection?.name ?: stringResource(R.string.collection_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.detail_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showRename = true }, enabled = collection != null) {
                        Icon(Icons.Default.Edit, stringResource(R.string.collection_rename))
                    }
                    IconButton(onClick = viewModel::togglePinned, enabled = collection != null) {
                        Icon(
                            Icons.Default.PushPin,
                            stringResource(if (collection?.isPinned == true) R.string.collection_unpin else R.string.collection_pin),
                            tint = if (collection?.isPinned == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = viewModel::toggleArchived, enabled = collection != null) {
                        Icon(
                            if (collection?.isArchived == true) Icons.Default.Restore else Icons.Default.Archive,
                            stringResource(if (collection?.isArchived == true) R.string.collection_restore else R.string.collection_archive)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (collection?.isArchived == false) {
                FloatingActionButton(onClick = { showAddItems = true }) {
                    Icon(Icons.Default.Add, stringResource(R.string.collection_add_items))
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            collection == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    text = stringResource(R.string.collection_not_found),
                    illustration = com.vaultbrain.core.common.R.drawable.il_empty_search,
                    actionLabel = stringResource(R.string.nav_back),
                    onAction = onBack
                )
            }
            else -> ResponsiveContainer(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = VaultSpacing.lg,
                        top = VaultSpacing.lg,
                        end = VaultSpacing.lg,
                        bottom = VaultSpacing.fabClearance
                    ),
                    verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)
                ) {
                    if (collection.isArchived) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Text(
                                    stringResource(R.string.collection_archived_body),
                                    modifier = Modifier.padding(VaultSpacing.lg)
                                )
                            }
                        }
                    }
                    if (state.items.isEmpty()) {
                        item {
                            CollectionEmptyCard(
                                message = stringResource(R.string.collection_empty_items),
                                actionLabel = if (!collection.isArchived) stringResource(R.string.collection_add_items) else null,
                                onAction = { showAddItems = true }
                            )
                        }
                    } else {
                        items(state.items, key = { it.id }) { item ->
                            VaultItemCard(
                                item = item,
                                onClick = { onItemClick(item.id) },
                                onArchive = null,
                                extraContent = {
                                    TextButton(onClick = { viewModel.removeItem(item.id) }) {
                                        Text(stringResource(R.string.collection_remove_item))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRename && collection != null) {
        CollectionNameDialog(
            title = stringResource(R.string.collection_rename_title),
            initialName = collection.name,
            onDismiss = { showRename = false },
            onConfirm = {
                viewModel.rename(it)
                showRename = false
            }
        )
    }
    if (showAddItems) {
        var selectedItemIds by remember { mutableStateOf(setOf<String>()) }
        fun toggleSelection(id: String) {
            selectedItemIds = if (selectedItemIds.contains(id)) {
                selectedItemIds - id
            } else {
                selectedItemIds + id
            }
        }
        AlertDialog(
            onDismissRequest = { showAddItems = false },
            title = { Text(stringResource(R.string.collection_add_items)) },
            text = {
                if (state.suggestedItems.isEmpty() && state.otherItems.isEmpty()) {
                    Text(stringResource(R.string.collection_no_items_available))
                } else {
                    LazyColumn(modifier = Modifier.height(420.dp)) {
                        if (state.suggestedItems.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.collections_smart_suggestions),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(state.suggestedItems, key = { it.id }) { item ->
                                val isSelected = selectedItemIds.contains(item.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = VaultSpacing.minTouchTarget)
                                        .toggleable(
                                            value = isSelected,
                                            role = Role.Checkbox,
                                            onValueChange = { toggleSelection(item.id) }
                                        )
                                        .padding(vertical = VaultSpacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null
                                    )
                                    Text(
                                        item.title,
                                        modifier = Modifier.weight(1f).padding(start = VaultSpacing.sm),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (state.otherItems.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.collections_all_items),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }
                            items(state.otherItems, key = { it.id }) { item ->
                                val isSelected = selectedItemIds.contains(item.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = VaultSpacing.minTouchTarget)
                                        .toggleable(
                                            value = isSelected,
                                            role = Role.Checkbox,
                                            onValueChange = { toggleSelection(item.id) }
                                        )
                                        .padding(vertical = VaultSpacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null
                                    )
                                    Text(
                                        item.title,
                                        modifier = Modifier.weight(1f).padding(start = VaultSpacing.sm),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedItemIds.forEach { viewModel.addItem(it) }
                        showAddItems = false
                    },
                    enabled = selectedItemIds.isNotEmpty()
                ) {
                    Text(stringResource(R.string.collections_add_items_confirm, selectedItemIds.size))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItems = false }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            }
        )
    }
}

@Composable
internal fun CollectionCard(
    collection: PersonalCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    collection.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pluralStringResource(R.plurals.collection_item_count, collection.itemCount, collection.itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (collection.isPinned) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = stringResource(R.string.collection_pinned),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun CollectionEmptyCard(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
            Text(message)
            if (actionLabel != null) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
internal fun CollectionNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val submit = {
        if (name.isNotBlank()) onConfirm(name.trim())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 120) name = it },
                label = { Text(stringResource(R.string.collection_name)) },
                supportingText = { Text("${name.length}/120") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )
        },
        confirmButton = {
            Button(onClick = submit, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.detail_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_cancel)) }
        }
    )
}

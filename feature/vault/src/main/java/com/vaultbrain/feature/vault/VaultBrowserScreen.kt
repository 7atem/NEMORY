package com.vaultbrain.feature.vault

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.vaultbrain.core.common.ui.getGridSpacing
import com.vaultbrain.core.common.ui.getHorizontalPadding
import com.vaultbrain.core.common.ui.RevealingAnimatedVisibility
import com.vaultbrain.core.common.ui.standardExitAnimation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.feature.vault.components.EmptyState
import com.vaultbrain.feature.vault.components.VaultItemCard
import kotlinx.coroutines.launch

@Composable
fun VaultBrowserScreen(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    initialFilter: String? = null,
    initialQuery: String? = null,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    viewModel: VaultBrowserViewModel = hiltViewModel()
) {
    LaunchedEffect(initialFilter, initialQuery) {
        viewModel.setInitialState(initialFilter, initialQuery)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.browser_item_deleted)
    val undoLabel = stringResource(R.string.home_undo)

    VaultBrowserContent(
        state = state,
        windowWidthSizeClass = windowWidthSizeClass,
        onQueryChange = viewModel::onQueryChange,
        onFilterSelected = viewModel::onFilterSelected,
        onCollectionSelected = viewModel::onCollectionSelected,
        onSortSelected = viewModel::onSortSelected,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onSelectAll = { viewModel.selectAll(state.items.map { it.id }) },
        onDeleteSelected = viewModel::deleteSelected,
        onPinSelected = viewModel::pinSelected,
        onDeleteItem = { item ->
            viewModel.deleteItem(item)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = deletedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.restoreItem(item)
            }
        },
        onItemClick = onItemClick,
        onBack = onBack,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultBrowserContent(
    state: VaultBrowserUiState,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    onQueryChange: (String) -> Unit,
    onFilterSelected: (BrowserFilter) -> Unit,
    onCollectionSelected: (String?) -> Unit,
    onSortSelected: (BrowserSort) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onPinSelected: () -> Unit,
    onDeleteItem: (VaultItem) -> Unit,
    onItemClick: (String) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isTimelineView by rememberSaveable { mutableStateOf(false) }

    val groupedItems = remember(state.items, androidx.compose.ui.platform.LocalConfiguration.current.locales.toLanguageTags()) {
        state.items.groupBy { item ->
            val date = java.time.Instant.ofEpochMilli(item.createdAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            date.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", java.util.Locale.getDefault()))
        }
    }

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.browser_selected_count, state.selectedItemIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.browser_clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.browser_select_all))
                        }
                        IconButton(onClick = onPinSelected) {
                            Icon(Icons.Default.PushPin, contentDescription = stringResource(R.string.browser_pin_selection))
                        }
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.browser_delete))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.browser_title))
                            Text(
                                androidx.compose.ui.res.pluralStringResource(
                                    R.plurals.browser_item_count,
                                    state.items.size,
                                    state.items.size
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.browser_back))
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 1000.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.browser_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.browser_clear_search))
                        }
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
                singleLine = true
            )

            // Filter Chips: generic item-state filters, then the user's personal collections.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(BrowserFilter.entries) { filter ->
                    val count = state.filterCounts[filter] ?: 0
                    if (count > 0 || (state.activeFilter == filter && state.activeCollectionId == null)) {
                        FilterChip(
                            selected = state.activeFilter == filter && state.activeCollectionId == null,
                            onClick = { onFilterSelected(filter) },
                            label = { Text("${stringResource(filter.labelRes)} ($count)") }
                        )
                    }
                }
                items(state.collections, key = { it.id }) { collection ->
                    FilterChip(
                        selected = state.activeCollectionId == collection.id,
                        onClick = {
                            onCollectionSelected(
                                if (state.activeCollectionId == collection.id) null else collection.id
                            )
                        },
                        label = { Text("${collection.name} (${collection.itemCount})") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    androidx.compose.ui.res.pluralStringResource(
                        R.plurals.browser_item_count,
                        state.items.size,
                        state.items.size
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isTimelineView = !isTimelineView }) {
                        Icon(
                            if (isTimelineView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewAgenda,
                            contentDescription = stringResource(R.string.browser_toggle_view)
                        )
                    }
                    Box {
                        AssistChip(
                            onClick = { showSortMenu = true },
                            label = { Text(stringResource(R.string.browser_sort, stringResource(state.sortOrder.labelRes))) },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        BrowserSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(stringResource(sort.labelRes)) },
                                onClick = {
                                    onSortSelected(sort)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
                }
            }

            // Item List
            if (state.items.isEmpty()) {
                val hasActiveNarrowing = state.searchQuery.isNotBlank() ||
                    state.activeFilter != BrowserFilter.ALL || state.activeCollectionId != null
                EmptyState(
                    text = if (hasActiveNarrowing) {
                        stringResource(R.string.browser_no_results)
                    } else {
                        stringResource(R.string.browser_empty)
                    },
                    illustration = if (state.searchQuery.isNotBlank()) {
                        com.vaultbrain.core.common.R.drawable.il_empty_search
                    } else {
                        com.vaultbrain.core.common.R.drawable.il_empty_vault
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                    actionLabel = if (hasActiveNarrowing) {
                        stringResource(R.string.browser_clear_filters)
                    } else null,
                    onAction = {
                        onQueryChange("")
                        onCollectionSelected(null)
                        onFilterSelected(BrowserFilter.ALL)
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isTimelineView) {
                        groupedItems.forEach { (monthYear, itemsForMonth) ->
                            item(key = monthYear) {
                                Text(
                                    text = monthYear,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                                )
                            }
                            items(itemsForMonth, key = { it.id }) { item ->
                                val isSelected = state.selectedItemIds.contains(item.id)

                                SwipeableSelectableVaultItem(
                                    item = item,
                                    isSelected = isSelected,
                                    isSelectionMode = state.isSelectionMode,
                                    onClick = {
                                        if (state.isSelectionMode) {
                                            onToggleSelection(item.id)
                                        } else {
                                            onItemClick(item.id)
                                        }
                                    },
                                    onLongClick = { onToggleSelection(item.id) },
                                    onDelete = { onDeleteItem(item) }
                                )
                            }
                        }
                    } else {
                        items(state.items, key = { it.id }) { item ->
                            val isSelected = state.selectedItemIds.contains(item.id)

                            SwipeableSelectableVaultItem(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = state.isSelectionMode,
                                onClick = {
                                    if (state.isSelectionMode) {
                                        onToggleSelection(item.id)
                                    } else {
                                        onItemClick(item.id)
                                    }
                                },
                                onLongClick = { onToggleSelection(item.id) },
                                onDelete = { onDeleteItem(item) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.browser_delete_title)) },
            text = {
                Text(
                    androidx.compose.ui.res.pluralStringResource(
                        R.plurals.browser_delete_body,
                        state.selectedItemIds.size,
                        state.selectedItemIds.size
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirmation = false
                    onDeleteSelected()
                }) { Text(stringResource(R.string.browser_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.home_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSelectableVaultItem(
    item: VaultItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !isSelectionMode,
        backgroundContent = {
            val color = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(color)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.browser_delete),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .semantics { selected = isSelected }
        ) {
            VaultItemCard(
                item = item,
                onClick = onClick,
                onLongClick = onLongClick
            )

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

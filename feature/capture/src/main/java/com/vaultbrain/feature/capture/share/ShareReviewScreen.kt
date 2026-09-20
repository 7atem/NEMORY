package com.vaultbrain.feature.capture.share

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vaultbrain.shared.model.PersonalCollection
import com.vaultbrain.feature.capture.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareReviewScreen(
    share: IncomingShare,
    onCancel: () -> Unit,
    onSave: (targetCollectionId: String?, skipLlmEnrichment: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShareReviewViewModel = hiltViewModel()
) {
    val collections by viewModel.collections.collectAsState()
    var selectedCollectionIndex by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_capture_share_review_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.feature_capture_discard)
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                TextButton(onClick = onCancel, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.feature_capture_discard))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        onSave(selectedCollectionId(selectedCollectionIndex, collections), true)
                    }
                ) {
                    Text(stringResource(R.string.feature_capture_share_save_without_ai))
                }
                TextButton(
                    onClick = {
                        onSave(selectedCollectionId(selectedCollectionIndex, collections), false)
                    }
                ) {
                    Text(stringResource(R.string.feature_capture_share_save))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SharePreviewCard(share = share)

            if (collections.isNotEmpty()) {
                CollectionPicker(
                    collections = collections,
                    selectedIndex = selectedCollectionIndex,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onSelect = { index ->
                        selectedCollectionIndex = index
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionPicker(
    collections: List<PersonalCollection>,
    selectedIndex: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Int) -> Unit
) {
    val options = listOf(stringResource(R.string.feature_capture_share_no_collection)) +
        collections.map { it.name }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = options[selectedIndex],
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.feature_capture_share_collection_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}

@Composable
private fun SharePreviewCard(share: IncomingShare) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            share.sourcePackage?.let { packageName ->
                Text(
                    text = stringResource(R.string.feature_capture_share_source, packageName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!share.text.isNullOrBlank()) {
                Text(
                    text = share.text,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (share.uris.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.feature_capture_share_attachments),
                    style = MaterialTheme.typography.titleSmall
                )
                share.uris.forEach { uri ->
                    AttachmentPreview(uri = uri)
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(uri: Uri) {
    val context = LocalContext.current
    val mimeType = remember(uri) {
        context.contentResolver.getType(uri) ?: "application/octet-stream"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (mimeType.startsWith("image/")) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(uri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Attachment,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = uri.lastPathSegment ?: uri.toString(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
            Text(
                text = mimeType,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun selectedCollectionId(
    index: Int,
    collections: List<PersonalCollection>
): String? {
    return if (index <= 0) null else collections.getOrNull(index - 1)?.id
}

package com.vaultbrain.feature.capture

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelStatus
import com.vaultbrain.core.common.model.SourceType
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.common.permissions.PermissionHelper
import com.vaultbrain.feature.capture.ui.components.CameraPreview
import com.vaultbrain.feature.capture.ui.components.ExperiencePickerScreen
import com.vaultbrain.feature.capture.ui.components.ReviewScreen

/**
 * Main capture flow composable.
 *
 * - If [initialInput] contains URIs or text, it is analysed immediately.
 * - Otherwise the camera preview is shown so the user can capture a new item.
 * - After analysis the user reviews the extracted fields and can save.
 *
 * @param onComplete Called after the item has been saved successfully.
 */
@Composable
fun CaptureFlowScreen(
    initialInput: CaptureInput = CaptureInput(),
    onComplete: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gemmaStatus by viewModel.gemmaStatus.collectAsState()
    val context = LocalContext.current

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showPermissionDenied by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.process(
                CaptureInput(
                    initialUris = uris,
                    sourceType = SourceType.GALLERY,
                    preferredLensTags = initialInput.preferredLensTags
                )
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val cameraGranted = grants[Manifest.permission.CAMERA] == true
        if (!cameraGranted) {
            showPermissionDenied = true
        }
    }

    LaunchedEffect(initialInput) {
        if (initialInput.initialUris.isNotEmpty() || !initialInput.initialText.isNullOrBlank()) {
            viewModel.process(initialInput)
        }
    }

    LaunchedEffect(uiState) {
        val isDirectCameraEntry = initialInput.initialUris.isEmpty() &&
            initialInput.initialText.isNullOrBlank()
        if (uiState is CaptureUiState.Camera && isDirectCameraEntry && !PermissionHelper.hasCamera(context)) {
            permissionLauncher.launch(PermissionHelper.capturePermissions())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is CaptureUiState.Camera -> {
                if (showPermissionDenied && !PermissionHelper.hasCamera(context)) {
                    PermissionDeniedScreen(
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        onPickFromGallery = {
                            showPermissionDenied = false
                            galleryLauncher.launch(arrayOf("image/*", "application/pdf"))
                        }
                    )
                } else {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onImageCaptured = { uri ->
                            pendingCameraUri = uri
                            viewModel.process(
                                CaptureInput(
                                    initialUris = listOf(uri),
                                    sourceType = SourceType.CAMERA,
                                    preferredLensTags = initialInput.preferredLensTags
                                )
                            )
                        },
                        onPickImage = { galleryLauncher.launch(arrayOf("image/*", "application/pdf")) }
                    )
                }
            }

            is CaptureUiState.Processing,
            is CaptureUiState.Saving -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(
                            if (state is CaptureUiState.Processing) R.string.feature_capture_processing
                            else R.string.feature_capture_saving
                        ),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            is CaptureUiState.PickExperience -> {
                ExperiencePickerScreen(
                    drafts = state.drafts,
                    suggestions = state.suggestions,
                    currentIndex = state.currentIndex,
                    onExperienceSelected = { viewModel.selectExperience(it) },
                    onJustSave = { viewModel.skipExperience() }
                )
            }

            is CaptureUiState.Review -> {
                ReviewScreen(
                    draft = state.draft,
                    confidence = state.confidence,
                    suggestedLensTags = state.suggestedLensTags,
                    onTitleChange = { viewModel.updateDraft(state.draft.copy(title = it)) },
                    onSummaryChange = { viewModel.updateDraft(state.draft.copy(summary = it)) },
                    onNotesChange = { viewModel.updateDraft(state.draft.copy(userNotes = it)) },
                    onClassificationChange = { classification ->
                        viewModel.updateDraft(
                            state.draft.copy(userClassificationOverride = classification)
                        )
                    },
                    onLensTagToggle = { tag ->
                        val tags = state.draft.lensTags.toMutableSet()
                        if (tag in tags) tags.remove(tag) else tags.add(tag)
                        viewModel.updateDraft(state.draft.copy(lensTags = tags))
                    },
                    onSave = { viewModel.save(onComplete) },
                    onDiscard = { viewModel.discard() },
                    batchPosition = state.batchPosition,
                    batchTotal = state.batchTotal,
                    onMetadataChange = { key, value -> viewModel.updateMetadata(key, value) },
                    editedMetadataKeys = state.editedMetadataKeys,
                    showOnDeviceAiHint = state.showOnDeviceAiHint &&
                        gemmaStatus == OnDeviceModelStatus.NOT_DOWNLOADED,
                    onOnDeviceAiDownload = viewModel::downloadGemmaModel
                )
            }

            is CaptureUiState.Saved -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.feature_capture_saved_success),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (state.itemNeedsReminder) {
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.feature_capture_reminder_prompt_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.feature_capture_reminder_prompt_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.dismissFollowUp(onComplete) },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(stringResource(R.string.feature_capture_got_it))
                                }
                            }
                        }
                    }
                    
                    if (state.followUps.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.feature_capture_suggested_followups),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    state.followUps.forEach { suggestion ->
                        OutlinedButton(
                            onClick = { viewModel.startFollowUpCapture(suggestion.experienceId) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(stringResource(suggestion.promptRes))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.dismissFollowUp(onComplete) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.feature_capture_done))
                    }
                }
            }

            is CaptureUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(
                        onClick = {
                            pendingCameraUri?.let { uri ->
                                viewModel.process(
                                    CaptureInput(
                                        initialUris = listOf(uri),
                                        sourceType = SourceType.CAMERA
                                    )
                                )
                            } ?: run {
                                viewModel.process(initialInput)
                            }
                        },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(stringResource(R.string.feature_capture_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionDeniedScreen(
    onOpenSettings: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.feature_capture_camera_perm_req_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.feature_capture_camera_perm_req_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_capture_open_settings))
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onPickFromGallery, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_capture_pick_from_gallery))
        }
    }
}

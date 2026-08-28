package com.vaultbrain.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.AutoAwesome

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultbrain.core.security.BiometricPromptHelper
import com.vaultbrain.feature.settings.viewmodel.SettingsUiState
import com.vaultbrain.feature.settings.viewmodel.SettingsViewModel

import com.vaultbrain.core.ai.llm.OnDeviceModelStatus
import com.vaultbrain.core.ai.llm.gemma.GemmaModelStatus

import com.vaultbrain.core.common.model.Classification
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gemmaStatus by viewModel.gemmaStatus.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricHelper = remember { BiometricPromptHelper() }
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    if (uiState.isPinSetupVisible) {
        PinSetupScreen(
            isDecoy = uiState.isDecoyPinSetup,
            onPinSet = { pin ->
                if (uiState.isDecoyPinSetup) {
                    viewModel.setDecoyPin(pin)
                } else {
                    viewModel.setPin(pin)
                }
                viewModel.dismissPinSetup()
            },
            onCancel = viewModel::dismissPinSetup
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
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
            GeneralSection(
                lensAccess = uiState.lensAccess,
                affiliateEnabled = uiState.affiliateLinksEnabled,
                onAffiliateToggle = viewModel::setAffiliateLinksEnabled
            )

            PrivateAiSection(
                status = uiState.privateAiStatus,
                onPrepare = viewModel::preparePrivateAi
            )

            GemmaModelSection(
                status = gemmaStatus,
                workRunning = uiState.localAiWorkRunning,
                processed = uiState.localAiProcessed,
                total = uiState.localAiTotal,
                lastImproved = uiState.localAiLastImproved,
                onDownload = viewModel::downloadGemmaModel,
                onDelete = viewModel::deleteGemmaModel,
                onReprocessAll = viewModel::reprocessAllWithGemma
            )

            CloudApprovalsSection(
                categories = uiState.cloudOptInCategories,
                onRevoke = viewModel::revokeCloudCategory,
                onClear = viewModel::clearCloudApprovals
            )

            AppearanceSection(
                themeMode = uiState.themeMode,
                dynamicColor = uiState.dynamicColor,
                onThemeModeChange = viewModel::setThemeMode,
                onDynamicColorChange = viewModel::setDynamicColor
            )

            SecuritySection(
                state = uiState,
                onToggleBiometric = { enabled ->
                    if (enabled && activity != null && biometricHelper.canAuthenticate(activity)) {
                        biometricHelper.showPrompt(
                            activity = activity,
                            title = "Enable biometric unlock",
                            subtitle = "Authenticate to enable biometric access",
                            negativeButtonText = "Cancel",
                            onSuccess = { viewModel.setBiometricEnabled(true) },
                            onError = { _, _ ->
                                Toast.makeText(context, "Biometric setup failed", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        viewModel.setBiometricEnabled(enabled)
                    }
                },
                onSetPin = { viewModel.showPinSetup(isDecoy = false) },
                onAutoLockChange = viewModel::setAutoLockMinutes,
                onToggleAuditLog = viewModel::toggleAuditLog
            )

            DataManagementSection(
                state = uiState,
                onDriveToggle = viewModel::setDriveBackupEnabled,
                onGmailToggle = viewModel::setGmailIntegrationEnabled
            )
        }
    }
}

@Composable
private fun PrivateAiSection(
    status: OnDeviceModelStatus,
    onPrepare: () -> Unit
) {
    val title = when (status) {
        OnDeviceModelStatus.READY -> stringResource(R.string.settings_private_ai_ready)
        OnDeviceModelStatus.DOWNLOADING -> stringResource(R.string.settings_private_ai_preparing)
        OnDeviceModelStatus.DOWNLOADABLE -> stringResource(R.string.settings_private_ai_available)
        OnDeviceModelStatus.UNAVAILABLE -> stringResource(R.string.settings_private_ai_unavailable)
        OnDeviceModelStatus.UNKNOWN,
        OnDeviceModelStatus.ERROR -> stringResource(R.string.settings_private_ai_checking)
    }
    val body = when (status) {
        OnDeviceModelStatus.READY -> stringResource(R.string.settings_private_ai_ready_body)
        OnDeviceModelStatus.DOWNLOADING -> stringResource(R.string.settings_private_ai_preparing_body)
        OnDeviceModelStatus.DOWNLOADABLE -> stringResource(R.string.settings_private_ai_available_body)
        OnDeviceModelStatus.UNAVAILABLE -> stringResource(R.string.settings_private_ai_unavailable_body)
        OnDeviceModelStatus.UNKNOWN,
        OnDeviceModelStatus.ERROR -> stringResource(R.string.settings_private_ai_checking_body)
    }
    SettingsCard(title = stringResource(R.string.settings_private_ai), icon = Icons.Default.AutoAwesome) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (status == OnDeviceModelStatus.DOWNLOADING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        if (status in setOf(
                OnDeviceModelStatus.DOWNLOADABLE,
                OnDeviceModelStatus.UNKNOWN,
                OnDeviceModelStatus.ERROR
            )
        ) {
            TextButton(onClick = onPrepare, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_private_ai_prepare))
            }
        }
    }
}

@Composable
private fun GemmaModelSection(
    status: GemmaModelStatus,
    workRunning: Boolean,
    processed: Int,
    total: Int,
    lastImproved: Int?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onReprocessAll: () -> Unit
) {
    val title = when (status) {
        GemmaModelStatus.NOT_SUPPORTED -> stringResource(R.string.settings_gemma_not_supported)
        GemmaModelStatus.NOT_DOWNLOADED -> stringResource(R.string.settings_gemma_not_downloaded)
        GemmaModelStatus.QUEUED -> stringResource(R.string.settings_gemma_queued)
        is GemmaModelStatus.DOWNLOADING -> stringResource(R.string.settings_gemma_downloading)
        GemmaModelStatus.READY -> stringResource(R.string.settings_gemma_ready)
        GemmaModelStatus.ERROR -> stringResource(R.string.settings_gemma_error)
    }
    val body = when (status) {
        GemmaModelStatus.NOT_SUPPORTED -> stringResource(R.string.settings_gemma_not_supported_body)
        GemmaModelStatus.NOT_DOWNLOADED -> stringResource(R.string.settings_gemma_not_downloaded_body)
        GemmaModelStatus.QUEUED -> stringResource(R.string.settings_gemma_queued_body)
        is GemmaModelStatus.DOWNLOADING -> stringResource(R.string.settings_gemma_downloading_body)
        GemmaModelStatus.READY -> stringResource(R.string.settings_gemma_ready_body)
        GemmaModelStatus.ERROR -> stringResource(R.string.settings_gemma_error_body)
    }
    SettingsCard(title = stringResource(R.string.settings_gemma_model), icon = Icons.Default.Memory) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (status == GemmaModelStatus.QUEUED) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        } else if (status is GemmaModelStatus.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { status.progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }
        if (status == GemmaModelStatus.READY && workRunning) {
            Text(
                text = if (total > 0) {
                    stringResource(R.string.settings_gemma_processing_progress, processed, total)
                } else {
                    stringResource(R.string.settings_gemma_activating)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { processed.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        } else if (status == GemmaModelStatus.READY && lastImproved != null) {
            Text(
                text = stringResource(R.string.settings_gemma_last_improved, lastImproved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        when (status) {
            GemmaModelStatus.NOT_DOWNLOADED,
            GemmaModelStatus.ERROR -> TextButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_gemma_download))
            }
            GemmaModelStatus.READY -> {
                TextButton(
                    onClick = onReprocessAll,
                    enabled = !workRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_gemma_reprocess_all))
                }
                TextButton(
                    onClick = onDelete,
                    enabled = !workRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_gemma_delete))
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun CloudApprovalsSection(
    categories: Set<Classification>,
    onRevoke: (Classification) -> Unit,
    onClear: () -> Unit
) {
    SettingsCard(
        title = stringResource(R.string.settings_cloud_approvals),
        icon = Icons.Default.Security
    ) {
        Text(
            stringResource(R.string.settings_cloud_approvals_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (categories.isEmpty()) {
            Text(
                stringResource(R.string.settings_cloud_approvals_empty),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            categories.sortedBy(Classification::name).forEach { category ->
                ListItem(
                    headlineContent = { Text(category.cloudCategoryLabel()) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_cloud_approval_remembered))
                    },
                    trailingContent = {
                        TextButton(onClick = { onRevoke(category) }) {
                            Text(stringResource(R.string.settings_cloud_approval_revoke))
                        }
                    }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_cloud_approvals_clear))
            }
        }
    }
}

@Composable
private fun Classification.cloudCategoryLabel(): String = stringResource(
    when (this) {
        Classification.MOVIE -> R.string.settings_cloud_category_movies
        Classification.TV_SERIES -> R.string.settings_cloud_category_tv
        Classification.BOOK -> R.string.settings_cloud_category_books
        Classification.PRODUCT_PHOTO -> R.string.settings_cloud_category_products
        Classification.MENU_PHOTO -> R.string.settings_cloud_category_menus
        else -> R.string.settings_cloud_category_other
    }
)

@Composable
private fun GeneralSection(
    lensAccess: Map<String, Boolean>,
    affiliateEnabled: Boolean,
    onAffiliateToggle: (Boolean) -> Unit
) {
    var helpVisible by remember { mutableStateOf(false) }
    var privacyVisible by remember { mutableStateOf(false) }

    SettingsCard(title = "General", icon = Icons.Default.Settings) {
        Text("Lenses", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
        lensAccess.forEach { (name, _) ->
            ListItem(
                headlineContent = { Text(name) },
                supportingContent = { Text("Enabled for everyone") },
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) }
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        SettingsSwitch(
            label = "Enable affiliate links",
            checked = affiliateEnabled,
            onCheckedChange = onAffiliateToggle
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        TextButton(onClick = { helpVisible = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Help & support")
        }
        TextButton(onClick = { privacyVisible = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Privacy policy")
        }
        Text(
            text = "Nemory v1.0.0",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    if (helpVisible) {
        AlertDialog(
            onDismissRequest = { helpVisible = false },
            title = { Text("Help & support") },
            text = {
                Text("Capture with the camera, import images or PDFs, paste text, or use voice. Review every item before saving, then edit its lens-specific fields from the item page. Cloud sync is intentionally unavailable in this build.")
            },
            confirmButton = { TextButton(onClick = { helpVisible = false }) { Text("Done") } }
        )
    }

    if (privacyVisible) {
        AlertDialog(
            onDismissRequest = { privacyVisible = false },
            title = { Text("Privacy summary") },
            text = {
                Text("Vault items and imported media stay in app-private storage on this device. On-device OCR and analysis process captures locally. Cloud and Gmail integrations are disabled in this build. Exporting or sharing an item sends only the content you explicitly choose.")
            },
            confirmButton = { TextButton(onClick = { privacyVisible = false }) { Text("Done") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecuritySection(
    state: SettingsUiState,
    onToggleBiometric: (Boolean) -> Unit,
    onSetPin: () -> Unit,
    onAutoLockChange: (Int) -> Unit,
    onToggleAuditLog: () -> Unit
) {
    val autoLockOptions = listOf(0 to "Never", 1 to "1 minute", 2 to "2 minutes", 5 to "5 minutes", 15 to "15 minutes")
    var expanded by remember { mutableStateOf(false) }

    SettingsCard(title = "Security", icon = Icons.Default.Security) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Security Health",
                tint = if (state.biometricEnabled || state.pinSet) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Security Health", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (state.biometricEnabled || state.pinSet) "Protected" else "At Risk - Set up PIN or Biometrics",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsSwitch(
            label = "Enable biometric unlock",
            checked = state.biometricEnabled,
            onCheckedChange = onToggleBiometric
        )

        TextButton(onClick = onSetPin, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.pinSet) "Change PIN" else "Set PIN")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Auto-lock after", modifier = Modifier.weight(1f))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = autoLockOptions.first { it.first == state.autoLockMinutes }.second,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .width(160.dp)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    autoLockOptions.forEach { (minutes, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onAutoLockChange(minutes)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        TextButton(onClick = onToggleAuditLog, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.showAuditLog) "Hide audit log" else "View audit log (last 50)")
        }

        if (state.showAuditLog) {
            Spacer(modifier = Modifier.height(8.dp))
            state.auditLogs.forEach { log ->
                Text(
                    text = "${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))} - ${log.action}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DataManagementSection(
    state: SettingsUiState,
    onDriveToggle: (Boolean) -> Unit,
    onGmailToggle: (Boolean) -> Unit
) {
    SettingsCard(title = "Data Management", icon = Icons.Default.Storage) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Storage Usage", style = MaterialTheme.typography.bodyMedium)
                Text("1.2 GB used", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { 0.4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsSwitch(
            label = "Google Drive backup",
            checked = state.driveBackupEnabled,
            onCheckedChange = onDriveToggle,
            enabled = false
        )
        SettingsSwitch(
            label = "Gmail integration",
            checked = state.gmailIntegrationEnabled,
            onCheckedChange = onGmailToggle,
            enabled = false
        )
        Text(
            "Cloud integrations remain off until OAuth and production API credentials are configured.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(
    themeMode: com.vaultbrain.core.common.preferences.ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (com.vaultbrain.core.common.preferences.ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val themeOptions = listOf(
        com.vaultbrain.core.common.preferences.ThemeMode.SYSTEM to "System Default",
        com.vaultbrain.core.common.preferences.ThemeMode.LIGHT to "Light",
        com.vaultbrain.core.common.preferences.ThemeMode.DARK to "Dark"
    )

    SettingsCard(title = "Appearance", icon = Icons.Default.Palette) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Theme", modifier = Modifier.weight(1f))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = themeOptions.first { it.first == themeMode }.second,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .width(160.dp)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    themeOptions.forEach { (mode, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onThemeModeChange(mode)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        SettingsSwitch(
            label = "Dynamic color",
            checked = dynamicColor,
            onCheckedChange = onDynamicColorChange
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

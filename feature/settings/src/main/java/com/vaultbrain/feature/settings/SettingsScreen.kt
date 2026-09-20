package com.vaultbrain.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.CloudOff

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

import com.vaultbrain.core.ai.llm.OnDeviceModelStatus as PrivateAiStatus
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelStatus as GemmaModelStatus

import com.vaultbrain.shared.model.Classification
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onConnectionsClick: () -> Unit = {},
    onPrivacyScoreClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gemmaStatus by viewModel.gemmaStatus.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricHelper = remember { BiometricPromptHelper() }
    var backupAction by remember { mutableStateOf<BackupAction?>(null) }
    var backupPassphrase by remember { mutableStateOf("") }
    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.exportPortableBackup(it, backupPassphrase) }
        backupPassphrase = ""
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importPortableBackup(it, backupPassphrase) }
        backupPassphrase = ""
    }
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

    backupAction?.let { action ->
        AlertDialog(
            onDismissRequest = { backupAction = null; backupPassphrase = "" },
            title = {
                Text(stringResource(if (action == BackupAction.EXPORT) R.string.settings_backup_export else R.string.settings_backup_restore))
            },
            text = {
                Column {
                    Text(stringResource(R.string.settings_backup_passphrase_help))
                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text(stringResource(R.string.settings_backup_passphrase)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = backupPassphrase.length >= if (action == BackupAction.EXPORT) 12 else 1,
                    onClick = {
                        backupAction = null
                        if (action == BackupAction.EXPORT) {
                            exportBackup.launch("nemory-${java.time.LocalDate.now()}.nemorybackup")
                        } else {
                            importBackup.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
                        }
                    }
                ) { Text(stringResource(R.string.settings_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { backupAction = null; backupPassphrase = "" }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            PrivacyDashboardSection(onPrivacyScoreClick = onPrivacyScoreClick)
            
            GeneralSection(
                lensAccess = uiState.lensAccess,
                affiliateEnabled = uiState.affiliateLinksEnabled,
                onAffiliateToggle = viewModel::setAffiliateLinksEnabled,
                magicScreenshotEnabled = uiState.magicScreenshotEnabled,
                onMagicScreenshotToggle = viewModel::setMagicScreenshotEnabled
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
                onGmailToggle = viewModel::setGmailIntegrationEnabled,
                onExportBackup = { backupAction = BackupAction.EXPORT },
                onImportBackup = { backupAction = BackupAction.IMPORT }
            )

            ConnectionsSection(onManageClick = onConnectionsClick)
        }
    }
}

@Composable
private fun PrivateAiSection(
    status: PrivateAiStatus,
    onPrepare: () -> Unit
) {
    val title = when (status) {
        PrivateAiStatus.READY -> stringResource(R.string.settings_private_ai_ready)
        PrivateAiStatus.DOWNLOADING -> stringResource(R.string.settings_private_ai_preparing)
        PrivateAiStatus.DOWNLOADABLE -> stringResource(R.string.settings_private_ai_available)
        PrivateAiStatus.UNAVAILABLE -> stringResource(R.string.settings_private_ai_unavailable)
        PrivateAiStatus.UNKNOWN,
        PrivateAiStatus.ERROR -> stringResource(R.string.settings_private_ai_checking)
    }
    val body = when (status) {
        PrivateAiStatus.READY -> stringResource(R.string.settings_private_ai_ready_body)
        PrivateAiStatus.DOWNLOADING -> stringResource(R.string.settings_private_ai_preparing_body)
        PrivateAiStatus.DOWNLOADABLE -> stringResource(R.string.settings_private_ai_available_body)
        PrivateAiStatus.UNAVAILABLE -> stringResource(R.string.settings_private_ai_unavailable_body)
        PrivateAiStatus.UNKNOWN,
        PrivateAiStatus.ERROR -> stringResource(R.string.settings_private_ai_checking_body)
    }
    SettingsCard(title = stringResource(R.string.settings_private_ai), icon = Icons.Default.AutoAwesome) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (status == PrivateAiStatus.DOWNLOADING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        if (status in setOf(
                PrivateAiStatus.DOWNLOADABLE,
                PrivateAiStatus.UNKNOWN,
                PrivateAiStatus.ERROR
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
        GemmaModelStatus.QUEUED,
        is GemmaModelStatus.DOWNLOADING -> stringResource(R.string.settings_gemma_downloading)
        GemmaModelStatus.READY -> stringResource(R.string.settings_gemma_ready)
        GemmaModelStatus.ERROR -> stringResource(R.string.settings_gemma_error)
    }
    val body = when (status) {
        GemmaModelStatus.NOT_SUPPORTED -> stringResource(R.string.settings_gemma_not_supported_body)
        GemmaModelStatus.NOT_DOWNLOADED -> stringResource(R.string.settings_gemma_not_downloaded_body)
        GemmaModelStatus.QUEUED,
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
        if (status is GemmaModelStatus.DOWNLOADING) {
            val progressVal = status.progress / 100f
            LinearProgressIndicator(
                progress = { progressVal },
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
private fun PrivacyDashboardSection(onPrivacyScoreClick: () -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.privacy_overview_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_local_ai), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onPrivacyScoreClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.privacy_overview_title))
            }
        }
    }
}

@Composable
private fun DashboardRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GeneralSection(
    lensAccess: Map<String, Boolean>,
    affiliateEnabled: Boolean,
    onAffiliateToggle: (Boolean) -> Unit,
    magicScreenshotEnabled: Boolean,
    onMagicScreenshotToggle: (Boolean) -> Unit
) {
    var helpVisible by remember { mutableStateOf(false) }
    var privacyVisible by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onMagicScreenshotToggle(true)
        } else {
            android.widget.Toast.makeText(context, "Storage permission is required", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    val mediaPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "1.1.2"
    }

    SettingsCard(title = stringResource(R.string.settings_general), icon = Icons.Default.Settings) {
        Text(stringResource(R.string.settings_lenses), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
        lensAccess.forEach { (name, _) ->
            ListItem(
                headlineContent = { Text(name) },
                supportingContent = { Text(stringResource(R.string.settings_enabled_for_everyone)) },
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) }
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        SettingsSwitch(
            label = stringResource(R.string.settings_affiliate_links),
            checked = affiliateEnabled,
            onCheckedChange = onAffiliateToggle
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsSwitch(
            label = "Magic Screenshot Detection (Ambient AI)",
            checked = magicScreenshotEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        mediaPermission
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    
                    if (isGranted) {
                        onMagicScreenshotToggle(true)
                    } else {
                        permissionLauncher.launch(mediaPermission)
                    }
                } else {
                    onMagicScreenshotToggle(false)
                }
            }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        TextButton(onClick = { helpVisible = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_help_support))
        }
        TextButton(onClick = { privacyVisible = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_privacy_policy))
        }
        Text(
            text = stringResource(R.string.settings_version, versionName),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    if (helpVisible) {
        AlertDialog(
            onDismissRequest = { helpVisible = false },
            title = { Text(stringResource(R.string.settings_help_support)) },
            text = {
                Text(stringResource(R.string.settings_help_support_desc))
            },
            confirmButton = { TextButton(onClick = { helpVisible = false }) { Text(stringResource(R.string.settings_done)) } }
        )
    }

    if (privacyVisible) {
        AlertDialog(
            onDismissRequest = { privacyVisible = false },
            title = { Text(stringResource(R.string.settings_privacy_summary)) },
            text = {
                Text(stringResource(R.string.settings_privacy_summary_desc))
            },
            confirmButton = { TextButton(onClick = { privacyVisible = false }) { Text(stringResource(R.string.settings_done)) } }
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
    val autoLockOptions = listOf(
        0 to stringResource(R.string.settings_lock_never),
        1 to stringResource(R.string.settings_lock_1m),
        2 to stringResource(R.string.settings_lock_2m),
        5 to stringResource(R.string.settings_lock_5m),
        15 to stringResource(R.string.settings_lock_15m)
    )
    var expanded by remember { mutableStateOf(false) }

    SettingsCard(title = stringResource(R.string.settings_security), icon = Icons.Default.Security) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = stringResource(R.string.settings_security_health),
                tint = if (state.biometricEnabled || state.pinSet) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.settings_security_health), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (state.biometricEnabled || state.pinSet) {
                        stringResource(R.string.settings_security_protected)
                    } else {
                        stringResource(R.string.settings_security_at_risk)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsSwitch(
            label = stringResource(R.string.settings_biometric_enable),
            checked = state.biometricEnabled,
            onCheckedChange = onToggleBiometric
        )

        TextButton(onClick = onSetPin, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.pinSet) stringResource(R.string.settings_pin_change) else stringResource(R.string.settings_pin_set))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.settings_auto_lock_after), modifier = Modifier.weight(1f))
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
            Text(if (state.showAuditLog) stringResource(R.string.settings_audit_log_hide) else stringResource(R.string.settings_audit_log_view))
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
    onGmailToggle: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val storageUsedFormatted = remember {
        runCatching {
            var totalBytes = 0L
            context.filesDir?.walkTopDown()?.forEach { if (it.isFile) totalBytes += it.length() }
            context.cacheDir?.walkTopDown()?.forEach { if (it.isFile) totalBytes += it.length() }
            android.text.format.Formatter.formatFileSize(context, totalBytes)
        }.getOrDefault("—")
    }

    SettingsCard(title = stringResource(R.string.settings_data_management), icon = Icons.Default.Storage) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.settings_storage_usage), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.settings_storage_used, storageUsedFormatted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(stringResource(R.string.settings_backup_portable), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.settings_backup_portable_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row {
            TextButton(onClick = onExportBackup, enabled = !state.backupInProgress) {
                Text(stringResource(R.string.settings_backup_export))
            }
            TextButton(onClick = onImportBackup, enabled = !state.backupInProgress) {
                Text(stringResource(R.string.settings_backup_restore))
            }
        }
        if (state.backupInProgress) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsSwitch(
            label = stringResource(R.string.settings_google_drive_backup),
            checked = state.driveBackupEnabled,
            onCheckedChange = onDriveToggle,
            enabled = false
        )
        SettingsSwitch(
            label = stringResource(R.string.settings_gmail_unavailable),
            checked = false,
            onCheckedChange = onGmailToggle,
            enabled = false
        )
        Text(
            stringResource(R.string.settings_cloud_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private enum class BackupAction { EXPORT, IMPORT }

@Composable
private fun ConnectionsSection(onManageClick: () -> Unit) {
    SettingsCard(
        title = stringResource(R.string.settings_connections),
        icon = Icons.Default.Sync
    ) {
        Text(
            stringResource(R.string.settings_connections_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = onManageClick,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.settings_connections_manage))
        }
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
        com.vaultbrain.core.common.preferences.ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
        com.vaultbrain.core.common.preferences.ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
        com.vaultbrain.core.common.preferences.ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
    )

    SettingsCard(title = stringResource(R.string.settings_appearance), icon = Icons.Default.Palette) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.settings_theme), modifier = Modifier.weight(1f))
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

import os

filepath = r"d:\Vault Brain\app\src\main\java\com\vaultbrain\app\navigation\VaultBrainNavGraph.kt"

with open(filepath, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# We want to keep lines up to 386.
new_lines = lines[:386]

# Then we append the correct LensRouterScreen function
correct_code = """
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LensRouterScreen(
    lensId: String,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    onAdd: (String) -> Unit,
    viewModel: com.vaultbrain.feature.vault.LensDetailViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    androidx.compose.runtime.LaunchedEffect(lensId) {
        viewModel.loadLens(lensId)
    }
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(state.title.ifBlank { lensId }) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAdd(lensId) }) {
                Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = "Add to ${state.title.ifBlank { lensId }}")
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (lensId) {
            com.vaultbrain.core.common.model.LensId.MONEY -> com.vaultbrain.feature.lensmoney.MoneyLensScreen(
                items = state.items,
                onItemClick = { onItemClick(it.id) },
                onExportClick = {
                    val csvFile = com.vaultbrain.feature.lensmoney.MoneyCsvExporter
                        .generateCsv(context, state.items)
                    val shareIntent = com.vaultbrain.feature.lensmoney.MoneyCsvExporter
                        .createShareIntent(context, csvFile)
                    context.startActivity(
                        android.content.Intent.createChooser(shareIntent, null)
                    )
                },
                onQuickAdd = { onAdd(com.vaultbrain.core.common.model.LensId.MONEY) },
                modifier = modifier
            )
            com.vaultbrain.core.common.model.LensId.HEALTH -> com.vaultbrain.feature.lenshealth.HealthLensScreen(
                items = state.items,
                onItemClick = { onItemClick(it.id) },
                onQuickAdd = { onAdd(com.vaultbrain.core.common.model.LensId.HEALTH) },
                modifier = modifier
            )
            com.vaultbrain.core.common.model.LensId.TRAVEL -> com.vaultbrain.feature.lenstravel.TravelLensScreen(
                items = state.items,
                onItemClick = { onItemClick(it.id) },
                onQuickAdd = { onAdd(com.vaultbrain.core.common.model.LensId.TRAVEL) },
                modifier = modifier
            )
            com.vaultbrain.core.common.model.LensId.BUREAUCRACY -> com.vaultbrain.feature.lensbureaucracy.BureaucracyLensScreen(
                items = state.items,
                onItemClick = { onItemClick(it.id) },
                onQuickAdd = { onAdd(com.vaultbrain.core.common.model.LensId.BUREAUCRACY) },
                modifier = modifier
            )
            com.vaultbrain.core.common.model.LensId.MEDIA -> com.vaultbrain.feature.lensmedia.MediaLensScreen(
                items = state.items,
                onItemClick = { onItemClick(it.id) },
                onRemindLater = { viewModel.remindMediaItem(it) },
                onMarkCompleted = { viewModel.completeMediaItem(it) },
                modifier = modifier
            )
            else -> com.vaultbrain.feature.vault.LensDetailScreen(
                lensId = lensId,
                onBack = onBack,
                onItemClick = onItemClick
            )
        }
    }
}
"""
new_lines.append(correct_code)

with open(filepath, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
print("Fixed NavGraph.")

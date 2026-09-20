package com.vaultbrain.app.navigation

import androidx.compose.ui.res.stringResource
import com.vaultbrain.app.R

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.feature.vault.LensDetailViewModel
import com.vaultbrain.feature.lensbureaucracy.BureaucracyLensScreen
import com.vaultbrain.feature.lenshealth.HealthLensScreen
import com.vaultbrain.feature.lensmedia.MediaLensScreen
import com.vaultbrain.feature.lensmoney.MoneyLensScreen
import com.vaultbrain.feature.lenstravel.TravelLensScreen

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VaultBrainNavGraph(
    modifier: Modifier = Modifier,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.AuthGate.route,
    destinationAfterUnlock: String = Screen.Home.route,
    launchRequestId: Int = 0
) {
    val consumedLaunch = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(-1) }
    LaunchedEffect(launchRequestId) {
        if (consumedLaunch.intValue != launchRequestId && startDestination !in setOf(Screen.AuthGate.route, Screen.Onboarding.route)) {
            consumedLaunch.intValue = launchRequestId
            if (launchRequestId > 0 || destinationAfterUnlock != Screen.Home.route) {
                navController.navigate(destinationAfterUnlock) { launchSingleTop = true }
            }
        }
    }
    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier
        ) {
            composable(Screen.AuthGate.route) {
                com.vaultbrain.feature.vault.AuthGateScreen(
                    onUnlocked = { isDecoyMode ->
                        navController.navigate(destinationAfterUnlock) {
                            popUpTo(Screen.AuthGate.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Onboarding.route) {
                val context = androidx.compose.ui.platform.LocalContext.current
                com.vaultbrain.feature.vault.OnboardingScreen(
                    onOnboardingComplete = {
                        com.vaultbrain.core.common.OnboardingState.setHasSeenOnboarding(context, true)
                        navController.navigate(destinationAfterUnlock) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Home.route) {
                MainScaffold(navController, Screen.Home.route, windowWidthSizeClass) {
                    com.vaultbrain.feature.vault.HomeScreen(
                        onSearchClick = { query ->
                            val route = query?.let {
                                "${Screen.Search.route}?query=${android.net.Uri.encode(it)}"
                            } ?: Screen.Search.route
                            navController.navigate(route)
                        },
                        onFilterClick = { filter ->
                            if (filter == "needs_review") {
                                navController.navigate(Screen.Review.route)
                            } else {
                                navController.navigate(
                                    "${Screen.Search.route}?filter=${android.net.Uri.encode(filter)}"
                                )
                            }
                        },
                        onCollectionsClick = { navController.navigate(Screen.Collections.route) },
                        onCollectionClick = { id ->
                            navController.navigate("collection/${android.net.Uri.encode(id)}")
                        },
                        onItemClick = { id -> navController.navigate("detail/${android.net.Uri.encode(id)}") },
                        onCaptureClick = { navController.navigate(Screen.Capture.route) },
                        onGalleryImagesPicked = { uris ->
                            val encoded = android.net.Uri.encode(uris.joinToString("\u001F"))
                            navController.navigate("${Screen.Capture.route}?imageUris=$encoded")
                        },
                        onTextPasted = { text ->
                            navController.navigate(
                                "${Screen.Capture.route}?text=${android.net.Uri.encode(text)}&source=text"
                            )
                        },
                        onVoiceCaptured = { text ->
                            navController.navigate(
                                "${Screen.Capture.route}?text=${android.net.Uri.encode(text)}&source=voice"
                            )
                        },
                        onSettingsClick = { navController.navigate(Screen.Settings.route) },
                        onAskBrain = { query ->
                            navController.navigate("${Screen.Brain.route}?query=${android.net.Uri.encode(query)}")
                        },
                        onLensClick = { lensId -> navController.navigate("lens/${android.net.Uri.encode(lensId)}") },
                        windowWidthSizeClass = windowWidthSizeClass
                    )
                }
            }
            composable(
                route = "${Screen.Search.route}?filter={filter}&query={query}",
                arguments = listOf(
                    androidx.navigation.navArgument("filter") {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    androidx.navigation.navArgument("query") {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                MainScaffold(navController, Screen.Search.route, windowWidthSizeClass) {
                    com.vaultbrain.feature.vault.VaultBrowserScreen(
                        onBack = { navController.popBackStack() },
                        onItemClick = { id -> navController.navigate("detail/${android.net.Uri.encode(id)}") },
                        initialFilter = backStackEntry.arguments?.getString("filter"),
                        initialQuery = backStackEntry.arguments?.getString("query"),
                        windowWidthSizeClass = windowWidthSizeClass
                    )
                }
            }
            composable(
                route = "${Screen.Brain.route}?query={query}",
                arguments = listOf(
                    androidx.navigation.navArgument("query") {
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                MainScaffold(navController, Screen.Brain.route, windowWidthSizeClass) {
                    com.vaultbrain.feature.brain.BrainChatScreen(
                        onSourceClick = { id -> navController.navigate("detail/${android.net.Uri.encode(id)}") },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.Settings.route) {
                com.vaultbrain.feature.settings.SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onConnectionsClick = { navController.navigate(Screen.Connections.route) },
                    onPrivacyScoreClick = { navController.navigate("privacy_score") }
                )
            }
            composable("privacy_score") {
                com.vaultbrain.feature.settings.PrivacyScoreScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Connections.route) {
                com.vaultbrain.feature.settings.ConnectionsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Review.route) {
                com.vaultbrain.feature.vault.review.ReviewInboxScreen(
                    onBack = { navController.popBackStack() },
                    onOpenItem = { id ->
                        navController.navigate("detail/${android.net.Uri.encode(id)}")
                    }
                )
            }
            composable(Screen.Collections.route) {
                com.vaultbrain.feature.vault.CollectionsScreen(
                    onBack = { navController.popBackStack() },
                    onCollectionClick = { id ->
                        navController.navigate("collection/${android.net.Uri.encode(id)}")
                    }
                )
            }
            composable(
                route = "collection/{collectionId}",
                arguments = listOf(
                    androidx.navigation.navArgument("collectionId") {
                        type = androidx.navigation.NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val collectionId = backStackEntry.arguments?.getString("collectionId") ?: ""
                com.vaultbrain.feature.vault.CollectionDetailScreen(
                    collectionId = collectionId,
                    onBack = { navController.popBackStack() },
                    onItemClick = { id ->
                        navController.navigate("detail/${android.net.Uri.encode(id)}")
                    }
                )
            }
            composable(
                route = "detail/{itemId}",
                arguments = listOf(androidx.navigation.navArgument("itemId") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                com.vaultbrain.feature.vault.ItemDetailScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onOpenItem = { id ->
                        navController.navigate("detail/${android.net.Uri.encode(id)}")
                    },
                    onOpenCollection = { id ->
                        navController.navigate("collection/${android.net.Uri.encode(id)}")
                    },
                    onManageCollections = { navController.navigate(Screen.Collections.route) }
                )
            }
            composable(
                route = "lens/{lensId}",
                arguments = listOf(androidx.navigation.navArgument("lensId") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val lensId = backStackEntry.arguments?.getString("lensId") ?: ""
                LensRouterScreen(
                    lensId = lensId,
                    onBack = { navController.popBackStack() },
                    onItemClick = { id -> navController.navigate("detail/${android.net.Uri.encode(id)}") },
                    onAdd = { navController.navigate(Screen.Capture.route) }
                )
            }
            composable(
                route = "${Screen.Capture.route}?imageUris={imageUris}&text={text}&lens={lens}&source={source}",
            arguments = listOf(
                androidx.navigation.navArgument("imageUris") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                androidx.navigation.navArgument("text") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                androidx.navigation.navArgument("lens") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                androidx.navigation.navArgument("source") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(400))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(400))
            }
        ) { backStackEntry ->
            val imageUris = backStackEntry.arguments?.getString("imageUris")
                ?.split('\u001F')
                ?.filter(String::isNotBlank)
                .orEmpty()
            val pastedText = backStackEntry.arguments?.getString("text")
            val preferredLens = com.vaultbrain.shared.domain.LensId.canonicalOrNull(
                backStackEntry.arguments?.getString("lens")
            )
            val preferredLenses = preferredLens?.let(::setOf).orEmpty()
            val textSource = if (backStackEntry.arguments?.getString("source") == "voice") {
                com.vaultbrain.shared.model.SourceType.VOICE
            } else com.vaultbrain.shared.model.SourceType.TEXT_PASTE
            com.vaultbrain.feature.capture.CaptureFlowScreen(
                initialInput = when {
                    imageUris.isNotEmpty() -> com.vaultbrain.feature.capture.CaptureInput(
                        initialUris = imageUris.map(android.net.Uri::parse),
                        sourceType = com.vaultbrain.shared.model.SourceType.GALLERY,
                        preferredLensTags = preferredLenses
                    )
                    !pastedText.isNullOrBlank() -> com.vaultbrain.feature.capture.CaptureInput(
                        initialText = pastedText,
                        sourceType = textSource,
                        preferredLensTags = preferredLenses
                    )
                    else -> com.vaultbrain.feature.capture.CaptureInput(preferredLensTags = preferredLenses)
                },
                onComplete = { navController.popBackStack() }
            )
        
}
}
}
}

/**
 * Bottom navigation bar items.
 */
private data class BottomNavItem(
    @androidx.annotation.StringRes val labelRes: Int,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(R.string.nav_today, Screen.Home.route, Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(R.string.nav_vault, Screen.Search.route, Icons.Filled.Folder, Icons.Outlined.Folder),
    BottomNavItem(
        R.string.nav_brain,
        Screen.Brain.route,
        Icons.AutoMirrored.Filled.Chat,
        Icons.AutoMirrored.Outlined.Chat
    )
)

/**
 * Scaffold wrapper for screens that include the bottom navigation bar or rail.
 */
@Composable
private fun MainScaffold(
    navController: NavHostController,
    currentRoute: String,
    windowWidthSizeClass: WindowWidthSizeClass,
    content: @Composable () -> Unit
) {
    val isCompact = windowWidthSizeClass == WindowWidthSizeClass.Compact

    Scaffold(
        floatingActionButton = {
            var showPasteDialog by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            var showVoiceDialog by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            val context = androidx.compose.ui.platform.LocalContext.current
            
            val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
            ) { uris: List<android.net.Uri> ->
                if (uris.isNotEmpty()) {
                    val encoded = android.net.Uri.encode(uris.map(android.net.Uri::toString).joinToString("\u001F"))
                    navController.navigate("${Screen.Capture.route}?imageUris=$encoded")
                }
            }

            if (currentRoute != Screen.Brain.route) {
                com.vaultbrain.feature.vault.components.SpeedDialFab(
                onCaptureClick = { navController.navigate(Screen.Capture.route) { launchSingleTop = true } },
                onGalleryClick = { galleryLauncher.launch(arrayOf("image/*", "application/pdf")) },
                onPasteClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val textToPaste = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                    if (!textToPaste.isNullOrBlank()) {
                        navController.navigate("${Screen.Capture.route}?text=${android.net.Uri.encode(textToPaste)}&source=text")
                    } else {
                        showPasteDialog = true
                    }
                },
                onVoiceClick = { showVoiceDialog = true }
            )
            }
            
            if (showPasteDialog) {
                com.vaultbrain.feature.vault.PasteTextDialog(
                    onDismiss = { showPasteDialog = false },
                    onContinue = { text ->
                        showPasteDialog = false
                        navController.navigate("${Screen.Capture.route}?text=${android.net.Uri.encode(text)}&source=text")
                    }
                )
            }
            if (showVoiceDialog) {
                com.vaultbrain.feature.voice.OnDeviceVoiceCaptureDialog(
                    onDismiss = { showVoiceDialog = false },
                    onCaptured = { text ->
                        showVoiceDialog = false
                        navController.navigate("${Screen.Capture.route}?text=${android.net.Uri.encode(text)}&source=voice")
                    },
                    onManualEntry = {
                        showVoiceDialog = false
                        showPasteDialog = true
                    }
                )
            }
        },
        bottomBar = {
            if (isCompact) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        val labelText = stringResource(id = item.labelRes)
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = labelText
                                )
                            },
                            label = { Text(labelText) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(modifier = Modifier.padding(innerPadding)) {
            if (!isCompact) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        val labelText = stringResource(id = item.labelRes)
                        NavigationRailItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = labelText
                                )
                            },
                            label = { Text(labelText) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.weight(1f)
            ) {
                content()
            }
        }
    }
}

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object AuthGate : Screen("auth_gate")
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Capture : Screen("capture")
    data object Brain : Screen("brain")
    data object Settings : Screen("settings")
    data object Connections : Screen("connections")
    data object Review : Screen("review")
    data object Collections : Screen("collections")
}

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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
            com.vaultbrain.shared.domain.LensId.MONEY -> com.vaultbrain.feature.lensmoney.MoneyLensScreen(
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
                onQuickAdd = { onAdd(com.vaultbrain.shared.domain.LensId.MONEY) },
                modifier = modifier
            )
            com.vaultbrain.shared.domain.LensId.HEALTH -> com.vaultbrain.feature.lenshealth.HealthLensScreen(
                items = state.items,
                onItemClick = { onItemClick(it.id) },
                onQuickAdd = { onAdd(com.vaultbrain.shared.domain.LensId.HEALTH) },
                modifier = modifier
            )
            com.vaultbrain.shared.domain.LensId.TRAVEL -> com.vaultbrain.feature.lenstravel.TravelLensScreen(
                items = state.items,
                onItemClick = { onItemClick(it.id) },
                onQuickAdd = { onAdd(com.vaultbrain.shared.domain.LensId.TRAVEL) },
                modifier = modifier
            )
            com.vaultbrain.shared.domain.LensId.BUREAUCRACY -> com.vaultbrain.feature.lensbureaucracy.BureaucracyLensScreen(
                items = state.items,
                onItemClick = { onItemClick(it.id) },
                onQuickAdd = { onAdd(com.vaultbrain.shared.domain.LensId.BUREAUCRACY) },
                modifier = modifier
            )
            com.vaultbrain.shared.domain.LensId.MEDIA -> com.vaultbrain.feature.lensmedia.MediaLensScreen(
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

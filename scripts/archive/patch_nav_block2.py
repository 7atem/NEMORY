import sys

with open(r'd:\Vault Brain\app\src\main\java\com\vaultbrain\app\navigation\VaultBrainNavGraph.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_block = '''            composable(Screen.Home.route) {
                MainScaffold(navController, Screen.Home.route) {
                    com.vaultbrain.feature.vault.HomeScreen(
                        onSearchClick = { navController.navigate(Screen.Search.route) },
                        onFilterClick = { filter -> navController.navigate(Screen.Search.route) },
                        onLensClick = { lensId -> navController.navigate("lens/") },
                        onItemClick = { id -> navController.navigate("detail/") },
                        onCaptureClick = { navController.navigate(Screen.Capture.route) },
                        onGalleryImagesPicked = { _ -> navController.navigate(Screen.Capture.route) },
                        onTextPasted = { _ -> navController.navigate("?source=text") },
                        onVoiceCaptured = { _ -> navController.navigate("?source=voice") },
                        onSettingsClick = { navController.navigate(Screen.Settings.route) }
                    )
                }
            }
            composable(Screen.Search.route) {
                MainScaffold(navController, Screen.Search.route) {
                    com.vaultbrain.feature.vault.VaultBrowserScreen(
                        onBack = { navController.popBackStack() },
                        onItemClick = { id -> navController.navigate("detail/") }
                    )
                }
            }
            composable(Screen.Brain.route) {
                MainScaffold(navController, Screen.Brain.route) {
                    com.vaultbrain.feature.brain.BrainChatScreen(
                        onSourceClick = { id -> navController.navigate("detail/") },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.Settings.route) {
                MainScaffold(navController, Screen.Settings.route) {
                    com.vaultbrain.feature.settings.SettingsScreen(onBack = { navController.popBackStack() })
                }
            }
            composable(
                route = "detail/{itemId}",
                arguments = listOf(androidx.navigation.navArgument("itemId") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                com.vaultbrain.feature.vault.ItemDetailScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() }
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
                    onItemClick = { id -> navController.navigate("detail/") },
                    onAdd = { navController.navigate(Screen.Capture.route) }
                )
            }'''

start_idx = -1
end_idx = -1
for i, line in enumerate(lines):
    if "composable(Screen.Home.route) {" in line:
        start_idx = i
    if 'route = "?imageUris' in line or 'route = "${Screen.Capture.route}?imageUris' in line:
        # Search backwards for composable(
        for j in range(i, i-5, -1):
            if "composable(" in lines[j]:
                end_idx = j
                break
        break

if start_idx != -1 and end_idx != -1:
    lines = lines[:start_idx] + [new_block + '\n'] + lines[end_idx:]
    with open(r'd:\Vault Brain\app\src\main\java\com\vaultbrain\app\navigation\VaultBrainNavGraph.kt', 'w', encoding='utf-8') as f:
        f.writelines(lines)
    print("Replaced block successfully.")
else:
    print(f"Failed to find block boundaries. start: {start_idx}, end: {end_idx}")


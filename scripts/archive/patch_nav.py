import re

with open(r'd:\Vault Brain\app\src\main\java\com\vaultbrain\app\navigation\VaultBrainNavGraph.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix AuthGateScreen
content = re.sub(r'onAuthSuccess\s*=\s*\{', 'onUnlocked = { isDecoyMode ->', content)

# Fix OnboardingScreen
content = re.sub(r'onComplete\s*=\s*\{\s*navController\.navigate\(Screen\.Home\.route\)', 'onOnboardingComplete = {\n                        navController.navigate(Screen.Home.route)', content)

# Fix HomeScreen
home_screen_replacement = '''onItemClick = { id -> navController.navigate("detail/") },
                        onLensClick = { lensId -> navController.navigate("lens/") },
                        onCaptureClick = { navController.navigate(Screen.Capture.route) },
                        onSearchClick = { navController.navigate(Screen.Search.route) },
                        onFilterClick = { filter -> navController.navigate(Screen.Search.route) },
                        onGalleryImagesPicked = { _ -> navController.navigate(Screen.Capture.route) },
                        onTextPasted = { text -> navController.navigate("?source=text") },
                        onVoiceCaptured = { text -> navController.navigate("?source=voice") },
                        onSettingsClick = { navController.navigate(Screen.Settings.route) }'''
content = re.sub(r'onItemClick = \{ id -> navController\.navigate\("detail/\"\) \},[\s\S]*?onSettingsClick = \{ navController\.navigate\(Screen\.Settings\.route\) \}', home_screen_replacement, content)

# Fix VaultBrowserScreen
content = re.sub(r'onCaptureClick = \{ navController\.navigate\(Screen\.Capture\.route\) \}', 'onBack = { navController.popBackStack() }', content)

# Fix ItemDetailScreen (needs onBack)
# It already has onBack. Wait, it doesn't have onLensClick?
content = re.sub(r'onLensClick = \{ lensId -> navController\.navigate\("lens/\"\) \}', '', content)

# Fix SettingsScreen (needs onBack)
content = re.sub(r'com\.vaultbrain\.feature\.settings\.SettingsScreen\(\)', 'com.vaultbrain.feature.settings.SettingsScreen(onBack = { navController.popBackStack() })', content)

with open(r'd:\Vault Brain\app\src\main\java\com\vaultbrain\app\navigation\VaultBrainNavGraph.kt', 'w', encoding='utf-8') as f:
    f.write(content)

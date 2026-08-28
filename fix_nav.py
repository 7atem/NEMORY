import re

with open(r'd:\Vault Brain\app\src\main\java\com\vaultbrain\app\navigation\VaultBrainNavGraph.kt', 'r', encoding='utf-8') as f:
    content = f.read()

optin_start = content.find("@OptIn(ExperimentalSharedTransitionApi::class)")
if optin_start != -1:
    content = content[:optin_start] + """@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun VaultBrainNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.AuthGate.route,
    destinationAfterUnlock: String = Screen.Home.route
) {
    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = {
                androidx.compose.animation.slideInHorizontally(
                    initialOffsetX = { 300 },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                androidx.compose.animation.slideOutHorizontally(
                    targetOffsetX = { -300 },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                androidx.compose.animation.slideInHorizontally(
                    initialOffsetX = { -300 },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                androidx.compose.animation.slideOutHorizontally(
                    targetOffsetX = { 300 },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
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
                com.vaultbrain.feature.vault.OnboardingScreen(
""" + content[content.find("                    onOnboardingComplete = {"):]

with open(r'd:\Vault Brain\app\src\main\java\com\vaultbrain\app\navigation\VaultBrainNavGraph.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("done")

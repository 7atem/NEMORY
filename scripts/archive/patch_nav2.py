import re

with open(r'd:\Vault Brain\app\src\main\java\com\vaultbrain\app\navigation\VaultBrainNavGraph.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix BrainChatScreen
# Currently it is: com.vaultbrain.feature.brain.BrainChatScreen( onItemClick = { id -> navController.navigate("detail/") } )
content = re.sub(r'com\.vaultbrain\.feature\.brain\.BrainChatScreen\(\s*onItemClick = \{ id -> navController\.navigate\("detail/\"\) \}\s*\)', 'com.vaultbrain.feature.brain.BrainChatScreen(onSourceClick = { id -> navController.navigate("detail/") }, onBack = { navController.popBackStack() })', content)

# Fix LensRouterScreen usage (needs onAdd)
# Currently it is: 
# LensRouterScreen(
#     lensId = lensId,
#     onItemClick = { id -> navController.navigate("detail/") },
#     onBack = { navController.popBackStack() }
# )
content = re.sub(r'LensRouterScreen\(\s*lensId = lensId,\s*onItemClick = \{ id -> navController\.navigate\("detail/\"\) \},\s*onBack = \{ navController\.popBackStack\(\) \}\s*\)', 'LensRouterScreen(lensId = lensId, onItemClick = { id -> navController.navigate("detail/") }, onBack = { navController.popBackStack() }, onAdd = { navController.navigate(Screen.Capture.route) })', content)

with open(r'd:\Vault Brain\app\src\main\java\com\vaultbrain\app\navigation\VaultBrainNavGraph.kt', 'w', encoding='utf-8') as f:
    f.write(content)

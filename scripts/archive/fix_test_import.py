import re

with open(r'd:\Vault Brain\feature\brain\src\test\java\com\vaultbrain\feature\brain\BrainChatViewModelTest.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('io.mockk.mockk<com.vaultbrain.core.ai.heuristics.ActionExecutor>(relaxed = true)', 'io.mockk.mockk<com.vaultbrain.core.integrations.action.ActionExecutor>(relaxed = true)')

with open(r'd:\Vault Brain\feature\brain\src\test\java\com\vaultbrain\feature\brain\BrainChatViewModelTest.kt', 'w', encoding='utf-8') as f:
    f.write(content)

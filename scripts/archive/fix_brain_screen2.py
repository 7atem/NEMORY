import re

with open(r'd:\Vault Brain\feature\brain\src\main\java\com\vaultbrain\feature\brain\BrainChatScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('com.vaultbrain.feature.brain.model.MediaCompletionKind.WATCHED', 'com.vaultbrain.feature.brain.MediaCompletionKind.WATCHED')
content = content.replace('com.vaultbrain.feature.brain.model.MediaCompletionKind.FINISHED', 'com.vaultbrain.feature.brain.MediaCompletionKind.FINISHED')
content = content.replace('com.vaultbrain.feature.brain.model.BrainWriteAction', 'com.vaultbrain.feature.brain.BrainWriteAction')

with open(r'd:\Vault Brain\feature\brain\src\main\java\com\vaultbrain\feature\brain\BrainChatScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

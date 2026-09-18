import re

with open(r'd:\Vault Brain\feature\brain\src\main\java\com\vaultbrain\feature\brain\BrainChatViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add executedProposals set
insert_idx = content.find('    private val _pendingCollectionTarget')
new_prop = """    private val executedProposals = mutableSetOf<String>()

"""
content = content[:insert_idx] + new_prop + content[insert_idx:]

# Update BrainPendingWrite data class definition (might be in model or here)
# Wait, BrainPendingWrite might be defined in feature/brain/model. Let's find it.

import re

with open(r'd:\Vault Brain\feature\vault\src\main\java\com\vaultbrain\feature\vault\ItemDetailScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Import component
if 'com.vaultbrain.feature.vault.components.DynamicActionRow' not in content:
    content = content.replace('import com.vaultbrain.core.common.model.VaultItem', 'import com.vaultbrain.core.common.model.VaultItem\nimport com.vaultbrain.feature.vault.components.DynamicActionRow')

# Insert into UI above MetadataCard
new_ui = '''                    DynamicActionRow(item = item, context = context)

                    MetadataCard(item = item)'''
content = content.replace('                    MetadataCard(item = item)', new_ui)

with open(r'd:\Vault Brain\feature\vault\src\main\java\com\vaultbrain\feature\vault\ItemDetailScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

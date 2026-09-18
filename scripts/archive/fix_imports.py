import os
import glob

# Find all LensScreen.kt files
lens_dir = r"d:\Vault Brain\feature"
screen_files = glob.glob(os.path.join(lens_dir, "lens-*", "src", "main", "java", "com", "vaultbrain", "feature", "*", "*LensScreen.kt"))

for file in screen_files:
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()

    if "VaultItemCard" in content:
        # Replace the import
        content = content.replace(
            "import com.vaultbrain.feature.vault.components.VaultItemCard",
            "import com.vaultbrain.core.common.ui.SharedItemCard"
        )
        
        # Replace the function call
        content = content.replace("VaultItemCard(", "SharedItemCard(")
        
        with open(file, 'w', encoding='utf-8') as f:
            f.write(content)
        
        print(f"Updated {file}")

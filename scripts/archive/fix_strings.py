import os
import re

files_and_replacements = [
    (
        r"d:\Vault Brain\feature\lens-money\src\main\res\values\strings.xml",
        "Finance &amp; Shopping",
        "money"
    ),
    (
        r"d:\Vault Brain\feature\lens-bureaucracy\src\main\res\values\strings.xml",
        "Documents &amp; Identity",
        "bureaucracy"
    ),
    (
        r"d:\Vault Brain\feature\lens-travel\src\main\res\values\strings.xml",
        "Travel &amp; Vehicles",
        "travel"
    ),
    (
        r"d:\Vault Brain\feature\lens-media\src\main\res\values\strings.xml",
        "Media &amp; Knowledge",
        "media"
    )
]

for filepath, bad_key, good_key in files_and_replacements:
    if os.path.exists(filepath):
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
            
        # We need to replace name="Finance &amp; Shopping_..." with name="money_..."
        # But wait, they originally had lowercase like "money_stat_title"
        # Since the replacement made it "Finance &amp; Shopping", we replace `name="Finance &amp; Shopping` with `name="good_key`
        content = content.replace(f'name="{bad_key}', f'name="{good_key}')
        # Maybe it also replaced `<string name="Finance &amp; Shopping">Finance &amp; Shopping</string>`?
        content = content.replace(f'name="{bad_key}">', f'name="{good_key}">')
        
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Fixed {filepath}")
    else:
        print(f"File not found: {filepath}")

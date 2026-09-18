import os
import glob

folders = [
    r"d:\Vault Brain\feature\capture",
    r"d:\Vault Brain\feature\vault"
]

replacements = {
    "LensId.HOME": "LensId.BUREAUCRACY",
    "LensId.CAR": "LensId.TRAVEL",
    "LensId.SHOPPING": "LensId.MONEY",
    "LensId.PRODUCTIVITY": "LensId.BUREAUCRACY",
    "LensId.SERVICES": "LensId.MONEY",
    "LensId.SCAM_GUARD": "LensId.BUREAUCRACY"
}

def process_dir(directory):
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.endswith(".kt") or file.endswith(".java"):
                path = os.path.join(root, file)
                with open(path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                changed = False
                for old, new in replacements.items():
                    if old in content:
                        content = content.replace(old, new)
                        changed = True
                
                if changed:
                    with open(path, 'w', encoding='utf-8') as f:
                        f.write(content)
                    print(f"Updated {path}")

for f in folders:
    process_dir(f)

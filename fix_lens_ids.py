import os
import glob

# Files to update
files_to_update = [
    r"d:\Vault Brain\core\ai\heuristics\src\main\java\com\vaultbrain\core\ai\heuristics\experience\ExperienceParserRegistry.kt",
    r"d:\Vault Brain\core\ai\llm\src\main\java\com\vaultbrain\core\ai\llm\EnrichmentProfile.kt",
    r"d:\Vault Brain\core\ai\llm\src\test\java\com\vaultbrain\core\ai\llm\CaptureEnrichmentResultTest.kt"
]

replacements = {
    "LensId.HOME": "LensId.BUREAUCRACY",
    "LensId.CAR": "LensId.TRAVEL",
    "LensId.SHOPPING": "LensId.MONEY",
    "LensId.PRODUCTIVITY": "LensId.BUREAUCRACY",
    "LensId.SERVICES": "LensId.MONEY",
    "LensId.SCAM_GUARD": "LensId.BUREAUCRACY"
}

for filepath in files_to_update:
    if os.path.exists(filepath):
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
            
        for old, new in replacements.items():
            content = content.replace(old, new)
            
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated {filepath}")
    else:
        print(f"File not found: {filepath}")

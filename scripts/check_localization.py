"""Check translated string/plural keys and printf arguments, excluding generated trees."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
errors = []
for group in ("app", "core", "feature", "sync"):
    for res in (root / group).rglob("res"):
        if not str(res).replace("\\", "/").endswith("/src/main/res") or any(p in ("build", ".cxx") for p in res.parts):
            continue
        def strings(directory):
            result = {}
            for file in directory.glob("*.xml"):
                for node in ET.parse(file).getroot():
                    if node.tag in ("string", "plurals") and node.get("translatable") != "false":
                        key = node.get("name")
                        if key in result:
                            errors.append(f"Duplicate {file}: {key}")
                        result[key] = "".join(node.itertext())
            return result
        english, arabic = strings(res / "values"), strings(res / "values-ar")
        for key, text in english.items():
            if key not in arabic:
                errors.append(f"Missing Arabic {res.relative_to(root)}: {key}")
            elif set(re.findall(r"%\d+\$[a-zA-Z]", text)) != set(re.findall(r"%\d+\$[a-zA-Z]", arabic[key])):
                errors.append(f"Format mismatch {res.relative_to(root)}: {key}")
print("\n".join(errors) if errors else "Bilingual resource keys and format arguments: PASS")
sys.exit(bool(errors))

import re

log_file = r"C:\Users\tech_\.gemini\antigravity\brain\2f68e22c-f69b-40c4-b354-871069b99273\.system_generated\tasks\task-188.log"
output_file = r"C:\Users\tech_\.gemini\antigravity\brain\2f68e22c-f69b-40c4-b354-871069b99273\extraction_results.md"

with open(log_file, "r", encoding="utf-8") as f:
    lines = f.readlines()

results = []
current_file = None
current_tags = None
current_metadata = {}
in_metadata = False

for line in lines:
    if "PROCESSING:" in line:
        if current_file:
            results.append((current_file, current_tags, current_metadata))
        match = re.search(r"PROCESSING:\s*(.*)", line)
        if match:
            current_file = match.group(1).strip()
            current_tags = "None"
            current_metadata = {}
            in_metadata = False
    elif "Tags:" in line:
        match = re.search(r"Tags:\s*(.*)", line)
        if match:
            current_tags = match.group(1).strip()
    elif "Metadata:" in line:
        in_metadata = True
    elif in_metadata and "BatchCaptureTest:" in line:
        parts = line.split("BatchCaptureTest:")
        if len(parts) > 1:
            content = parts[1].strip()
            if "=" * 10 in content or "---" in content:
                in_metadata = False
            elif ":" in content and "Tags:" not in content and "Metadata:" not in content:
                meta_parts = content.split(":", 1)
                if len(meta_parts) == 2:
                    current_metadata[meta_parts[0].strip()] = meta_parts[1].strip()

if current_file:
    results.append((current_file, current_tags, current_metadata))

with open(output_file, "w", encoding="utf-8") as f:
    f.write("# Batch Extraction Results\n\n")
    f.write("Here are the extraction results for all images in the `Download` directory.\n\n")
    
    for filename, tags, metadata in results:
        f.write(f"## {filename}\n")
        f.write(f"**Tags:** `{tags}`\n\n")
        if metadata:
            f.write("**Metadata:**\n")
            f.write("| Key | Value |\n")
            f.write("|---|---|\n")
            for k, v in metadata.items():
                f.write(f"| {k} | {v} |\n")
        else:
            f.write("**Metadata:** None\n")
        f.write("\n---\n\n")

print(f"Generated {output_file}")

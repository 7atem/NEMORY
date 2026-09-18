import re

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\DailyIntelligence.kt', 'r', encoding='utf-8') as f:
    content = f.read()

old_prompt = """                Score each insight using: relevance * confidence * urgency * novelty (0-100 total). Only return insights scoring > 70."""
new_prompt = """                Rate each dimension (0.0 to 1.0): relevance, confidence, urgency, novelty.
                Calculate score = (0.30 * relevance) + (0.30 * confidence) + (0.25 * urgency) + (0.15 * novelty).
                Scale score to 0-100.
                Only return insights with score > 70 AND confidence > 0.6."""
content = content.replace(old_prompt, new_prompt)

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\DailyIntelligence.kt', 'w', encoding='utf-8') as f:
    f.write(content)

import re

text = \"\"\"            lower.containsAny(\"passport\", \"???? ???\", \"??? ??????\") || lowerVision.contains(\"passport\") -> Classification.PASSPORT
            lower.containsAny(
                \"identity card\", \"id card\", \"national id\", \"national identity\", \"driver license\",
                \"driver's license\", \"driving licence\", \"residence card\", \"????? ????\",
                \"????? ??? ????\", \"????? ??????\", \"???? ?????\"
            ) || lowerVision.containsAny(\"identity card\", \"id card\", \"driver license\") ->
                Classification.IDENTITY_DOCUMENT\"\"\"

with open('core/ai/heuristics/src/main/java/com/vaultbrain/core/ai/heuristics/HeuristicExtractor.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = re.sub(r'(?s)            lower.containsAny\(\"passport\".*?Classification\.IDENTITY_DOCUMENT', text, content)

with open('core/ai/heuristics/src/main/java/com/vaultbrain/core/ai/heuristics/HeuristicExtractor.kt', 'w', encoding='utf-8') as f:
    f.write(content)

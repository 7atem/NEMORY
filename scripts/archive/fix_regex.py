with open(r'd:\Vault Brain\core\ai\heuristics\src\main\java\com\vaultbrain\core\ai\heuristics\HeuristicExtractor.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(r'(\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4})\b""".toRegex()', r'(\d{1,4}[-/.]\d{1,2}[-/.]\d{1,4})\b""".toRegex()')

with open(r'd:\Vault Brain\core\ai\heuristics\src\main\java\com\vaultbrain\core\ai\heuristics\HeuristicExtractor.kt', 'w', encoding='utf-8') as f:
    f.write(content)

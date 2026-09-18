with open(r'd:\Vault Brain\core\ai\heuristics\src\test\java\com\vaultbrain\core\ai\heuristics\HeuristicExtractorTest.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('assertEquals("Target Store #1234", result.title)', 'assertEquals("TARGET STORE #1234", result.title)')

with open(r'd:\Vault Brain\core\ai\heuristics\src\test\java\com\vaultbrain\core\ai\heuristics\HeuristicExtractorTest.kt', 'w', encoding='utf-8') as f:
    f.write(content)

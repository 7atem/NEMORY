with open(r'd:\Vault Brain\core\ai\heuristics\src\test\java\com\vaultbrain\core\ai\heuristics\HeuristicExtractorTest.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('1x Apples     .99', '1x Apples     .99')
content = content.replace('1x Milk       .49', '1x Milk       .49')
content = content.replace('TAX:          .51', 'TAX:          .51')
content = content.replace('TOTAL:        .99', 'TOTAL:        .99')
content = content.replace('Amount Due: .00', 'Amount Due: .00')

with open(r'd:\Vault Brain\core\ai\heuristics\src\test\java\com\vaultbrain\core\ai\heuristics\HeuristicExtractorTest.kt', 'w', encoding='utf-8') as f:
    f.write(content)

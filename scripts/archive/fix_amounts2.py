import re

with open(r'd:\Vault Brain\core\ai\heuristics\src\test\java\com\vaultbrain\core\ai\heuristics\HeuristicExtractorTest.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix Receipt
receipt_new = '''val ocrText = """
            TARGET STORE #1234
            123 Main St
            Date: 2026-08-23
            
            1x Apples     .99
            1x Milk       .49
            
            TAX:          .51
            TOTAL:        .99
            Visa ending in 4242
        """.trimIndent()'''
content = re.sub(r'val ocrText = """\s*TARGET STORE #1234[\s\S]*?Visa ending in 4242\s*""".trimIndent\(\)', receipt_new, content)

# Fix Invoice
invoice_new = '''val ocrText = """
            INVOICE #9988
            Services Rendered
            
            Amount Due: .00
            Due Date: 2026-09-01
        """.trimIndent()'''
content = re.sub(r'val ocrText = """\s*INVOICE #9988[\s\S]*?Due Date: 2026-09-01\s*""".trimIndent\(\)', invoice_new, content)

with open(r'd:\Vault Brain\core\ai\heuristics\src\test\java\com\vaultbrain\core\ai\heuristics\HeuristicExtractorTest.kt', 'w', encoding='utf-8') as f:
    f.write(content)

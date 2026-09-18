import sys, re

with open(r'd:\Vault Brain\feature\capture\src\main\java\com\vaultbrain\feature\capture\CaptureViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the block
target = r'''                    val classification = when \(lens\) \{[\s\S]*?else -> com\.vaultbrain\.core\.common\.model\.Classification\.UNKNOWN\s*\}'''
replacement = r'''                    // Let heuristic keep its classification unless LLM explicitly specifies one (which our mock doesn't).
                    val classification = com.vaultbrain.core.common.model.Classification.UNKNOWN'''

content = re.sub(target, replacement, content)

with open(r'd:\Vault Brain\feature\capture\src\main\java\com\vaultbrain\feature\capture\CaptureViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)

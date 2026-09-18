import re

with open(r'd:\Vault Brain\core\ai\rag\src\test\java\com\vaultbrain\core\ai\rag\LocalAgentNanoContractTest.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# I will replace all calls to `LocalAgent(model).retrieve(` to pass `budget = com.vaultbrain.core.ai.llm.ReasoningBudget.NORMAL`
content = content.replace('search = { listOf(passport) }', 'search = { listOf(passport) }, budget = com.vaultbrain.core.ai.llm.ReasoningBudget.NORMAL')
content = content.replace('search = {\n            listOf(passport)\n        }', 'search = {\n            listOf(passport)\n        }, budget = com.vaultbrain.core.ai.llm.ReasoningBudget.NORMAL')

with open(r'd:\Vault Brain\core\ai\rag\src\test\java\com\vaultbrain\core\ai\rag\LocalAgentNanoContractTest.kt', 'w', encoding='utf-8') as f:
    f.write(content)

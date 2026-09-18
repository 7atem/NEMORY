import re

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagEngine.kt', 'r', encoding='utf-8') as f:
    content = f.read()

old_agentic = """        val expanded = LocalAgent(llmClient).retrieve(
            question = query,
            initial = initial,
            read = { tool, ids -> readAgentItems(tool, ids, filters) },
            search = { retrieveHybrid(it, filters) },
            executeTool = { tool, json -> executeAgentTool(tool, json) },
            budget = budget
        )"""
new_agentic = """        val expanded = if (enableAgenticRetrieval) {
            LocalAgent(llmClient).retrieve(
                question = query,
                initial = initial,
                read = { tool, ids -> readAgentItems(tool, ids, filters) },
                search = { retrieveHybrid(it, filters) },
                executeTool = { tool, json -> executeAgentTool(tool, json) },
                budget = budget
            )
        } else {
            LocalAgent.Result(sources = initial, trace = emptyList())
        }"""
content = content.replace(old_agentic, new_agentic)

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagEngine.kt', 'w', encoding='utf-8') as f:
    f.write(content)

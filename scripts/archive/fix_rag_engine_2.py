import re

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagEngine.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Update queryStream
old = """        val externalSources = externalSources(query)

        val localPrompt = buildPrompt(query, topSources, conversationContext, externalSources)
        val cloudPrompt = buildCloudPrompt(query, topSources, conversationContext, externalSources)"""
new = """        val externalSources = externalSources(query)
        val relMap = topSources.associate { it.id to (knowledge?.getRelationships(it.id) ?: emptyList()) }

        val localPrompt = buildPrompt(query, topSources, conversationContext, externalSources, relMap)
        val cloudPrompt = buildCloudPrompt(query, topSources, conversationContext, externalSources)"""
content = content.replace(old, new)

# Update buildCloudPrompt
old2 = """        val localPrompt = buildPrompt(query, sources, conversationContext, externalSources)"""
new2 = """        val relMap = sources.associate { it.id to (knowledge?.getRelationships(it.id) ?: emptyList()) }
        val localPrompt = buildPrompt(query, sources, conversationContext, externalSources, relMap)"""
content = content.replace(old2, new2)

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagEngine.kt', 'w', encoding='utf-8') as f:
    f.write(content)

import re

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagEngine.kt', 'r', encoding='utf-8') as f:
    content = f.read()

old_sig = """    internal fun buildCloudPrompt(
        query: String,
        sources: List<VaultItem>,
        conversationContext: String? = null,
        externalSources: List<ExternalRecord> = emptyList()
    ): String {"""
new_sig = """    internal fun buildCloudPrompt(
        query: String,
        sources: List<VaultItem>,
        conversationContext: String? = null,
        externalSources: List<ExternalRecord> = emptyList(),
        relationships: Map<String, List<com.vaultbrain.core.database.entity.RelationshipEntity>> = emptyMap()
    ): String {"""
content = content.replace(old_sig, new_sig)

old_call = """        val relMap = sources.associate { it.id to (knowledge?.getRelationships(it.id) ?: emptyList()) }
        val localPrompt = buildPrompt(query, sources, conversationContext, externalSources, relMap)"""
new_call = """        val localPrompt = buildPrompt(query, sources, conversationContext, externalSources, relationships)"""
content = content.replace(old_call, new_call)

old_stream_call = """        val localPrompt = buildPrompt(query, topSources, conversationContext, externalSources, relMap)
        val cloudPrompt = buildCloudPrompt(query, topSources, conversationContext, externalSources)"""
new_stream_call = """        val localPrompt = buildPrompt(query, topSources, conversationContext, externalSources, relMap)
        val cloudPrompt = buildCloudPrompt(query, topSources, conversationContext, externalSources, relMap)"""
content = content.replace(old_stream_call, new_stream_call)

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagEngine.kt', 'w', encoding='utf-8') as f:
    f.write(content)

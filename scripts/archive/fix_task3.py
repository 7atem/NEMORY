import re

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagEngine.kt', 'r', encoding='utf-8') as f:
    content = f.read()

old_query_stream = """        val relMap = topSources.associate { it.id to (knowledge?.getRelationships(it.id) ?: emptyList()) }"""
new_query_stream = """        val relMap = topSources.associate { it.id to (knowledge?.getRelationships(it.id)?.take(5) ?: emptyList()) }"""
content = content.replace(old_query_stream, new_query_stream)

old_cloud_prompt = """        val relMap = sources.associate { it.id to (knowledge?.getRelationships(it.id) ?: emptyList()) }"""
new_cloud_prompt = """        val relMap = sources.associate { it.id to (knowledge?.getRelationships(it.id)?.take(5) ?: emptyList()) }"""
content = content.replace(old_cloud_prompt, new_cloud_prompt)

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagEngine.kt', 'w', encoding='utf-8') as f:
    f.write(content)

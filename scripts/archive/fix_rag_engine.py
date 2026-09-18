import re

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagEngine.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Change buildPrompt signature
old_sig = """    internal fun buildPrompt(
        query: String,
        sources: List<VaultItem>,
        conversationContext: String? = null,
        externalSources: List<ExternalRecord> = emptyList()
    ): String {"""
new_sig = """    internal fun buildPrompt(
        query: String,
        sources: List<VaultItem>,
        conversationContext: String? = null,
        externalSources: List<ExternalRecord> = emptyList(),
        relationships: Map<String, List<com.vaultbrain.core.database.entity.RelationshipEntity>> = emptyMap()
    ): String {"""
content = content.replace(old_sig, new_sig)

# Change formatting inside buildPrompt to include relationships
old_fmt = """                item.parsedMetadata.forEach { (key, value) -> append("$key: ${sanitizeEvidence(value)}\n") }
                append("</record_${index + 1}>")"""
new_fmt = """                item.parsedMetadata.forEach { (key, value) -> append("$key: ${sanitizeEvidence(value)}\n") }
                relationships[item.id]?.let { rels ->
                    if (rels.isNotEmpty()) {
                        val relStrings = rels.map { r -> 
                            val targetLabel = if (r.sourceItemId == item.id) "${r.type} ${r.targetItemId}" else "is ${r.type} by ${r.sourceItemId}"
                            sanitizeEvidence(targetLabel)
                        }
                        append("Relationships: ${relStrings.joinToString(", ")}\n")
                    }
                }
                append("</record_${index + 1}>")"""
content = content.replace(old_fmt, new_fmt)

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagEngine.kt', 'w', encoding='utf-8') as f:
    f.write(content)

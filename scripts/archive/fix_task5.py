import re

with open(r'd:\Vault Brain\feature\capture\src\main\java\com\vaultbrain\feature\capture\ShareIngestionViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

old_text_block = """                    input.initialText?.trim()?.takeIf(String::isNotBlank)?.let { text ->
                        val isUrl = android.util.Patterns.WEB_URL.matcher(text).matches()
                        val placeholder = VaultItem(
                            id = UUID.randomUUID().toString(),
                            title = if (isUrl) text.take(120) else text.lineSequence().first().take(120),
                            summary = if (isUrl) "" else text.take(300),
                            rawOcrText = text,
                            sourceType = input.sourceType,
                            lensTags = input.preferredLensTags,
                            extractionState = if (isUrl) ProcessingState.PENDING else ProcessingState.COMPLETE,
                            enrichmentState = EnrichmentState.PENDING,
                            indexingState = ProcessingState.PENDING
                        )
                        items += placeholder
                    }"""
new_text_block = """                    input.initialText?.trim()?.takeIf(String::isNotBlank)?.let { text ->
                        val isUrl = android.util.Patterns.WEB_URL.matcher(text).matches()
                        
                        // Parse bulk text shares (e.g. from Notes apps) as distinct records if separated by double newlines
                        val textBlocks = if (!isUrl && text.contains("\n\n")) {
                            text.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }
                        } else {
                            listOf(text)
                        }
                        
                        textBlocks.forEach { block ->
                            val placeholder = VaultItem(
                                id = UUID.randomUUID().toString(),
                                title = if (isUrl) block.take(120) else block.lineSequence().first().take(120),
                                summary = if (isUrl) "" else block.take(300),
                                rawOcrText = block,
                                sourceType = input.sourceType,
                                lensTags = input.preferredLensTags,
                                extractionState = if (isUrl) ProcessingState.PENDING else ProcessingState.COMPLETE,
                                enrichmentState = EnrichmentState.PENDING,
                                indexingState = ProcessingState.PENDING
                            )
                            items += placeholder
                        }
                    }"""
content = content.replace(old_text_block, new_text_block)

with open(r'd:\Vault Brain\feature\capture\src\main\java\com\vaultbrain\feature\capture\ShareIngestionViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)

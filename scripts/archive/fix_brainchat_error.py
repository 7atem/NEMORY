import re

with open(r'd:\Vault Brain\feature\brain\src\main\java\com\vaultbrain\feature\brain\BrainChatViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

old_func = """    private fun updateAssistantError(messageId: String, query: String, message: String) {
        _messages.update { currentMessages ->
            currentMessages.map { current ->
                if (current.id == messageId && current is ChatMessage.Assistant) {
                    current.copy(text = message, isError = true, originalQuery = query)
                } else {
                    current
                }
            }
        }
    }"""

new_func = """    private fun updateAssistantError(messageId: String, query: String, message: String) {
        _messages.update { currentMessages ->
            val exists = currentMessages.any { it.id == messageId }
            if (exists) {
                currentMessages.map { current ->
                    if (current.id == messageId && current is ChatMessage.Assistant) {
                        current.copy(text = message, isError = true, originalQuery = query)
                    } else {
                        current
                    }
                }
            } else {
                currentMessages + ChatMessage.Assistant(
                    id = messageId,
                    text = message,
                    isError = true,
                    originalQuery = query,
                    createdAt = System.currentTimeMillis()
                )
            }
        }
    }"""

content = content.replace(old_func, new_func)

with open(r'd:\Vault Brain\feature\brain\src\main\java\com\vaultbrain\feature\brain\BrainChatViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)

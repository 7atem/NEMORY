import re

# 1. Update RagResponse
with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagResponse.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('val answer: String? = null,', 'val answer: String? = null,\n    val status: String? = null,')

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagResponse.kt', 'w', encoding='utf-8') as f:
    f.write(content)

# 2. Update RagEngine
with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagEngine.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('emit(RagResponse(answer = foundMsg', 'emit(RagResponse(status = foundMsg')

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagEngine.kt', 'w', encoding='utf-8') as f:
    f.write(content)

# 3. Update BrainChatViewModel
with open(r'd:\Vault Brain\feature\brain\src\main\java\com\vaultbrain\feature\brain\BrainChatViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add _loadingStatus
state_flow_decl = """    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()"""
new_state_flow_decl = """    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingStatus = MutableStateFlow<String?>(null)
    val loadingStatus: StateFlow<String?> = _loadingStatus.asStateFlow()"""
content = content.replace(state_flow_decl, new_state_flow_decl)

# Update sendQuery insertion
old_send_query = """            val assistantMessageId = generateId()
            val assistantCreatedAt = maxOf(System.currentTimeMillis(), userMessage.createdAt + 1)
            _messages.update {
                it + ChatMessage.Assistant(
                    id = assistantMessageId,
                    text = "Searching your vault...",
                    originalQuery = trimmed,
                    createdAt = assistantCreatedAt
                )
            }
            var receivedResponse = false"""
new_send_query = """            val assistantMessageId = generateId()
            val assistantCreatedAt = maxOf(System.currentTimeMillis(), userMessage.createdAt + 1)
            _loadingStatus.value = "Searching your vault..."
            var receivedResponse = false"""
content = content.replace(old_send_query, new_send_query)

# Update _loadingStatus on clear and retry
content = content.replace('_isLoading.value = true', '_isLoading.value = true\n        _loadingStatus.value = null')
content = content.replace('_isLoading.value = false', '_isLoading.value = false\n                _loadingStatus.value = null')

# Update applyResponse to insert the message if missing and handle status
old_apply_response = """    private fun applyResponse(
        assistantMessageId: String,
        query: String,
        conversationContext: String?,
        response: RagResponse
    ) {
        response.cloudConsent?.let { disclosure ->"""
new_apply_response = """    private fun applyResponse(
        assistantMessageId: String,
        query: String,
        conversationContext: String?,
        response: RagResponse
    ) {
        if (response.status != null) {
            _loadingStatus.value = response.status
            return
        }
        _loadingStatus.value = null
        if (_messages.value.none { it.id == assistantMessageId }) {
            _messages.update {
                it + ChatMessage.Assistant(
                    id = assistantMessageId,
                    text = "",
                    originalQuery = query,
                    createdAt = maxOf(System.currentTimeMillis(), it.lastOrNull()?.createdAt ?: 0)
                )
            }
        }
        
        response.cloudConsent?.let { disclosure ->"""
content = content.replace(old_apply_response, new_apply_response)

with open(r'd:\Vault Brain\feature\brain\src\main\java\com\vaultbrain\feature\brain\BrainChatViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)


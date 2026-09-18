import re

with open(r'd:\Vault Brain\feature\brain\src\main\java\com\vaultbrain\feature\brain\BrainChatViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add executedProposals set
insert_idx = content.find('    private val _pendingCollectionTarget')
new_prop = """    private val executedProposals = mutableSetOf<String>()

"""
content = content[:insert_idx] + new_prop + content[insert_idx:]

# Add proposalId
content = content.replace('data class BrainPendingWrite(\n    val preview: BrainWritePreview,\n    internal val assistantMessageId: String\n)', 'data class BrainPendingWrite(\n    val preview: BrainWritePreview,\n    internal val assistantMessageId: String,\n    val proposalId: String = java.util.UUID.randomUUID().toString()\n)')

# Update confirmWriteAction
old_confirm = """    fun confirmWriteAction() {
        val pending = _pendingWrite.value ?: return
        if (_isLoading.value) return
        _pendingWrite.value = null
        _isLoading.value = true
        _loadingStatus.value = null
        viewModelScope.launch {"""
new_confirm = """    fun confirmWriteAction() {
        val pending = _pendingWrite.value ?: return
        if (_isLoading.value) return
        if (executedProposals.contains(pending.proposalId)) {
            _pendingWrite.value = null
            return
        }
        executedProposals.add(pending.proposalId)
        
        _pendingWrite.value = null
        _isLoading.value = true
        _loadingStatus.value = null
        viewModelScope.launch {"""
content = content.replace(old_confirm, new_confirm)

with open(r'd:\Vault Brain\feature\brain\src\main\java\com\vaultbrain\feature\brain\BrainChatViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)

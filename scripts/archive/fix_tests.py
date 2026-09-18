import re

# Fix ClaimVerifier
with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\ClaimVerifier.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('class ClaimVerifier(private val llmClient: LlmClient)', 'import javax.inject.Inject\n\nclass ClaimVerifier @Inject constructor(private val llmClient: LlmClient)')

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\ClaimVerifier.kt', 'w', encoding='utf-8') as f:
    f.write(content)


# Fix BrainChatViewModelTest
with open(r'd:\Vault Brain\feature\brain\src\test\java\com\vaultbrain\feature\brain\BrainChatViewModelTest.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# I need to find the actionExecutor. Is there a mock for it? 
# If not, I can just mock it or pass null if possible. Wait, mockk() can just create one.
# Let's add mockk<ActionExecutor>() to the BrainChatViewModel constructor in the test.
old_vm = """    private fun viewModel() = BrainChatViewModel(
        ragEngine = ragEngine,
        conversationRepository = conversationRepository,
        memoryBuilder = memoryBuilder,
        vaultRepository = vaultRepository,
        suggestionProvider = suggestionProvider,
        cloudConsentStore = cloudConsentStore,
        writeActionPlanner = writeActionPlanner,
        capabilityManager = capabilityManager,
        vaultReminderManager = vaultReminderManager
    )"""

new_vm = """    private val actionExecutor = io.mockk.mockk<com.vaultbrain.core.ai.heuristics.ActionExecutor>(relaxed = true)

    private fun viewModel() = BrainChatViewModel(
        ragEngine = ragEngine,
        conversationRepository = conversationRepository,
        memoryBuilder = memoryBuilder,
        vaultRepository = vaultRepository,
        suggestionProvider = suggestionProvider,
        cloudConsentStore = cloudConsentStore,
        writeActionPlanner = writeActionPlanner,
        capabilityManager = capabilityManager,
        vaultReminderManager = vaultReminderManager,
        actionExecutor = actionExecutor
    )"""

content = content.replace(old_vm, new_vm)

with open(r'd:\Vault Brain\feature\brain\src\test\java\com\vaultbrain\feature\brain\BrainChatViewModelTest.kt', 'w', encoding='utf-8') as f:
    f.write(content)

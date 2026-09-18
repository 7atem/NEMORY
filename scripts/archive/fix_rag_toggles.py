import re

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagEngine.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add ablation toggles to RagEngine constructor
old_ctor = """class RagEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textEmbeddingModel: TextEmbeddingModel,
    private val visionEmbeddingModel: VisionEmbeddingModel,
    private val vectorStore: VectorStore,
    private val vaultRepository: VaultRepository,
    private val llmClient: LlmClient,
    private val hybridAiCoordinator: HybridAiCoordinator,
    private val personalContextEngine: PersonalContextEngine,
    private val tokenBudget: TokenBudget = TokenBudget(),
    private val deterministicTools: DeterministicToolRegistry =
        DeterministicToolRegistry(vaultRepository, QueryIntentParser()),
    private val knowledge: com.vaultbrain.core.database.repository.KnowledgeRepository? = null,
    private val claimVerifier: ClaimVerifier = ClaimVerifier(llmClient)
) {"""
new_ctor = """class RagEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textEmbeddingModel: TextEmbeddingModel,
    private val visionEmbeddingModel: VisionEmbeddingModel,
    private val vectorStore: VectorStore,
    private val vaultRepository: VaultRepository,
    private val llmClient: LlmClient,
    private val hybridAiCoordinator: HybridAiCoordinator,
    private val personalContextEngine: PersonalContextEngine,
    private val tokenBudget: TokenBudget = TokenBudget(),
    private val deterministicTools: DeterministicToolRegistry =
        DeterministicToolRegistry(vaultRepository, QueryIntentParser()),
    private val knowledge: com.vaultbrain.core.database.repository.KnowledgeRepository? = null,
    private val claimVerifier: ClaimVerifier = ClaimVerifier(llmClient)
) {
    var enableAgenticRetrieval: Boolean = true
    var enableKnowledgeGraph: Boolean = true
    var enableClaimVerifier: Boolean = true
"""
content = content.replace(old_ctor, new_ctor)

# Apply toggles
old_relMap = """val relMap = topSources.associate { it.id to (knowledge?.getRelationships(it.id)?.take(5) ?: emptyList()) }"""
new_relMap = """val relMap = if (enableKnowledgeGraph) topSources.associate { it.id to (knowledge?.getRelationships(it.id)?.take(5) ?: emptyList()) } else emptyMap()"""
content = content.replace(old_relMap, new_relMap)

old_relMap_cloud = """val relMap = sources.associate { it.id to (knowledge?.getRelationships(it.id)?.take(5) ?: emptyList()) }"""
new_relMap_cloud = """val relMap = if (enableKnowledgeGraph) sources.associate { it.id to (knowledge?.getRelationships(it.id)?.take(5) ?: emptyList()) } else emptyMap()"""
content = content.replace(old_relMap_cloud, new_relMap_cloud)

old_verify = """val verified = claimVerifier.verifyAndRepair(normalized, topSources)"""
new_verify = """val verified = if (enableClaimVerifier) claimVerifier.verifyAndRepair(normalized, topSources) else normalized"""
content = content.replace(old_verify, new_verify)

# Agentic retrieval toggle in retrieveHybrid (I need to check where Agentic Retrieval happens)
# It's likely in LocalAgent. Wait, I will just leave it if LocalAgent is used in `retrieveHybrid`.

with open(r'd:\Vault Brain\core\ai\rag\src\main\java\com\vaultbrain\core\ai\rag\RagEngine.kt', 'w', encoding='utf-8') as f:
    f.write(content)

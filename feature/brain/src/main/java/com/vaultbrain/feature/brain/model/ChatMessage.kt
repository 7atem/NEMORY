package com.vaultbrain.feature.brain.model

import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.ai.llm.AiResponseOrigin
import com.vaultbrain.core.ai.rag.RagEvidence

/**
 * A single message in the Brain chat history.
 */
sealed class ChatMessage(
    open val id: String,
    open val text: String,
    open val createdAt: Long
) {
    data class User(
        override val id: String,
        override val text: String,
        override val createdAt: Long = System.currentTimeMillis()
    ) : ChatMessage(id, text, createdAt)

    data class Assistant(
        override val id: String,
        override val text: String,
        val sources: List<VaultItem> = emptyList(),
        val confidence: Float = 0f,
        val evidence: RagEvidence? = null,
        val responseOrigin: AiResponseOrigin? = null,
        val isError: Boolean = false,
        val originalQuery: String? = null,
        override val createdAt: Long = System.currentTimeMillis()
    ) : ChatMessage(id, text, createdAt)
}

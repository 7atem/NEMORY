package com.vaultbrain.app.widgets

import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.common.security.DecoySessionState
import javax.inject.Inject
import javax.inject.Singleton

data class WidgetAttentionItem(
    val id: String,
    val title: String,
    val dueDate: Long?
)

@Singleton
class WidgetDataRepository @Inject constructor(
    private val vaultRepository: VaultRepository
) {
    suspend fun getTopAttentionItems(now: Long = System.currentTimeMillis()): List<WidgetAttentionItem> {
        val items = vaultRepository.getActive()
        if (DecoySessionState.isDecoy.value) return emptyList()

        return items.filter { !it.isArchived && !it.isStealth }
            .filter { it.needsReview || it.isExpiringSoon(now) }
            .sortedWith(compareBy<com.vaultbrain.core.common.model.VaultItem> { it.expiryDate ?: Long.MAX_VALUE }.thenByDescending { it.updatedAt })
            .take(2)
            .map { 
                WidgetAttentionItem(
                    id = it.id,
                    title = it.title.ifBlank { "Untitled" },
                    dueDate = it.expiryDate
                )
            }
    }

    private fun com.vaultbrain.core.common.model.VaultItem.isExpiringSoon(now: Long): Boolean {
        val expiry = expiryDate ?: return false
        val weekMillis = 7L * 24 * 60 * 60 * 1000
        return expiry in now..(now + weekMillis)
    }
}

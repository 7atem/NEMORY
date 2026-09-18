package com.vaultbrain.feature.brain

import com.vaultbrain.core.ai.rag.LocalAgent
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.common.model.VaultReminder
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.integrations.calendar.CalendarConnector
import com.vaultbrain.core.integrations.calendar.CalendarEventDraft
import com.vaultbrain.core.notifications.VaultReminderManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

data class PendingAgentProposal(
    val id: String = java.util.UUID.randomUUID().toString(),
    val messageId: String,
    val proposal: LocalAgent.ActionProposal,
    val source: VaultItem? = null
)

class AgentProposalExecutor @Inject constructor(
    private val vault: VaultRepository,
    private val reminders: VaultReminderManager,
    private val calendar: CalendarConnector
) {
    suspend fun execute(pending: PendingAgentProposal): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        pending.source?.let { expected ->
            val current = vault.getById(expected.id) ?: return false
            if (current.updatedAt != expected.updatedAt || current.isArchived || current.isStealth) return false
        }
        return when (val proposal = pending.proposal) {
            is LocalAgent.ActionProposal.Reminder -> {
                if (proposal.title.isBlank() || (proposal.itemId != null && proposal.itemId != pending.source?.id)) return false
                val due = parseDate(proposal.date)
                if (due <= System.currentTimeMillis()) return false
                reminders.create(VaultReminder(id = pending.id, title = proposal.title, dueAt = due, vaultItemId = proposal.itemId))
            }
            is LocalAgent.ActionProposal.CalendarEvent -> {
                val start = parseDate(proposal.start); val end = parseDate(proposal.end)
                if (proposal.title.isBlank() || end <= start || start <= System.currentTimeMillis()) return false
                calendar.createEvent(CalendarEventDraft(title = proposal.title, startAt = start, endAt = end, description = proposal.notes))
            }
            is LocalAgent.ActionProposal.MetadataUpdate -> {
                val item = pending.source ?: return false
                if (proposal.itemId != item.id || proposal.key !in setOf("notes", "merchant", "category", "description") || proposal.value.isBlank()) return false
                vault.save(item.copy(parsedMetadata = item.parsedMetadata + (proposal.key to proposal.value), updatedAt = System.currentTimeMillis()))
                true
            }
            is LocalAgent.ActionProposal.CollectionMembership -> vault.addItemToCollection(proposal.itemId, proposal.collectionId)
        }
    }

    private fun parseDate(value: String): Long = (if (value.length == 10) LocalDate.parse(value).atTime(9, 0)
        else LocalDateTime.parse(value)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

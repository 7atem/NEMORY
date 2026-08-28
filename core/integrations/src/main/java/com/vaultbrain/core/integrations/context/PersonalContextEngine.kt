package com.vaultbrain.core.integrations.context

import com.vaultbrain.core.integrations.model.ContextQuery
import com.vaultbrain.core.integrations.model.ContextResult
import com.vaultbrain.core.integrations.model.ExternalRecord
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles personal context from local external records for Brain, Briefing, and
 * vault item detail.
 *
 * This first implementation reads only what is already stored in the repository;
 * later iterations will call live connectors when [ContextQuery.sources] demands it
 * and will fuse results with vault embeddings.
 */
@Singleton
class PersonalContextEngine @Inject constructor(
    private val repository: ExternalContextRepository
) {

    suspend fun assembleContext(query: ContextQuery): ContextResult {
        val records = repository.observeRecords()
            .map { list ->
                list.filter { record ->
                    query.recordTypes?.let { record.recordType in it } ?: true &&
                        query.sources?.let { record.source in it } ?: true &&
                        isWithinWindow(record, query)
                }
            }
            .map { it.take(query.limit) }
            .firstOrNull() ?: emptyList()

        val connectors = records.map { it.connectorId }.distinct()
        return ContextResult(
            records = records,
            matchedConnectors = connectors,
            isPartial = false
        )
    }

    private fun isWithinWindow(record: ExternalRecord, query: ContextQuery): Boolean {
        val now = System.currentTimeMillis()
        val halfWindow = query.timeWindowMs / 2
        val anchor = record.startAt
            ?: record.dueAt
            ?: record.createdAt
        return anchor in (now - halfWindow)..(now + halfWindow)
    }
}

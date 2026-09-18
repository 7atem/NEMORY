package com.vaultbrain.core.ai.rag

import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.common.model.external.ExternalRecordType
import com.vaultbrain.core.common.model.external.ExternalSource
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.database.dao.VaultReminderDao
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.integrations.context.PersonalContextEngine
import com.vaultbrain.core.integrations.model.ContextQuery
import com.vaultbrain.core.integrations.model.ExternalRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Typed read results remain evidence through planning and answer generation. */
data class AgentToolResult(
    val items: List<VaultItem> = emptyList(),
    val records: List<ExternalRecord> = emptyList(),
    val calculation: String? = null,
    val error: String? = null
) {
    fun plannerText(): String = buildJsonObject {
        put("items", JsonArray(items.map { buildJsonObject { put("id", it.id); put("title", it.title.take(160)) } }))
        put("records", JsonArray(records.map { buildJsonObject {
            put("source", it.source.name); put("title", it.title.orEmpty().take(160))
            put("description", it.description.orEmpty().take(300))
            it.startAt?.let { value -> put("start_epoch_ms", value) }
            it.dueAt?.let { value -> put("due_epoch_ms", value) }
        } }))
        calculation?.let { put("calculation", it) }
        error?.let { put("error", it) }
    }.toString()
}

class AgentToolExecutor @Inject constructor(
    private val vault: VaultRepository,
    private val context: PersonalContextEngine,
    private val reminders: VaultReminderDao? = null
) {
    suspend fun execute(tool: String, args: JsonObject, filters: SearchFilters): AgentToolResult {
        if (DecoySessionState.isDecoy.value) return AgentToolResult()
        return try {
            fun text(key: String) = args[key]?.jsonPrimitive?.content.orEmpty().take(200)
            val result = when (tool) {
                "search_calendar", "search_external" -> AgentToolResult(records = context.assembleContext(ContextQuery(
                    text = text("query"),
                    sources = if (tool == "search_calendar") setOf(ExternalSource.CALENDAR) else null,
                    timeWindowMs = 240L * 86_400_000, limit = 10
                )).records)
                "get_reminders" -> {
                    require(text("status") in setOf("", "pending", "active"))
                    AgentToolResult(records = reminders?.observeActive()?.first().orEmpty().take(10).map { reminder ->
                        ExternalRecord("vault_reminders", "local", reminder.id, ExternalSource.VAULT_REMINDER,
                            ExternalRecordType.TASK, title = reminder.title, dueAt = reminder.dueAt,
                            payload = mapOf("due" to java.time.Instant.ofEpochMilli(reminder.dueAt)
                                .atZone(ZoneId.systemDefault()).toString()), updatedAt = reminder.updatedAt)
                    })
                }
                "calculate" -> {
                    val expression = text("expression")
                    AgentToolResult(calculation = "$expression = ${BoundedArithmetic.evaluate(expression)}")
                }
                "find_duplicates" -> {
                    val original = vault.getByIds(listOf(text("item_id"))).firstOrNull()
                    AgentToolResult(items = if (original == null) emptyList() else
                        eligible(vault.search(original.title).filter { it.id != original.id }, filters))
                }
                "search_by_entity" -> AgentToolResult(items = eligible(vault.search(text("entity_name")), filters))
                "search_by_date" -> {
                    val from = LocalDate.parse(text("from_date")).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val to = LocalDate.parse(text("to_date")).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    require(from < to)
                    AgentToolResult(items = eligible(vault.getActive().filter { item ->
                        listOfNotNull(item.createdAt, item.expiryDate, item.secondaryAlertDate).any { it >= from && it < to }
                    }, filters))
                }
                "search_by_amount" -> {
                    val min = text("min").toBigDecimal(); val max = text("max").toBigDecimal()
                    require(min <= max)
                    AgentToolResult(items = eligible(vault.getActive().filter { item ->
                        listOf("total", "amount", "total_amount", "price").any { key ->
                            item.parsedMetadata[key]?.replace(",", "")?.toBigDecimalOrNull()?.let { it in min..max } == true
                        }
                    }, filters))
                }
                else -> AgentToolResult(error = "Tool unavailable")
            }
            if (DecoySessionState.isDecoy.value) AgentToolResult() else result
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { AgentToolResult(error = "Unable to complete this read; no result was inferred") }
    }

    private suspend fun eligible(items: List<VaultItem>, filters: SearchFilters): List<VaultItem> {
        val ids = vault.filterIds(items.map { it.id }, filters.lensTag, filters.dateFrom, filters.dateTo, filters.hasImage).toSet()
        return items.filter { it.id in ids && !it.isArchived && !it.isStealth &&
            it.enrichmentState != com.vaultbrain.core.common.model.EnrichmentState.SKIPPED_PRIVACY }.take(10)
    }
}

/** Small arithmetic grammar, not code evaluation. No names, functions, exponentiation or scripts. */
internal object BoundedArithmetic {
    fun evaluate(expression: String): String {
        require(expression.length in 1..160)
        val input = expression.filterNot(Char::isWhitespace)
        var position = 0
        var depth = 0
        fun peek() = input.getOrNull(position)
        lateinit var sum: () -> BigDecimal
        fun atom(): BigDecimal {
            require(++depth <= 16)
            val sign = if (peek() == '-') { position++; -1 } else { if (peek() == '+') position++; 1 }
            val value = if (peek() == '(') {
                position++
                val nested = sum()
                require(peek() == ')'); position++; nested
            } else {
                val start = position
                while (peek()?.let { it in '0'..'9' || it == '.' } == true) position++
                require(position > start && position - start <= 30)
                input.substring(start, position).toBigDecimal()
            }
            depth--
            return value.multiply(BigDecimal(sign), MathContext.DECIMAL64)
        }
        fun product(): BigDecimal {
            var value = atom()
            while (peek() == '*' || peek() == '/') {
                val operation = input[position++]
                val right = atom()
                value = if (operation == '*') value.multiply(right, MathContext.DECIMAL64)
                    else value.divide(right, MathContext.DECIMAL64)
            }
            return value
        }
        sum = {
            var value = product()
            while (peek() == '+' || peek() == '-') {
                val operation = input[position++]
                val right = product()
                value = if (operation == '+') value.add(right, MathContext.DECIMAL64)
                    else value.subtract(right, MathContext.DECIMAL64)
            }
            value
        }
        val value = sum()
        require(position == input.length && value.precision() <= 40 && kotlin.math.abs(value.scale()) <= 100)
        return value.stripTrailingZeros().toPlainString()
    }
}

package com.vaultbrain.feature.brain

import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.notifications.UnifiedAlertManager
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

sealed interface BrainWriteAction {
    data object Pin : BrainWriteAction
    data object Unpin : BrainWriteAction
    data object Archive : BrainWriteAction
    data object Restore : BrainWriteAction
    data class SetReminder(val triggerAt: Long) : BrainWriteAction
    data object ClearReminder : BrainWriteAction
    data class MarkMediaCompleted(val kind: MediaCompletionKind) : BrainWriteAction
}

enum class MediaCompletionKind(val storedStatus: String) {
    WATCHED("watched"),
    FINISHED("finished")
}

data class BrainWritePreview(
    val item: VaultItem,
    val expectedUpdatedAt: Long,
    val action: BrainWriteAction,
    val originalQuery: String
)

enum class BrainWriteIssue {
    INVALID_COMMAND,
    INVALID_DATE,
    DATE_TOO_SOON,
    DATE_TOO_FAR,
    NO_MATCH,
    AMBIGUOUS_MATCH,
    CATEGORY_MISMATCH,
    ALREADY_APPLIED,
    ITEM_CHANGED,
    EXECUTION_FAILED,
    NOTIFICATION_PERMISSION_DENIED
}

sealed interface BrainWritePlanResult {
    data object NotWriteAction : BrainWritePlanResult
    data class Ready(val preview: BrainWritePreview) : BrainWritePlanResult
    data class Rejected(val issue: BrainWriteIssue) : BrainWritePlanResult
}

sealed interface BrainWriteExecutionResult {
    data class Success(val updatedItem: VaultItem) : BrainWriteExecutionResult
    data class Failed(val issue: BrainWriteIssue) : BrainWriteExecutionResult
}

/**
 * Deterministic, fail-closed planner and executor for the small set of Brain writes.
 * Planning never writes; execution revalidates the exact item snapshot before mutation.
 */
class BrainWriteActionPlanner @Inject constructor(
    private val repository: VaultRepository,
    private val alertManager: UnifiedAlertManager
) {
    suspend fun plan(
        query: String,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): BrainWritePlanResult {
        val parsed = parse(query, now, zoneId) ?: return BrainWritePlanResult.NotWriteAction
        if (parsed is ParsedCommand.Invalid) return BrainWritePlanResult.Rejected(parsed.issue)
        parsed as ParsedCommand.Valid

        val items = try {
            when (parsed.action) {
                BrainWriteAction.Restore -> repository.getArchived()
                else -> repository.getActive()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return BrainWritePlanResult.Rejected(BrainWriteIssue.EXECUTION_FAILED)
        }
        val target = parsed.title.normalizedTitle()
        val exact = items.filter { it.title.normalizedTitle() == target }
        val matches = if (exact.isNotEmpty()) exact else {
            items.filter { item ->
                target.length >= MIN_PARTIAL_TITLE_LENGTH && target in item.title.normalizedTitle()
            }
        }
        val item = when (matches.size) {
            0 -> return BrainWritePlanResult.Rejected(BrainWriteIssue.NO_MATCH)
            1 -> matches.single()
            else -> return BrainWritePlanResult.Rejected(BrainWriteIssue.AMBIGUOUS_MATCH)
        }
        val action = parsed.action
        if (action == BrainWriteAction.Pin && item.isPinned) {
            return BrainWritePlanResult.Rejected(BrainWriteIssue.ALREADY_APPLIED)
        }
        if (action == BrainWriteAction.Unpin && !item.isPinned) {
            return BrainWritePlanResult.Rejected(BrainWriteIssue.ALREADY_APPLIED)
        }
        if (action == BrainWriteAction.Archive && item.isArchived) {
            return BrainWritePlanResult.Rejected(BrainWriteIssue.ALREADY_APPLIED)
        }
        if (action == BrainWriteAction.Restore && !item.isArchived) {
            return BrainWritePlanResult.Rejected(BrainWriteIssue.ALREADY_APPLIED)
        }
        if (action is BrainWriteAction.SetReminder && item.secondaryAlertDate == action.triggerAt) {
            return BrainWritePlanResult.Rejected(BrainWriteIssue.ALREADY_APPLIED)
        }
        if (action == BrainWriteAction.ClearReminder && item.secondaryAlertDate == null) {
            return BrainWritePlanResult.Rejected(BrainWriteIssue.ALREADY_APPLIED)
        }
        if (action is BrainWriteAction.MarkMediaCompleted) {
            if (!item.supports(action.kind)) {
                return BrainWritePlanResult.Rejected(BrainWriteIssue.CATEGORY_MISMATCH)
            }
            if (item.isMediaCompleted()) {
                return BrainWritePlanResult.Rejected(BrainWriteIssue.ALREADY_APPLIED)
            }
        }
        return BrainWritePlanResult.Ready(
            BrainWritePreview(
                item = item,
                expectedUpdatedAt = item.updatedAt,
                action = action,
                originalQuery = query.trim()
            )
        )
    }

    suspend fun execute(
        preview: BrainWritePreview,
        now: Long = System.currentTimeMillis()
    ): BrainWriteExecutionResult {
        val current = try {
            repository.getById(preview.item.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return BrainWriteExecutionResult.Failed(BrainWriteIssue.EXECUTION_FAILED)
        } ?: return BrainWriteExecutionResult.Failed(BrainWriteIssue.ITEM_CHANGED)
        val archiveStateChanged = when (preview.action) {
            BrainWriteAction.Restore -> !current.isArchived
            else -> current.isArchived
        }
        if (archiveStateChanged || current.isStealth || current.updatedAt != preview.expectedUpdatedAt) {
            return BrainWriteExecutionResult.Failed(BrainWriteIssue.ITEM_CHANGED)
        }
        val updatedAt = maxOf(now, current.updatedAt + 1)
        val updated = when (val action = preview.action) {
            BrainWriteAction.Pin -> {
                if (current.isPinned) {
                    return BrainWriteExecutionResult.Failed(BrainWriteIssue.ALREADY_APPLIED)
                }
                current.copy(isPinned = true, updatedAt = updatedAt, userEditedAt = updatedAt)
            }
            BrainWriteAction.Unpin -> {
                if (!current.isPinned) {
                    return BrainWriteExecutionResult.Failed(BrainWriteIssue.ALREADY_APPLIED)
                }
                current.copy(isPinned = false, updatedAt = updatedAt, userEditedAt = updatedAt)
            }
            BrainWriteAction.Archive -> current.copy(
                isArchived = true,
                updatedAt = updatedAt,
                userEditedAt = updatedAt
            )
            BrainWriteAction.Restore -> current.copy(
                isArchived = false,
                updatedAt = updatedAt,
                userEditedAt = updatedAt
            )
            is BrainWriteAction.SetReminder -> {
                when {
                    action.triggerAt < now + MIN_REMINDER_LEAD_MILLIS ->
                        return BrainWriteExecutionResult.Failed(BrainWriteIssue.DATE_TOO_SOON)
                    action.triggerAt > now + MAX_REMINDER_HORIZON_MILLIS ->
                        return BrainWriteExecutionResult.Failed(BrainWriteIssue.DATE_TOO_FAR)
                }
                current.copy(
                    secondaryAlertDate = action.triggerAt,
                    updatedAt = updatedAt,
                    userEditedAt = updatedAt
                )
            }
            BrainWriteAction.ClearReminder -> {
                if (current.secondaryAlertDate == null) {
                    return BrainWriteExecutionResult.Failed(BrainWriteIssue.ALREADY_APPLIED)
                }
                current.copy(
                    secondaryAlertDate = null,
                    updatedAt = updatedAt,
                    userEditedAt = updatedAt
                )
            }
            is BrainWriteAction.MarkMediaCompleted -> {
                if (!current.supports(action.kind)) {
                    return BrainWriteExecutionResult.Failed(BrainWriteIssue.CATEGORY_MISMATCH)
                }
                if (current.isMediaCompleted()) {
                    return BrainWriteExecutionResult.Failed(BrainWriteIssue.ALREADY_APPLIED)
                }
                current.copy(
                    parsedMetadata = current.parsedMetadata + (MEDIA_STATUS_KEY to action.kind.storedStatus),
                    customFields = current.customFields + (MEDIA_STATUS_KEY to action.kind.storedStatus),
                    secondaryAlertDate = null,
                    updatedAt = updatedAt,
                    userEditedAt = updatedAt
                )
            }
        }

        var mutationSaved = false
        return try {
            repository.save(updated)
            mutationSaved = true
            when (preview.action) {
                BrainWriteAction.Pin,
                BrainWriteAction.Unpin,
                BrainWriteAction.Archive,
                BrainWriteAction.Restore -> Unit
                is BrainWriteAction.SetReminder -> alertManager.scheduleAlerts(updated)
                BrainWriteAction.ClearReminder -> alertManager.cancelForItem(updated.id)
                is BrainWriteAction.MarkMediaCompleted -> alertManager.cancelForItem(updated.id)
            }
            BrainWriteExecutionResult.Success(updated)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (mutationSaved && preview.action.requiresAlertRecovery()) {
                try {
                    repository.save(current)
                    alertManager.scheduleAlerts(current)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Recovery is best effort; the UI tells the user to inspect the item.
                }
            }
            BrainWriteExecutionResult.Failed(BrainWriteIssue.EXECUTION_FAILED)
        }
    }

    private fun parse(query: String, now: Long, zoneId: ZoneId): ParsedCommand? {
        val trimmed = query.trim()
        UNPIN_PATTERNS.firstNotNullOfOrNull { it.matchEntire(trimmed) }?.let { match ->
            val title = match.groupValues[1].cleanTitle()
            return if (title.isBlank()) ParsedCommand.Invalid(BrainWriteIssue.INVALID_COMMAND)
            else ParsedCommand.Valid(title, BrainWriteAction.Unpin)
        }

        PIN_PATTERNS.firstNotNullOfOrNull { it.matchEntire(trimmed) }?.let { match ->
            val title = match.groupValues[1].cleanTitle()
            return if (title.isBlank()) ParsedCommand.Invalid(BrainWriteIssue.INVALID_COMMAND)
            else ParsedCommand.Valid(title, BrainWriteAction.Pin)
        }

        ARCHIVE_PATTERNS.firstNotNullOfOrNull { it.matchEntire(trimmed) }?.let { match ->
            val title = match.groupValues[1].cleanTitle()
            return if (title.isBlank()) ParsedCommand.Invalid(BrainWriteIssue.INVALID_COMMAND)
            else ParsedCommand.Valid(title, BrainWriteAction.Archive)
        }

        RESTORE_PATTERNS.firstNotNullOfOrNull { it.matchEntire(trimmed) }?.let { match ->
            val title = match.groupValues[1].cleanTitle()
            return if (title.isBlank()) ParsedCommand.Invalid(BrainWriteIssue.INVALID_COMMAND)
            else ParsedCommand.Valid(title, BrainWriteAction.Restore)
        }

        MEDIA_COMPLETION_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.regex.matchEntire(trimmed)?.let { match -> pattern to match }
        }?.let { (pattern, match) ->
            val title = match.groupValues[1].cleanTitle()
            return if (title.isBlank()) ParsedCommand.Invalid(BrainWriteIssue.INVALID_COMMAND)
            else ParsedCommand.Valid(title, BrainWriteAction.MarkMediaCompleted(pattern.kind))
        }

        CLEAR_REMINDER_PATTERNS.firstNotNullOfOrNull { it.matchEntire(trimmed) }?.let { match ->
            val title = match.groupValues[1].cleanTitle()
            return if (title.isBlank()) ParsedCommand.Invalid(BrainWriteIssue.INVALID_COMMAND)
            else ParsedCommand.Valid(title, BrainWriteAction.ClearReminder)
        }

        val reminderMatch = REMINDER_PATTERNS.firstNotNullOfOrNull { it.matchEntire(trimmed) }
            ?: return if (looksLikeWrite(trimmed)) {
                ParsedCommand.Invalid(BrainWriteIssue.INVALID_COMMAND)
            } else {
                null
            }
        val dateSpec = reminderMatch.groupValues[1].trim()
        val title = reminderMatch.groupValues[2].cleanTitle()
        if (title.isBlank()) return ParsedCommand.Invalid(BrainWriteIssue.INVALID_COMMAND)
        val triggerAt = parseTrigger(dateSpec, now, zoneId)
            ?: return ParsedCommand.Invalid(BrainWriteIssue.INVALID_DATE)
        return when {
            triggerAt < now + MIN_REMINDER_LEAD_MILLIS ->
                ParsedCommand.Invalid(BrainWriteIssue.DATE_TOO_SOON)
            triggerAt > now + MAX_REMINDER_HORIZON_MILLIS ->
                ParsedCommand.Invalid(BrainWriteIssue.DATE_TOO_FAR)
            else -> ParsedCommand.Valid(title, BrainWriteAction.SetReminder(triggerAt))
        }
    }

    private fun parseTrigger(dateSpec: String, now: Long, zoneId: ZoneId): Long? = try {
        val normalized = dateSpec.lowercase(Locale.ROOT).trim().removePrefix("on ")
        val timeMatch = TIME_PATTERN.find(normalized)
        val time = if (timeMatch == null) {
            LocalTime.of(DEFAULT_REMINDER_HOUR, 0)
        } else {
            parseTime(timeMatch) ?: return null
        }
        val dateText = timeMatch?.let { normalized.removeRange(it.range).trim() } ?: normalized
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val date = when (dateText) {
            "today", "اليوم" -> today
            "tomorrow", "غدا", "غدًا" -> today.plusDays(1)
            else -> LocalDate.parse(dateText, DateTimeFormatter.ISO_LOCAL_DATE)
        }
        date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
    } catch (_: DateTimeException) {
        null
    }

    private fun parseTime(match: MatchResult): LocalTime? {
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
        return when (match.groupValues[3].lowercase(Locale.ROOT)) {
            "am" -> {
                if (hour !in 1..12) return null
                if (hour == 12) hour = 0
                LocalTime.of(hour, minute)
            }
            "pm" -> {
                if (hour !in 1..12) return null
                if (hour != 12) hour += 12
                LocalTime.of(hour, minute)
            }
            else -> LocalTime.of(hour, minute)
        }
    }

    private fun looksLikeWrite(query: String): Boolean {
        val normalized = query.lowercase(Locale.ROOT)
        return normalized.startsWith("pin ") || normalized.startsWith("unpin ") ||
            normalized.startsWith("archive ") || normalized.startsWith("please archive ") ||
            normalized.startsWith("أرشف ") || normalized.startsWith("ارشف ") ||
            normalized.startsWith("من فضلك أرشف ") || normalized.startsWith("من فضلك ارشف ") ||
            normalized.startsWith("restore ") || normalized.startsWith("unarchive ") ||
            normalized.startsWith("please restore ") || normalized.startsWith("please unarchive ") ||
            normalized.startsWith("استرجع ") || normalized.startsWith("ألغ أرشفة ") ||
            normalized.startsWith("الغ أرشفة ") ||
            normalized.startsWith("remind me") || normalized.startsWith("clear reminder") ||
            normalized.startsWith("remove reminder") || normalized.startsWith("cancel reminder") ||
            normalized.startsWith("mark ") || normalized.startsWith("please mark ") ||
            normalized.startsWith("i watched ") ||
            normalized.startsWith("i read ") || normalized.startsWith("i finished ") ||
            normalized.startsWith("ثبت ") || normalized.startsWith("ثبّت ") ||
            normalized.startsWith("ألغ تثبيت") || normalized.startsWith("ألغِ تثبيت") ||
            normalized.startsWith("الغ تثبيت") || normalized.startsWith("إلغاء تثبيت") ||
            normalized.startsWith("ذكرني") || normalized.startsWith("ذكّرني") ||
            normalized.startsWith("ألغ تذكير") || normalized.startsWith("ألغِ تذكير") ||
            normalized.startsWith("الغ تذكير") || normalized.startsWith("احذف تذكير") ||
            normalized.startsWith("شاهدت ") || normalized.startsWith("قرأت ") ||
            normalized.startsWith("أنهيت قراءة ")
    }

    private fun String.cleanTitle(): String = trim().removeSurrounding("\"").removeSurrounding("“", "”").trim()

    private fun String.normalizedTitle(): String = lowercase(Locale.ROOT)
        .replace(Regex("[\\p{Punct}\\s]+"), " ")
        .trim()

    private fun VaultItem.supports(kind: MediaCompletionKind): Boolean = when (kind) {
        MediaCompletionKind.WATCHED -> effectiveClassification in setOf(
            Classification.MOVIE,
            Classification.TV_SERIES
        )
        MediaCompletionKind.FINISHED -> effectiveClassification == Classification.BOOK
    }

    private fun VaultItem.isMediaCompleted(): Boolean =
        (customFields[MEDIA_STATUS_KEY] ?: parsedMetadata[MEDIA_STATUS_KEY])
            ?.lowercase(Locale.ROOT) in COMPLETED_MEDIA_STATUSES

    private fun BrainWriteAction.requiresAlertRecovery(): Boolean =
        this is BrainWriteAction.SetReminder ||
            this == BrainWriteAction.ClearReminder ||
            this is BrainWriteAction.MarkMediaCompleted

    private sealed interface ParsedCommand {
        data class Valid(val title: String, val action: BrainWriteAction) : ParsedCommand
        data class Invalid(val issue: BrainWriteIssue) : ParsedCommand
    }

    private companion object {
        const val DEFAULT_REMINDER_HOUR = 9
        const val MIN_PARTIAL_TITLE_LENGTH = 3
        const val MIN_REMINDER_LEAD_MILLIS = 5L * 60L * 1000L
        const val MAX_REMINDER_HORIZON_MILLIS = 365L * 24L * 60L * 60L * 1000L
        const val MEDIA_STATUS_KEY = "media_status"

        data class MediaCompletionPattern(
            val regex: Regex,
            val kind: MediaCompletionKind
        )

        val PIN_PATTERNS = listOf(
            Regex("^(?:please\\s+)?pin\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:من فضلك\\s+)?(?:ثبّت|ثبت)\\s+(.+)$")
        )
        val UNPIN_PATTERNS = listOf(
            Regex("^(?:please\\s+)?unpin\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:من فضلك\\s+)?(?:ألغِ|ألغ|الغِ|الغ|إلغاء|الغاء)\\s+تثبيت\\s+(.+)$")
        )
        val ARCHIVE_PATTERNS = listOf(
            Regex("^(?:please\\s+)?archive\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:من فضلك\\s+)?(?:أرشف|ارشف)\\s+(.+)$")
        )
        val RESTORE_PATTERNS = listOf(
            Regex("^(?:please\\s+)?(?:restore|unarchive)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:من فضلك\\s+)?استرجع\\s+(.+?)\\s+من\\s+الأرشيف$"),
            Regex("^(?:من فضلك\\s+)?استرجع\\s+(.+)$"),
            Regex("^(?:من فضلك\\s+)?(?:ألغ|الغ)\\s+أرشفة\\s+(.+)$")
        )
        val MEDIA_COMPLETION_PATTERNS = listOf(
            MediaCompletionPattern(
                Regex("^(?:please\\s+)?mark\\s+(.+?)\\s+as\\s+watched$", RegexOption.IGNORE_CASE),
                MediaCompletionKind.WATCHED
            ),
            MediaCompletionPattern(
                Regex("^(?:i\\s+)?watched\\s+(.+)$", RegexOption.IGNORE_CASE),
                MediaCompletionKind.WATCHED
            ),
            MediaCompletionPattern(
                Regex("^(?:please\\s+)?mark\\s+(.+?)\\s+as\\s+(?:read|finished)$", RegexOption.IGNORE_CASE),
                MediaCompletionKind.FINISHED
            ),
            MediaCompletionPattern(
                Regex("^(?:i\\s+)?(?:read|finished)\\s+(.+)$", RegexOption.IGNORE_CASE),
                MediaCompletionKind.FINISHED
            ),
            MediaCompletionPattern(
                Regex("^شاهدت\\s+(.+)$"),
                MediaCompletionKind.WATCHED
            ),
            MediaCompletionPattern(
                Regex("^(?:قرأت|أنهيت\\s+قراءة)\\s+(.+)$"),
                MediaCompletionKind.FINISHED
            )
        )
        val REMINDER_PATTERNS = listOf(
            Regex(
                "^remind\\s+me\\s+(.+?)\\s+(?:about|to\\s+(?:watch|read|check|review))\\s+(.+)$",
                RegexOption.IGNORE_CASE
            ),
            Regex("^(?:ذكّرني|ذكرني)\\s+(.+?)\\s+(?:بشأن|عن|لمشاهدة|لقراءة|بمراجعة)\\s+(.+)$")
        )
        val CLEAR_REMINDER_PATTERNS = listOf(
            Regex(
                "^(?:please\\s+)?(?:clear|remove|cancel)\\s+(?:the\\s+)?reminder\\s+(?:for|from|about)\\s+(.+)$",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "^(?:من فضلك\\s+)?(?:ألغِ|ألغ|الغِ|الغ|احذف)\\s+(?:ال)?تذكير\\s+(?:(?:عن|بشأن|لـ|ل)\\s+)?(.+)$"
            )
        )
        val TIME_PATTERN = Regex("(?:at|الساعة)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
        val COMPLETED_MEDIA_STATUSES = setOf("watched", "finished", "completed")
    }
}

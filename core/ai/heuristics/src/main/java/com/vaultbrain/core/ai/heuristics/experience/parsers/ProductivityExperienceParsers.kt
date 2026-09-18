package com.vaultbrain.core.ai.heuristics.experience.parsers

import com.vaultbrain.core.ai.heuristics.experience.ExperienceParser
import com.vaultbrain.core.common.model.ExperienceId
import com.vaultbrain.core.common.model.VaultItem

/** Extracts note / reminder fields. */
class NoteParser : ExperienceParser {
    override val experienceId = ExperienceId.NOTE

    override fun canApply(item: VaultItem): Boolean {
        // Generic fallback: almost any shared text can be a note.
        return item.rawOcrText?.isNotBlank() == true
    }

    override fun extract(item: VaultItem): Map<String, String> {
        return buildMap {
            put("experience_type", "note")
            item.rawOcrText?.take(200)?.let { put("preview", it) }
        }
    }
}

/** Extracts reminder fields. */
class ReminderParser : ExperienceParser {
    override val experienceId = ExperienceId.REMINDER

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("remind", ignoreCase = true) ||
            text.contains("remember", ignoreCase = true) ||
            text.contains("don't forget", ignoreCase = true) ||
            text.contains("due", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "reminder")
            extractDate(text)?.let { put("due_date", it) }
            extractTime(text)?.let { put("due_time", it) }
        }
    }
}

/** Extracts meeting notes fields. */
class MeetingNotesParser : ExperienceParser {
    override val experienceId = ExperienceId.MEETING_NOTES

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("meeting", ignoreCase = true) ||
            text.contains("agenda", ignoreCase = true) ||
            text.contains("attendees", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "meeting_notes")
            extractDate(text)?.let { put("meeting_date", it) }
            extractTime(text)?.let { put("meeting_time", it) }
            extractAttendees(text).takeIf { it.isNotEmpty() }?.let { put("attendees", it.joinToString(", ")) }
            extractActionItems(text).takeIf { it.isNotEmpty() }?.let { put("action_items", it.joinToString("\n")) }
        }
    }
}

// Productivity helpers

private val attendeesRegex = """(?i)(?:attendees|attending|participants)[\s:]*([A-Za-z\s,]+)""".toRegex()
private val actionItemRegex = """(?i)(?:action item|todo|task|follow.up)[\s:]*(.+)""".toRegex()

internal fun extractAttendees(text: String): List<String> {
    return attendeesRegex.find(text)?.groups?.get(1)?.value
        ?.split(",", ";")
        ?.map { it.trim() }
        ?.filter { it.length > 2 }
        .orEmpty()
}

internal fun extractActionItems(text: String): List<String> {
    return actionItemRegex.findAll(text).map { it.groups[1]?.value?.trim() ?: "" }.filter { it.isNotBlank() }.take(10).toList()
}


// Extended productivity parsers (tasks, habits, goals, journaling).

/** Matches to-do lists; extracts due date if present. */
class TodoListParser : KeywordDocumentParser(
    ExperienceId.TODO_LIST,
    typeName = "todo_list",
    keywords = listOf("to do", "todo", "مهام", "قائمة مهام", "tasks"),
    dateKey = "due_date"
)

/** Matches habit trackers; extracts start date if present. */
class HabitTrackerParser : KeywordDocumentParser(
    ExperienceId.HABIT_TRACKER,
    typeName = "habit_tracker",
    keywords = listOf("habit", "عادة", "عادات", "streak", "habit tracker"),
    dateKey = "start_date"
)

/** Extracts goal fields (target date). */
class GoalParser : KeywordDocumentParser(
    ExperienceId.GOAL,
    typeName = "goal",
    keywords = listOf("goal", "objective", "هدف", "أهداف", "target date"),
    dateKey = "target_date"
)

/** Extracts project plan fields (deadline, owner). */
class ProjectPlanParser : KeywordDocumentParser(
    ExperienceId.PROJECT_PLAN,
    typeName = "project_plan",
    keywords = listOf("project plan", "خطة المشروع", "milestone", "deliverable", "roadmap"),
    dateKey = "due_date",
    providerKey = "owner"
)

/** Matches journal entries; extracts entry date. */
class JournalParser : KeywordDocumentParser(
    ExperienceId.JOURNAL,
    typeName = "journal",
    keywords = listOf("journal", "diary", "يوميات", "مذكرات", "dear diary"),
    dateKey = "entry_date"
)

/** Matches gratitude logs; extracts entry date. */
class GratitudeLogParser : KeywordDocumentParser(
    ExperienceId.GRATITUDE_LOG,
    typeName = "gratitude_log",
    keywords = listOf("gratitude", "grateful", "thankful", "امتنان", "ممتن"),
    dateKey = "entry_date"
)

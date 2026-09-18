package com.vaultbrain.core.ai.heuristics.experience.parsers

import com.vaultbrain.core.ai.heuristics.experience.ExperienceParser
import com.vaultbrain.core.common.model.ExperienceId
import com.vaultbrain.core.common.model.VaultItem

/** Extracts watchlist fields from movie, TV, or book references. */
class WatchlistParser : ExperienceParser {
    override val experienceId = ExperienceId.WATCHLIST

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("watch", ignoreCase = true) ||
            text.contains("movie", ignoreCase = true) ||
            text.contains("series", ignoreCase = true) ||
            text.contains("netflix", ignoreCase = true) ||
            text.contains("imdb", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "watchlist")
            put("status", "to_watch")
            extractTitleFromWatchlist(text)?.let { put("title", it) }
            extractYear(text)?.let { put("year", it) }
            extractPlatform(text)?.let { put("platform", it) }
        }
    }
}

/** Extracts reading list fields. */
class ReadingListParser : ExperienceParser {
    override val experienceId = ExperienceId.READING_LIST

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("book", ignoreCase = true) ||
            text.contains("read", ignoreCase = true) ||
            text.contains("author", ignoreCase = true) ||
            text.contains("novel", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "reading_list")
            put("status", "to_read")
            extractAuthor(text)?.let { put("author", it) }
            extractYear(text)?.let { put("year", it) }
        }
    }
}

/** Extracts recipe fields. */
class RecipeParser : ExperienceParser {
    override val experienceId = ExperienceId.RECIPE

    override fun canApply(item: VaultItem): Boolean {
        val text = item.rawOcrText ?: return false
        return text.contains("recipe", ignoreCase = true) ||
            text.contains("ingredients", ignoreCase = true) ||
            text.contains("instructions", ignoreCase = true)
    }

    override fun extract(item: VaultItem): Map<String, String> {
        val text = item.rawOcrText ?: return emptyMap()
        return buildMap {
            put("experience_type", "recipe")
            extractRecipeName(text)?.let { put("recipe_name", it) }
            extractPrepTime(text)?.let { put("prep_time", it) }
            extractServings(text)?.let { put("servings", it) }
        }
    }
}

// Media helpers

private val yearRegex = """\b(19\d{2}|20\d{2})\b""".toRegex()
private val platformRegex = """(?i)(netflix|prime video|disney\+|hulu|hbo max|youtube|apple tv|spotify|audible|kindle)""".toRegex()
private val authorRegex = """(?i)(?:author|by)[\s:]*([A-Z][a-zA-Z]+(?:\s[A-Z][a-zA-Z]+)?)""".toRegex()
private val recipeNameRegex = """(?i)(?:recipe|dish)[\s:]*([A-Z][a-zA-Z]+(?:\s[A-Z][a-zA-Z]+){0,4})""".toRegex()
private val prepTimeRegex = """(?i)(prep|cook|total)\s*time[\s:]*(\d+\s*(?:min|hr|hour)s?)""".toRegex()
private val servingsRegex = """(?i)serves?[\s:]*(\d+)""".toRegex()
private val titleBlacklist = setOf("watch", "movie", "series", "netflix", "imdb", "book", "read", "author", "recipe")

internal fun extractYear(text: String): String? {
    return yearRegex.find(text)?.value
}

internal fun extractPlatform(text: String): String? {
    return platformRegex.find(text)?.value?.lowercase()?.replaceFirstChar { it.titlecase() }
}

internal fun extractAuthor(text: String): String? {
    return authorRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractRecipeName(text: String): String? {
    return recipeNameRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractPrepTime(text: String): String? {
    return prepTimeRegex.find(text)?.groups?.get(2)?.value
}

internal fun extractServings(text: String): String? {
    return servingsRegex.find(text)?.groups?.get(1)?.value
}

internal fun extractTitleFromWatchlist(text: String): String? {
    // Heuristic: the longest line that does not contain platform/keywords.
    return text.lines()
        .map { it.trim() }
        .filter { it.length in 4..60 && titleBlacklist.none { k -> it.contains(k, ignoreCase = true) } }
        .maxByOrNull { it.length }
}


// Extended media parsers (audio, books, learning, event tickets).

/** Matches podcast references; extracts show name and episode date. */
class PodcastParser : KeywordDocumentParser(
    ExperienceId.PODCAST,
    typeName = "podcast",
    keywords = listOf("podcast", "بودكاست", "episode", "show notes"),
    dateKey = "episode_date",
    providerKey = "show"
)

/** Extracts audiobook fields (publisher, date). */
class AudiobookParser : KeywordDocumentParser(
    ExperienceId.AUDIOBOOK,
    typeName = "audiobook",
    keywords = listOf("audiobook", "كتاب صوتي", "narrated by", "audible", "narrator"),
    dateKey = "date",
    providerKey = "publisher"
)

/** Extracts ebook fields (publisher, purchase date). */
class EbookParser : KeywordDocumentParser(
    ExperienceId.EBOOK,
    typeName = "ebook",
    keywords = listOf("ebook", "e-book", "كتاب إلكتروني", "kindle", "epub"),
    dateKey = "purchase_date",
    providerKey = "publisher"
)

/** Extracts online course fields (platform, enrollment date). */
class OnlineCourseParser : KeywordDocumentParser(
    ExperienceId.ONLINE_COURSE,
    typeName = "online_course",
    keywords = listOf("online course", "دورة تدريبية", "udemy", "coursera", "certificate of completion"),
    dateKey = "enrollment_date",
    providerKey = "platform"
)

/** Extracts concert ticket fields (price, event date, artist). */
class ConcertTicketParser : KeywordDocumentParser(
    ExperienceId.CONCERT_TICKET,
    typeName = "concert_ticket",
    keywords = listOf("concert", "حفلة", "live music", "gig", "festival"),
    amountKey = "price",
    dateKey = "event_date",
    providerKey = "artist",
    withReference = true
)

/** Extracts museum ticket fields (price, visit date, museum). */
class MuseumTicketParser : KeywordDocumentParser(
    ExperienceId.MUSEUM_TICKET,
    typeName = "museum_ticket",
    keywords = listOf("museum", "متحف", "exhibition", "معرض", "admission"),
    amountKey = "price",
    dateKey = "visit_date",
    providerKey = "museum",
    withReference = true
)

/** Extracts theater ticket fields (price, show date, venue). */
class TheaterTicketParser : KeywordDocumentParser(
    ExperienceId.THEATER_TICKET,
    typeName = "theater_ticket",
    keywords = listOf("theater", "theatre", "مسرح", "مسرحية", "opera", "ballet"),
    amountKey = "price",
    dateKey = "show_date",
    providerKey = "venue",
    withReference = true
)

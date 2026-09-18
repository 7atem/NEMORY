package com.vaultbrain.feature.capture.share

import org.jsoup.Jsoup

/**
 * Deterministic, Android-context-free validation helpers for incoming shares.
 *
 * These functions are kept separate from [IncomingShareParser] so they can be
 * unit-tested on the JVM without a device or Robolectric.
 */
object ShareContentValidator {

    private val SUPPORTED_LITERAL_MIMES = setOf("text/plain", "text/html", "application/pdf")
    private const val IMAGE_WILDCARD = "image/"
    private val URL_REGEX = Regex("""https?://[a-zA-Z0-9\-.]+\.[a-zA-Z]{2,}[^\s]*""")

    /** Maximum individual shared file size: 100 MB. */
    const val MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024

    fun isSupportedMime(mime: String?): Boolean {
        if (mime.isNullOrBlank()) return false
        val normalized = mime.lowercase().trim()
        if (normalized in SUPPORTED_LITERAL_MIMES) return true
        return normalized.startsWith(IMAGE_WILDCARD)
    }

    /**
     * Extracts the first HTTP/HTTPS URL from [text], or null if none is found.
     */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return URL_REGEX.find(text)?.value
    }

    /**
     * Strips HTML tags and collapses whitespace into plain text.
     */
    fun extractTextFromHtml(html: String?): String? {
        if (html.isNullOrBlank()) return null
        return Jsoup.parse(html).text().trim().takeIf { it.isNotBlank() }
    }

    /**
     * Removes path traversal and invisible characters from a display filename.
     */
    fun sanitizeFilename(name: String?): String {
        if (name.isNullOrBlank()) return "shared_file"
        return name
            .replace("..", "_")
            .replace(Regex("[\\\\/]"), "_")
            .replace(Regex("[\\x00-\\x1f\\x7f]"), "")
            .trim()
            .take(120)
            .ifEmpty { "shared_file" }
    }

    /**
     * True when [scheme] is acceptable for an incoming shared URI.
     * Modern Android shares should use the `content` scheme; `file` URIs from
     * third-party apps are rejected because the receiving app rarely holds
     * read permission for them.
     */
    fun isAllowedUriScheme(scheme: String?): Boolean {
        return scheme.equals("content", ignoreCase = true)
    }
}

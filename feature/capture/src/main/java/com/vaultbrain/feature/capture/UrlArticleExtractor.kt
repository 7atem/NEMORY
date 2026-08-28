package com.vaultbrain.feature.capture

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

data class ArticleContent(
    val title: String,
    val summary: String,
    val body: String
)

@Singleton
class UrlArticleExtractor @Inject constructor() {
    
    suspend fun extract(url: String): ArticleContent? = withContext(Dispatchers.IO) {
        try {
            val document = Jsoup.connect(url)
                .timeout(10000)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .get()

            val title = document.title().ifBlank { url }
            
            // Try to find meta description for summary
            var summary = document.select("meta[name=description]").attr("content")
            if (summary.isBlank()) {
                summary = document.select("meta[property=og:description]").attr("content")
            }

            // Extract paragraphs
            val paragraphs = document.select("p")
            val body = paragraphs.joinToString("\n\n") { it.text() }.trim()

            if (summary.isBlank()) {
                summary = body.take(300)
            }

            ArticleContent(
                title = title.take(120),
                summary = summary.take(300),
                body = body
            )
        } catch (e: Exception) {
            null
        }
    }
}

package com.vaultbrain.feature.vault

import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.VaultItem
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class CaptureSuggestion(
    val title: String,
    val subtitle: String
)

data class CollectionSuggestion(
    val lensId: String,
    val lensLabel: String,
    val itemCount: Int,
    val sampleTitles: List<String>
)

@Singleton
class CapturePatternDetector @Inject constructor() {
    fun detectPattern(items: List<VaultItem>, now: Long = System.currentTimeMillis()): CaptureSuggestion? {
        if (items.isEmpty()) return null

        val currentZdt = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        val currentDayOfWeek = currentZdt.dayOfWeek
        val currentHour = currentZdt.hour

        // Rule 1: Lunch receipts
        if (currentHour in 12..14) {
            val lunchReceipts = items.count { 
                it.effectiveClassification == Classification.RECEIPT && 
                Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).hour in 12..14
            }
            if (lunchReceipts >= 3) {
                return CaptureSuggestion(
                    title = "Lunch time!",
                    subtitle = "Snap your lunch receipt while it's fresh."
                )
            }
        }

        // Rule 2: Day of week patterns
        val recentItemsDayMatch = items.take(30).count {
            val itemDate = Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault())
            itemDate.dayOfWeek == currentDayOfWeek
        }
        
        if (recentItemsDayMatch >= 5) {
            val relatedItem = items.firstOrNull {
                Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).dayOfWeek == currentDayOfWeek 
            }
            val category = relatedItem?.subtype ?: relatedItem?.topics?.firstOrNull() ?: "item"
            
            val dayName = currentDayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            return CaptureSuggestion(
                title = "Typical $dayName?",
                subtitle = "You usually save a $category on ${dayName}s. Snap one now?"
            )
        }

        return null
    }

    /**
     * Suggests grouping items into a collection when 3+ items share a lens in the past 7 days.
     * Returns the top lens suggestion, or null if no strong pattern is found.
     */
    fun detectCollectionPattern(
        items: List<VaultItem>,
        now: Long = System.currentTimeMillis()
    ): CollectionSuggestion? {
        val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
        val recent = items.filter { it.createdAt >= sevenDaysAgo && it.primaryLensId != null }
        if (recent.isEmpty()) return null

        // Group by primaryLensId and find the most common lens with >= 3 items
        val candidate = recent
            .groupBy { it.primaryLensId!! }
            .maxByOrNull { it.value.size }
            ?.takeIf { it.value.size >= 3 }
            ?: return null

        val lensLabel = when (candidate.key) {
            com.vaultbrain.shared.domain.LensId.MONEY -> "Finance"
            com.vaultbrain.shared.domain.LensId.HEALTH -> "Health"
            com.vaultbrain.shared.domain.LensId.TRAVEL -> "Travel"
            com.vaultbrain.shared.domain.LensId.BUREAUCRACY -> "Documents"
            com.vaultbrain.shared.domain.LensId.MEDIA -> "Media"
            else -> candidate.key
        }

        return CollectionSuggestion(
            lensId = candidate.key,
            lensLabel = lensLabel,
            itemCount = candidate.value.size,
            sampleTitles = candidate.value.take(3).map { it.title }
        )
    }
}

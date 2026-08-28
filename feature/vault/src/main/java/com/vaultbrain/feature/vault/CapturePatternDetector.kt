package com.vaultbrain.feature.vault

import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.VaultItem
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class CaptureSuggestion(
    val title: String,
    val subtitle: String
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
}

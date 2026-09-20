package com.vaultbrain.shared.search

import platform.CoreSpotlight.*
import kotlinx.cinterop.ExperimentalForeignApi

class IosSystemSearchManager : SystemSearchManager {

    @OptIn(ExperimentalForeignApi::class)
    override fun indexItem(item: SearchableVaultItem) {
        // Create attributes for Spotlight
        val attributeSet = CSSearchableItemAttributeSet(itemContentType = "public.content")
        attributeSet.title = item.title
        attributeSet.contentDescription = item.contentDescription
        
        // Convert Kotlin List to iOS Array
        attributeSet.keywords = item.keywords

        // Create the searchable item
        val searchableItem = CSSearchableItem(
            uniqueIdentifier = item.id,
            domainIdentifier = "com.vaultbrain.vault",
            attributeSet = attributeSet
        )

        // Index the item
        CSSearchableIndex.defaultSearchableIndex().indexSearchableItems(listOf(searchableItem)) { error ->
            if (error != null) {
                println("Spotlight index error: ${error.localizedDescription}")
            }
        }
    }

    override fun removeItem(id: String) {
        CSSearchableIndex.defaultSearchableIndex().deleteSearchableItemsWithIdentifiers(listOf(id)) { error ->
            if (error != null) {
                println("Spotlight delete error: ${error.localizedDescription}")
            }
        }
    }

    override fun clearAll() {
        CSSearchableIndex.defaultSearchableIndex().deleteAllSearchableItemsWithCompletionHandler { error ->
            if (error != null) {
                println("Spotlight clear all error: ${error.localizedDescription}")
            }
        }
    }
}

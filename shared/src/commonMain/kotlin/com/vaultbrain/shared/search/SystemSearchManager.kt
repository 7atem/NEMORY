package com.vaultbrain.shared.search

/**
 * A data class representing an item to be indexed in the OS-level search
 * (e.g., iOS Spotlight Search or Android AppSearch).
 */
data class SearchableVaultItem(
    val id: String,
    val title: String,
    val contentDescription: String,
    val keywords: List<String>,
    val type: String
)

/**
 * Cross-platform manager for indexing Vault content into the OS-level system search.
 * This makes the app highly engaging by allowing users to find Vault documents
 * right from their phone's home screen.
 */
interface SystemSearchManager {
    
    /**
     * Indexes a single item into the system search.
     */
    fun indexItem(item: SearchableVaultItem)
    
    /**
     * Removes an item from the system search (e.g. when deleted or archived).
     */
    fun removeItem(id: String)
    
    /**
     * Clears all indexed items (e.g. when Decoy Mode is activated).
     */
    fun clearAll()
}

/** CompositionLocal for providing the platform-specific SystemSearchManager. */
val LocalSystemSearchManager = androidx.compose.runtime.staticCompositionLocalOf<SystemSearchManager?> { null }

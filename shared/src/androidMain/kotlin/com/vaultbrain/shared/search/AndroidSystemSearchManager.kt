package com.vaultbrain.shared.search

/**
 * Placeholder for Android AppSearch integration.
 * In the future, this will index vault items into Android's on-device AppSearch index.
 */
class AndroidSystemSearchManager : SystemSearchManager {
    
    override fun indexItem(item: SearchableVaultItem) {
        // TODO: Implement AndroidX AppSearch indexing
    }

    override fun removeItem(id: String) {
        // TODO: Implement AndroidX AppSearch removal
    }

    override fun clearAll() {
        // TODO: Implement AndroidX AppSearch clearing
    }
}

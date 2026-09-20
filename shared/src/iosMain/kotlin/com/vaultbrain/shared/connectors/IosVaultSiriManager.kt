package com.vaultbrain.shared.connectors

import platform.Intents.*
import kotlinx.cinterop.ExperimentalForeignApi

class IosVaultSiriManager : VaultSiriManager {
    
    @OptIn(ExperimentalForeignApi::class)
    override fun donateSearchIntent(query: String) {
        // In a real app, this would use a custom INIntent or AppIntent generated class.
        // For now, we donate a standard user activity for Siri Suggestions.
        val userActivity = platform.Foundation.NSUserActivity("com.nemory.app.search")
        userActivity.title = "Search Nemory for $query"
        userActivity.eligibleForSearch = true
        userActivity.eligibleForPrediction = true
        userActivity.becomeCurrent()
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun donateCaptureIntent(type: String) {
        val userActivity = platform.Foundation.NSUserActivity("com.nemory.app.capture")
        userActivity.title = "Save $type to Nemory"
        userActivity.eligibleForSearch = true
        userActivity.eligibleForPrediction = true
        userActivity.becomeCurrent()
    }
}

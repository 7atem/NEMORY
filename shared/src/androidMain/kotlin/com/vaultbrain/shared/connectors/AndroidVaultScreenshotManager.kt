package com.vaultbrain.shared.connectors

class AndroidVaultScreenshotManager : VaultScreenshotManager {
    override suspend fun requestPermission(): Boolean {
        // Needs Android READ_MEDIA_IMAGES permission handling
        return false
    }

    override fun hasPermission(): Boolean {
        return false
    }

    override suspend fun getRecentScreenshots(limit: Int): List<ScreenshotItem> {
        return emptyList()
    }
}

package com.vaultbrain.shared.connectors

import platform.Photos.*
import platform.Foundation.NSSortDescriptor
import platform.Foundation.timeIntervalSince1970
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.cinterop.ExperimentalForeignApi

class IosVaultScreenshotManager : VaultScreenshotManager {

    override suspend fun requestPermission(): Boolean = suspendCoroutine { continuation ->
        PHPhotoLibrary.requestAuthorization { status ->
            continuation.resume(status == PHAuthorizationStatusAuthorized)
        }
    }

    override fun hasPermission(): Boolean {
        return PHPhotoLibrary.authorizationStatus() == PHAuthorizationStatusAuthorized
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun getRecentScreenshots(limit: Int): List<ScreenshotItem> {
        if (!hasPermission()) return emptyList()

        val fetchOptions = PHFetchOptions()
        fetchOptions.sortDescriptors = listOf(NSSortDescriptor(key = "creationDate", ascending = false))
        fetchOptions.fetchLimit = limit.toULong()

        // Filter for screenshots
        val fetchResult = PHAsset.fetchAssetsWithMediaType(PHAssetMediaTypeImage, fetchOptions)
        
        val screenshots = mutableListOf<ScreenshotItem>()
        for (i in 0 until fetchResult.count.toInt()) {
            val asset = fetchResult.objectAtIndex(i.toULong()) as? PHAsset
            asset?.let {
                // PHAssetMediaSubtypePhotoScreenshot is 2 (1UL shl 1) but in C it's usually defined.
                // Kotlin/Native maps it as PHAssetMediaSubtypePhotoScreenshot.
                if ((it.mediaSubtypes and PHAssetMediaSubtypePhotoScreenshot) != 0UL) {
                    screenshots.add(
                        ScreenshotItem(
                            id = it.localIdentifier,
                            creationDateMs = (it.creationDate?.timeIntervalSince1970?.times(1000))?.toLong() ?: 0L,
                            filePath = null // PHAsset requires asynchronous fetching to get actual file URL
                        )
                    )
                }
            }
        }
        
        return screenshots
    }
}

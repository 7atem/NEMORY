package com.vaultbrain.feature.capture.coil

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.vaultbrain.feature.capture.MediaVaultStorage
import okio.buffer
import okio.source

class VaultMediaFetcher(
    private val options: Options,
    private val uri: Uri,
    private val mediaVaultStorage: MediaVaultStorage
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val inputStream = mediaVaultStorage.openInputStream(uri) ?: return null
        return SourceResult(
            source = ImageSource(inputStream.source().buffer(), options.context),
            mimeType = mediaVaultStorage.mimeType(uri),
            dataSource = DataSource.DISK
        )
    }

    class Factory(
        private val mediaVaultStorage: MediaVaultStorage
    ) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (mediaVaultStorage.isVaultMedia(data)) {
                return VaultMediaFetcher(options, data, mediaVaultStorage)
            }
            return null
        }
    }
}

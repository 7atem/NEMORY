package com.vaultbrain.feature.capture

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Takes ownership of incoming media before the temporary URI grant disappears.
 * Files live in private, no-backup app storage and are never exposed through MediaStore.
 */
@Singleton
class MediaVaultStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mediaDirectory: File
        get() = File(context.noBackupFilesDir, "vault_media").apply { mkdirs() }

    suspend fun import(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val sourceFile = uri.takeIf { it.scheme == "file" }?.path?.let(::File)
        if (sourceFile != null && sourceFile.parentFile?.canonicalFile == mediaDirectory.canonicalFile) {
            return@withContext uri
        }

        val displayName = queryDisplayName(uri)
        val extension = displayName.substringAfterLast('.', "")
            .takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?: extensionFor(context.contentResolver.getType(uri))
        val destination = File(
            mediaDirectory,
            buildString {
                append(UUID.randomUUID())
                if (extension.isNotBlank()) append('.').append(extension.lowercase())
            }
        )

        val sourceInCache = sourceFile != null &&
            sourceFile.parentFile?.canonicalFile == context.cacheDir.canonicalFile
        // Same-filesystem moves are O(1); only stream bytes when a rename is not possible.
        val moved = sourceInCache && sourceFile!!.renameTo(destination)
        if (!moved) {
            val input = if (sourceFile != null) {
                FileInputStream(sourceFile)
            } else {
                context.contentResolver.openInputStream(uri)
                    ?: error("Unable to read selected media")
            }
            input.use { source -> destination.outputStream().use(source::copyTo) }
            if (sourceInCache) sourceFile!!.delete()
        }
        Uri.fromFile(destination)
    }

    fun mimeType(uri: Uri): String? {
        context.contentResolver.getType(uri)?.let { return it }
        return when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heif"
            "pdf" -> "application/pdf"
            else -> null
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment.orEmpty()
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull().orEmpty()
    }

    private fun extensionFor(mimeType: String?): String = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heif", "image/heic" -> "heic"
        "application/pdf" -> "pdf"
        else -> "bin"
    }
}

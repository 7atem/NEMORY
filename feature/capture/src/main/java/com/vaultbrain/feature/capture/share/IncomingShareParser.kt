package com.vaultbrain.feature.capture.share

import android.app.Activity
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Parses an incoming Android share intent into a normalized [IncomingShare].
 *
 * Supports [Intent.ACTION_SEND] and [Intent.ACTION_SEND_MULTIPLE] for the MIME
 * types declared in the manifest, and validates that the received URIs are
 * actually readable and within size limits.
 */
class IncomingShareParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun parse(activity: Activity, intent: Intent): IncomingShare {
        val action = intent.action
        val mimeType = intent.resolveType(context)
        val receivedAt = System.currentTimeMillis()

        val rawText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)

        val text = when {
            mimeType.equals("text/html", ignoreCase = true) -> {
                ShareContentValidator.extractTextFromHtml(rawText)
                    ?: rawText?.trim()
            }

            else -> rawText?.trim()
        }

        val uris = when (action) {
            Intent.ACTION_SEND -> {
                val streamUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                listOfNotNull(streamUri)
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    .orEmpty()
            }

            else -> emptyList()
        }.ifEmpty {
            intent.clipData?.items().orEmpty()
        }

        val sourcePackage = resolveSourcePackage(activity, intent)
        val url = ShareContentValidator.extractUrl(text ?: subject)

        return IncomingShare(
            mimeType = mimeType,
            subject = subject?.trim(),
            text = text,
            url = url,
            uris = uris,
            sourcePackage = sourcePackage,
            receivedAt = receivedAt
        )
    }

    /**
     * Validates that the share can be safely imported.
     *
     * Rejects unsupported MIME types, unreadable URIs, non-content schemes,
     * oversized files, and empty payloads.
     */
    fun validate(share: IncomingShare): ShareValidationResult {
        if (share.isEmpty) {
            return ShareValidationResult.Invalid("No usable content was shared")
        }

        val mime = share.mimeType
        if (!mime.isNullOrBlank() && !ShareContentValidator.isSupportedMime(mime)) {
            return ShareValidationResult.Invalid("Unsupported content type: $mime")
        }

        share.uris.forEach { uri ->
            val schemeValidation = validateUriScheme(uri)
            if (schemeValidation != null) return schemeValidation

            val permissionValidation = validateUriReadability(uri)
            if (permissionValidation != null) return permissionValidation

            val sizeValidation = validateUriSize(uri)
            if (sizeValidation != null) return sizeValidation
        }

        return ShareValidationResult.Valid(share)
    }

    private fun validateUriScheme(uri: Uri): ShareValidationResult.Invalid? {
        return if (ShareContentValidator.isAllowedUriScheme(uri.scheme)) {
            null
        } else {
            ShareValidationResult.Invalid("URI scheme not allowed: ${uri.scheme}")
        }
    }

    private fun validateUriReadability(uri: Uri): ShareValidationResult.Invalid? {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.close()
            null
        }.getOrElse {
            ShareValidationResult.Invalid("Cannot read shared file: ${uri.lastPathSegment}")
        }
    }

    private fun validateUriSize(uri: Uri): ShareValidationResult.Invalid? {
        val size = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull() ?: return null

        return if (size > ShareContentValidator.MAX_FILE_SIZE_BYTES) {
            ShareValidationResult.Invalid("Shared file is too large")
        } else {
            null
        }
    }

    private fun resolveSourcePackage(activity: Activity, intent: Intent): String? {
        // Some sending apps explicitly set their package name.
        @Suppress("DEPRECATION")
        val extraPackage = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
            ?: intent.getStringExtra(EXTRA_CALLING_PACKAGE)

        if (!extraPackage.isNullOrBlank()) return extraPackage

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            activity.referrer?.authority
        } else {
            null
        }
    }

    private fun ClipData.items(): List<Uri> {
        return (0 until itemCount).mapNotNull { getItemAt(it).uri }
    }

    companion object {
        private const val EXTRA_CALLING_PACKAGE = "androidx.core.app.EXTRA_CALLING_PACKAGE"
    }
}

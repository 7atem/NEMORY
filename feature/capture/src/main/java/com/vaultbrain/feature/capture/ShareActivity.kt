package com.vaultbrain.feature.capture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vaultbrain.core.common.model.SourceType
import com.vaultbrain.feature.capture.share.IncomingShare
import com.vaultbrain.feature.capture.share.IncomingShareParser
import com.vaultbrain.feature.capture.share.ShareReviewScreen
import com.vaultbrain.feature.capture.share.ShareValidationResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Entry point for external shares.
 *
 * Supports [Intent.ACTION_SEND] / [Intent.ACTION_SEND_MULTIPLE] for the MIME types
 * registered in the manifest. The share is parsed, validated, and shown on a review
 * screen before being handed to the existing capture pipeline. This ensures the user
 * always has a chance to cancel or route the item to a collection before saving.
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    @Inject
    lateinit var parser: IncomingShareParser
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug builds support physical-device screenshot reviews. Release vaults stay secure.
        // Screenshot protection is disabled at the owner's request.

        val result = runCatching { parser.parse(this, intent) }
            .map { parser.validate(it) }
            .getOrElse { ShareValidationResult.Invalid(it.message ?: "Could not parse share") }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (result) {
                        is ShareValidationResult.Invalid -> {
                            android.widget.Toast.makeText(
                                this@ShareActivity,
                                result.reason,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            finish()
                        }

                        is ShareValidationResult.Valid -> {
                            ShareRouter(share = result.share)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ShareRouter(share: IncomingShare) {
        var pendingInput by remember { mutableStateOf<CaptureInput?>(null) }

        val captureInput = pendingInput
        if (captureInput != null) {
            CaptureFlowScreen(
                initialInput = captureInput,
                onComplete = { finish() }
            )
        } else {
            ShareReviewScreen(
                share = share,
                onCancel = { finish() },
                onSave = { collectionId, skipLlm ->
                    pendingInput = share.toCaptureInput(
                        targetCollectionId = collectionId,
                        skipLlmEnrichment = skipLlm
                    )
                }
            )
        }
    }

    private fun IncomingShare.toCaptureInput(
        targetCollectionId: String?,
        skipLlmEnrichment: Boolean
    ): CaptureInput = CaptureInput(
        initialUris = uris,
        initialText = text?.takeIf { it.isNotBlank() },
        sourceType = SourceType.APP_SHARE,
        provenanceMetadata = buildProvenance(),
        skipLlmEnrichment = skipLlmEnrichment,
        targetCollectionId = targetCollectionId
    )

    private fun IncomingShare.buildProvenance(): Map<String, String> = buildMap {
        put("share_source", "ANDROID_SHARE")
        sourcePackage?.let { put("share_source_package", it) }
        url?.let { put("share_url", it) }
        put("share_received_at", receivedAt.toString())
    }
}

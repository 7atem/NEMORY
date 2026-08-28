package com.vaultbrain.feature.capture

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.vaultbrain.core.common.model.SourceType
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point for external shares.
 *
 * Handles:
 * - [Intent.ACTION_SEND] / [Intent.ACTION_SEND_MULTIPLE] for image and PDF types
 * - [Intent.ACTION_SEND] for text/plain
 *
 * Incoming URIs/text are routed into the capture/review flow so the user can pick an
 * experience and review the extracted details before saving.
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        val input = parseIntent(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (input.initialUris.isEmpty() && input.initialText.isNullOrBlank()) {
                        android.widget.Toast.makeText(
                            this@ShareActivity,
                            "Unsupported content type or missing data.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        finish()
                    } else {
                        FastShareIngestionScreen(
                            input = input,
                            onComplete = { finish() }
                        )
                    }
                }
            }
        }
    }
    private fun parseIntent(intent: Intent): CaptureInput {
        val action = intent.action
        val type = intent.type ?: ""

        val uris = mutableListOf<Uri>()
        if (action == Intent.ACTION_SEND) {
            (intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uris.add(it) }
        } else if (action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getParcelableArrayListExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)?.forEach {
                if (it is Uri) uris.add(it)
            }
        }
        
        if (uris.isEmpty()) {
            uris.addAll(clipDataUris(intent))
        }

        return when {
            (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) && uris.isNotEmpty() -> {
                CaptureInput(
                    initialUris = uris.toList(),
                    sourceType = SourceType.APP_SHARE
                )
            }

            Intent.ACTION_SEND == action && type == "text/plain" -> {
                val sharedText = listOfNotNull(
                    intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf(String::isNotBlank),
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf(String::isNotBlank)
                ).distinct().joinToString("\n")
                CaptureInput(
                    initialText = sharedText.takeIf(String::isNotBlank),
                    sourceType = SourceType.APP_SHARE
                )
            }

            else -> CaptureInput()
        }
    }

    private fun clipDataUris(intent: Intent): List<Uri> {
        val clipData = intent.clipData ?: return emptyList()
        return (0 until clipData.itemCount).mapNotNull { clipData.getItemAt(it).uri }
    }
}

@Composable
private fun FastShareIngestionScreen(
    input: CaptureInput,
    onComplete: () -> Unit,
    viewModel: ShareIngestionViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(input) {
        viewModel.ingest(input)
    }

    androidx.compose.runtime.LaunchedEffect(state) {
        when (state) {
            is ShareIngestionState.Saved -> {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.feature_capture_share_saved),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                onComplete()
            }
            is ShareIngestionState.Error -> {
                android.widget.Toast.makeText(context, (state as ShareIngestionState.Error).message, android.widget.Toast.LENGTH_LONG).show()
                onComplete()
            }
            else -> {}
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator()
    }

}

package com.vaultbrain.feature.capture

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.shared.model.EnrichmentState
import com.vaultbrain.shared.model.ProcessingState
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.feature.capture.worker.DeferredAnalysisWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

sealed interface ShareIngestionState {
    data object Idle : ShareIngestionState
    data object Saving : ShareIngestionState
    data class Saved(val count: Int) : ShareIngestionState
    data class Error(val message: String) : ShareIngestionState
}

/**
 * Fast share-target path: take ownership of the media, persist PENDING placeholders, enqueue
 * [DeferredAnalysisWorker] for the heavy OCR/vision work, and return immediately.
 */
@HiltViewModel
class ShareIngestionViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val mediaVaultStorage: MediaVaultStorage,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow<ShareIngestionState>(ShareIngestionState.Idle)
    val state: StateFlow<ShareIngestionState> = _state.asStateFlow()

    fun ingest(input: CaptureInput) {
        if (_state.value != ShareIngestionState.Idle) return
        viewModelScope.launch {
            _state.value = ShareIngestionState.Saving
            runCatching {
                withContext(Dispatchers.IO) {
                    val items = mutableListOf<VaultItem>()
                    input.initialUris.forEach { incoming ->
                        val owned = mediaVaultStorage.import(incoming)
                        val placeholder = VaultItem(
                            id = UUID.randomUUID().toString(),
                            title = "Captured item",
                            sourceType = input.sourceType,
                            capturedImageUri = owned.toString(),
                            parsedMetadata = mediaVaultStorage.mimeType(owned)
                                ?.let { mapOf("media_mime_type" to it) }
                                .orEmpty(),
                            lensTags = input.preferredLensTags,
                            extractionState = ProcessingState.PENDING,
                            enrichmentState = EnrichmentState.PENDING,
                            indexingState = ProcessingState.PENDING
                        )
                        items += placeholder
                    }
                    input.initialText?.trim()?.takeIf(String::isNotBlank)?.let { text ->
                        val isUrl = android.util.Patterns.WEB_URL.matcher(text).matches()
                        
                        // Parse bulk text shares (e.g. from Notes apps) as distinct records if separated by double newlines
                        val textBlocks = if (!isUrl && text.contains("\n\n")) {
                            text.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }
                        } else {
                            listOf(text)
                        }
                        
                        textBlocks.forEach { block ->
                            val placeholder = VaultItem(
                                id = UUID.randomUUID().toString(),
                                title = if (isUrl) block.take(120) else block.lineSequence().first().take(120),
                                summary = if (isUrl) "" else block.take(300),
                                rawOcrText = block,
                                sourceType = input.sourceType,
                                lensTags = input.preferredLensTags,
                                extractionState = if (isUrl) ProcessingState.PENDING else ProcessingState.COMPLETE,
                                enrichmentState = EnrichmentState.PENDING,
                                indexingState = ProcessingState.PENDING
                            )
                            items += placeholder
                        }
                    }
                    check(items.isNotEmpty()) { "No supported shared content" }
                    items.forEach { repository.save(it) }
                    items.size
                }
            }.onSuccess { count ->
                DeferredAnalysisWorker.enqueue(context)
                _state.value = ShareIngestionState.Saved(count)
            }.onFailure { error ->
                _state.value = ShareIngestionState.Error(error.message ?: "Could not save shared content")
            }
        }
    }
}

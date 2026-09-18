package com.vaultbrain.feature.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.vaultbrain.core.ai.embeddings.TextEmbeddingModel
import com.vaultbrain.core.ai.embeddings.VisionEmbeddingModel
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.vectorstore.VectorStore
import com.vaultbrain.core.vectorstore.entity.VaultEmbedding
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates and stores vector embeddings for a vault item.
 *
 * Mirrors what retrieval reads:
 * - `contentType = "ocr_text"` — the raw OCR text (single chunk, capped).
 * - `contentType = "summary"` — title + summary + metadata in the same labeled layout
 *   `RagEngine.buildPrompt` uses for records.
 * - `contentType = "image"` — a visual embedding of the stored image (or first PDF page);
 *   retrieval applies a cross-modal penalty to these.
 *
 * Both models emit L2-normalized 100-dim vectors, matching the ObjectBox HNSW index.
 */
@Singleton
class VaultEmbeddingGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textEmbeddingModel: TextEmbeddingModel,
    private val visionEmbeddingModel: VisionEmbeddingModel,
    private val vectorStore: VectorStore,
    private val mediaVaultStorage: MediaVaultStorage,
    private val collectionSuggestionMatcher: com.vaultbrain.feature.capture.collections.CollectionSuggestionMatcher,
    private val knowledgeIndexer: DocumentKnowledgeIndexer? = null
) {

    /** Generates and stores embeddings for [item]. Items with no embeddable content are cleared. */
    suspend fun indexItem(item: VaultItem) = withContext(Dispatchers.Default) {
        knowledgeIndexer?.index(item)
        val now = System.currentTimeMillis()
        val embeddings = mutableListOf<VaultEmbedding>()

        if (textEmbeddingModel.isAvailable()) {
            item.rawOcrText?.takeIf(String::isNotBlank)?.let { ocr ->
                embeddings += VaultEmbedding(
                    itemId = item.id,
                    embedding = textEmbeddingModel.encode(ocr.take(MAX_OCR_EMBED_CHARS)),
                    contentType = CONTENT_TYPE_OCR,
                    chunkIndex = 0,
                    createdAt = now
                )
            }
            val summaryText = summaryTextOf(item)
            if (summaryText.isNotBlank()) {
                embeddings += VaultEmbedding(
                    itemId = item.id,
                    embedding = textEmbeddingModel.encode(summaryText),
                    contentType = CONTENT_TYPE_SUMMARY,
                    chunkIndex = 0,
                    createdAt = now
                )
            }
        }

        if (visionEmbeddingModel.isAvailable()) {
            val preview = item.capturedImageUri?.let { decodePreview(Uri.parse(it)) }
            if (preview != null) {
                try {
                    embeddings += VaultEmbedding(
                        itemId = item.id,
                        embedding = visionEmbeddingModel.encode(preview),
                        contentType = CONTENT_TYPE_IMAGE,
                        chunkIndex = 0,
                        createdAt = now
                    )
                } finally {
                    preview.recycle()
                }
            }
        }

        if (embeddings.isEmpty()) {
            vectorStore.deleteEmbeddingsForItem(item.id)
        } else {
            vectorStore.putEmbeddingsForItem(item.id, embeddings)
        }

        // Run collection semantic matching
        collectionSuggestionMatcher.match(item)
    }

    private fun summaryTextOf(item: VaultItem): String = buildString {
        if (item.title.isNotBlank()) append("Title: ").append(item.title).append('\n')
        item.summary?.takeIf(String::isNotBlank)?.let { append("Summary: ").append(it).append('\n') }
        item.subtype?.takeIf(String::isNotBlank)?.let { append("Subtype: ").append(it).append('\n') }
        if (item.topics.isNotEmpty()) append("Topics: ").append(item.topics.joinToString(", ")).append('\n')
        if (item.entities.isNotEmpty()) append("Entities: ").append(item.entities.joinToString(", ")).append('\n')
        if (item.tags.isNotEmpty()) append("Tags: ").append(item.tags.joinToString(", ")).append('\n')
        item.parsedMetadata.forEach { (key, value) -> append(key).append(": ").append(value).append('\n') }
    }.trim().take(MAX_SUMMARY_EMBED_CHARS)

    private fun decodePreview(uri: Uri): Bitmap? =
        if (mediaVaultStorage.mimeType(uri) == "application/pdf") {
            renderPdfFirstPage(uri)
        } else {
            decodeBitmap(uri)
        }

    private fun decodeBitmap(uri: Uri): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            if (info.size.width > MAX_IMAGE_DIM || info.size.height > MAX_IMAGE_DIM) {
                val scale = MAX_IMAGE_DIM.toFloat() / maxOf(info.size.width, info.size.height)
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1)
                )
            }
        }
    }.getOrNull()

    private fun renderPdfFirstPage(uri: Uri): Bitmap? = runCatching {
        val file = File(requireNotNull(uri.path))
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) return@runCatching null
                renderer.openPage(0).use { page ->
                    val scale = (MAX_IMAGE_DIM.toFloat() / page.width).coerceAtMost(1f)
                    createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    ).also { bitmap ->
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }.getOrNull()

    private companion object {
        const val CONTENT_TYPE_OCR = "ocr_text"
        const val CONTENT_TYPE_SUMMARY = "summary"
        const val CONTENT_TYPE_IMAGE = "image"
        const val MAX_OCR_EMBED_CHARS = 2_000
        const val MAX_SUMMARY_EMBED_CHARS = 1_000
        const val MAX_IMAGE_DIM = 512
    }
}

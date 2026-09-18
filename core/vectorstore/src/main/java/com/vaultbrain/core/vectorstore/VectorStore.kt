package com.vaultbrain.core.vectorstore

import android.content.Context
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.vectorstore.entity.CollectionEmbedding
import com.vaultbrain.core.vectorstore.entity.CollectionEmbedding_
import com.vaultbrain.core.vectorstore.entity.VaultEmbedding
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import com.vaultbrain.core.vectorstore.entity.MyObjectBox
import com.vaultbrain.core.vectorstore.entity.VaultEmbedding_
/**
 * Manages the ObjectBox vector store for semantic and visual search.
 *
 * Note: The ObjectBox database file lives in app-private storage. It is not encrypted by
 * SQLCipher. If desired, additional file-level encryption (e.g., Jetpack Security with
 * encrypted files) can be layered on top; here we rely on Android sandboxing.
 * All operations are gated by [DecoySessionState] to ensure complete isolation in decoy mode.
 */
@Singleton
class VectorStore @Inject constructor(
    private val context: Context
) : CollectionVectorStore {

    private val boxStore: BoxStore? by lazy {
        runCatching {
            MyObjectBox.builder()
                .androidContext(context.applicationContext)
                .build()
        }.getOrNull()
    }

    private val embeddingBox: Box<VaultEmbedding>? by lazy { boxStore?.boxFor() }

    private val collectionEmbeddingBox: Box<CollectionEmbedding>? by lazy { boxStore?.boxFor() }

    /**
     * Inserts or updates all embeddings for an item. Existing embeddings for the item are
     * replaced atomically within a single transaction.
     *
     * All embeddings must share the same dimension because the ObjectBox HNSW index is
     * fixed-size. Mixed dimensions will throw at insert time.
     */
    fun putEmbeddingsForItem(itemId: String, embeddings: List<VaultEmbedding>) {
        if (DecoySessionState.isDecoy.value || embeddings.isEmpty()) return
        val dimensions = embeddings.mapNotNull { it.embedding?.size }.distinct()
        if (dimensions.size > 1) return

        runCatching {
            val store = boxStore ?: return
            val box = embeddingBox ?: return
            store.runInTx {
                // Remove stale embeddings for this item.
                val existing = box.query(VaultEmbedding_.itemId.equal(itemId))
                    .build()
                    .find()
                box.remove(existing)
                box.put(embeddings)
            }
        }
    }

    /**
     * Deletes all embeddings associated with [itemId].
     */
    fun deleteEmbeddingsForItem(itemId: String) {
        if (DecoySessionState.isDecoy.value) return
        runCatching {
            val box = embeddingBox ?: return
            val existing = box.query(VaultEmbedding_.itemId.equal(itemId))
                .build()
                .find()
            box.remove(existing)
        }
    }

    /**
     * Finds the top-k nearest neighbors for a query vector.
     *
     * ObjectBox HNSW nearest-neighbor search is used when the property is indexed.
     */
    fun nearestNeighbors(
        queryVector: FloatArray,
        topK: Int = 50,
        contentType: String? = null
    ): List<VaultEmbedding> {
        if (DecoySessionState.isDecoy.value) return emptyList()
        return runCatching {
            val box = embeddingBox ?: return emptyList()
            val nearestNeighborCondition =
                VaultEmbedding_.embedding.nearestNeighbors(queryVector, topK)
            val condition = contentType?.let {
                nearestNeighborCondition.and(VaultEmbedding_.contentType.equal(it))
            } ?: nearestNeighborCondition
            box.query(condition).build().find()
        }.getOrDefault(emptyList())
    }

    /**
     * Computes cosine similarity between two vectors.
     */
    override fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return if (normA == 0.0 || normB == 0.0) 0f
        else (dot / (sqrt(normA) * sqrt(normB))).toFloat()
    }

    // ---- Collection embeddings (CollectionVectorStore) ----

    override fun putCollectionEmbedding(embedding: CollectionEmbedding) {
        if (DecoySessionState.isDecoy.value) return
        runCatching {
            val store = boxStore ?: return
            val box = collectionEmbeddingBox ?: return
            store.runInTx {
                val existing = box.query(CollectionEmbedding_.collectionId.equal(embedding.collectionId))
                    .build()
                    .find()
                box.remove(existing)
                box.put(embedding)
            }
        }
    }

    override fun deleteCollectionEmbedding(collectionId: String) {
        if (DecoySessionState.isDecoy.value) return
        runCatching {
            val box = collectionEmbeddingBox ?: return
            val existing = box.query(CollectionEmbedding_.collectionId.equal(collectionId))
                .build()
                .find()
            box.remove(existing)
        }
    }

    override fun getCollectionEmbedding(collectionId: String): CollectionEmbedding? {
        if (DecoySessionState.isDecoy.value) return null
        return runCatching {
            val box = collectionEmbeddingBox ?: return null
            box.query(CollectionEmbedding_.collectionId.equal(collectionId)).build().findFirst()
        }.getOrNull()
    }

    override fun getAllCollectionEmbeddings(): List<CollectionEmbedding> {
        if (DecoySessionState.isDecoy.value) return emptyList()
        return runCatching {
            collectionEmbeddingBox?.all ?: emptyList()
        }.getOrDefault(emptyList())
    }

    override fun nearestCollectionNeighbors(queryVector: FloatArray, topK: Int): List<CollectionEmbedding> {
        if (DecoySessionState.isDecoy.value) return emptyList()
        return runCatching {
            val box = collectionEmbeddingBox ?: return emptyList()
            box.query(CollectionEmbedding_.embedding.nearestNeighbors(queryVector, topK))
                .build()
                .find()
        }.getOrDefault(emptyList())
    }

    /**
     * Number of embeddings currently stored.
     */
    fun count(): Long {
        if (DecoySessionState.isDecoy.value) return 0L
        return runCatching { embeddingBox?.count() ?: 0L }.getOrDefault(0L)
    }

    /**
     * True when at least one embedding row exists for [itemId]. Used by the embedding
     * backfill to cheaply skip items that are already indexed.
     */
    fun hasEmbeddingsForItem(itemId: String): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        return runCatching {
            val box = embeddingBox ?: return false
            box.query(VaultEmbedding_.itemId.equal(itemId)).build().count() > 0
        }.getOrDefault(false)
    }

    /**
     * Closes the store. Call only when the app is being destroyed.
     */
    fun close() {
        runCatching { boxStore?.close() }
    }
}

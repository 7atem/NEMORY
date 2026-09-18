package com.vaultbrain.core.ai.rag

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.vaultbrain.core.common.security.DecoySessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Disposable encrypted cache; never stores private insight text in WorkManager or plain prefs. */
@Singleton
class DailyInsightStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "daily_intelligence.bin"))
    private val lock = Any()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private fun read(): JsonObject = runCatching {
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size in 29..100_000)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        Json.parseToJsonElement(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)).jsonObject
    }.getOrElse { buildJsonObject {} }

    private fun write(value: JsonObject) {
        if (DecoySessionState.isDecoy.value) return
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.iv + cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    }

    suspend fun cached(revision: String): List<DailyInsight>? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (DecoySessionState.isDecoy.value) return@synchronized null
            val state = read()
            if (state["revision"]?.jsonPrimitive?.content != revision ||
                System.currentTimeMillis() - (state["savedAt"]?.jsonPrimitive?.longOrNull ?: 0L) !in 0..21_600_000L) return@synchronized null
            val dismissed = state["dismissed"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.toSet()
            val result = state["insights"]?.jsonArray.orEmpty().mapNotNull { value -> runCatching {
                val item = value.jsonObject
                DailyInsight(item["id"]!!.jsonPrimitive.content, item["title"]!!.jsonPrimitive.content,
                    item["evidence"]!!.jsonPrimitive.content, item["updated"]!!.jsonPrimitive.long)
            }.getOrNull() }.filter { insightId(it) !in dismissed }
            if (DecoySessionState.isDecoy.value) null else result
        }
    }

    suspend fun save(revision: String, insights: List<DailyInsight>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val dismissed = read()["dismissed"] ?: JsonArray(emptyList())
            write(buildJsonObject {
                put("revision", revision); put("savedAt", System.currentTimeMillis()); put("dismissed", dismissed)
                put("insights", JsonArray(insights.take(3).map { buildJsonObject {
                    put("id", it.itemId); put("title", it.title); put("evidence", it.evidence); put("updated", it.sourceUpdatedAt)
                } }))
            })
        }
    }

    suspend fun dismiss(insight: DailyInsight) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (DecoySessionState.isDecoy.value) return@synchronized
            val state = read()
            val ids = (state["dismissed"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content } + insightId(insight)).distinct().takeLast(100)
            write(JsonObject(state + ("dismissed" to JsonArray(ids.map(::JsonPrimitive)))))
        }
    }

    companion object {
        private const val ALIAS = "nemory_daily_intelligence_v1"
        fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        fun insightId(insight: DailyInsight) = digest("${insight.itemId}|${insight.sourceUpdatedAt}")
    }
}

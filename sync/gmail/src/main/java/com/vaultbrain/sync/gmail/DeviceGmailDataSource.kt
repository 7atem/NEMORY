package com.vaultbrain.sync.gmail

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vaultbrain.shared.model.external.ConnectionState
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.integrations.google.GoogleApiException
import com.vaultbrain.core.integrations.google.GoogleDeviceApi
import com.vaultbrain.core.integrations.google.GoogleDeviceAuthorization
import com.vaultbrain.core.integrations.google.GoogleService
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceGmailDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: GoogleDeviceApi,
    private val authorization: GoogleDeviceAuthorization,
    private val repository: ExternalContextRepository
) : GmailDataSource {
    override suspend fun isConfigured() = true // Runtime authorization reports missing developer registration honestly.
    override suspend fun authorizedAccounts() = repository.observeConnections().first()
        .filter { it.connectorId == "gmail" && it.state == ConnectionState.CONNECTED }.map { it.accountId }
    override suspend fun authorize(accountId: String?): Result<String> = runCatching {
        val account = requireNotNull(accountId)
        authorization.token(GoogleService.GMAIL, account)
        account
    }
    override suspend fun revoke(accountId: String): Result<Unit> = runCatching { authorization.disconnect(accountId) }
    override suspend fun messages(accountId: String, newerThan: Long, limit: Int): List<GmailMessageRow> =
        page(accountId, newerThan, limit, null).messages

    override suspend fun page(accountId: String, newerThan: Long, limit: Int, cursor: String?): GmailSyncPage {
        check(!DecoySessionState.isDecoy.value)
        val state = cursor?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val history = state?.text("history")
        if (history != null) {
            try { return incremental(accountId, history, state.text("page"), newerThan, limit) }
            catch (error: GoogleApiException) { if (error.status != 404) throw error }
        }
        val snapshot = state?.text("snapshot") ?: get(accountId, "/profile").text("historyId")
        val query = mutableMapOf("maxResults" to limit.coerceIn(1, 100).toString(), "q" to "after:${newerThan / 1000} -in:trash -in:spam")
        state?.text("fullPage")?.let { query["pageToken"] = it }
        val page = get(accountId, "/messages", query)
        val rows = page.array("messages").take(limit.coerceIn(1, 100)).mapNotNull { reference ->
            fetch(accountId, reference.jsonObject.text("id") ?: return@mapNotNull null)
        }
        val next = page.text("nextPageToken")
        return GmailSyncPage(rows, cursor = buildJsonObject {
            if (next != null) { put("fullPage", next); snapshot?.let { put("snapshot", it) } }
            else snapshot?.let { put("history", it) }
        }.toString())
    }

    private suspend fun incremental(account: String, history: String, token: String?, newerThan: Long, limit: Int): GmailSyncPage {
        val query = mutableMapOf("startHistoryId" to history, "maxResults" to "30")
        token?.let { query["pageToken"] = it }
        val page = get(account, "/history", query)
        val changed = linkedSetOf<String>()
        val deleted = linkedSetOf<String>()
        page.array("history").forEach { entry ->
            val record = entry.jsonObject
            record.array("messagesDeleted").forEach { it.jsonObject["message"]?.jsonObject?.text("id")?.let(deleted::add) }
            listOf("messagesAdded", "labelsAdded", "labelsRemoved").forEach { key ->
                record.array(key).forEach { it.jsonObject["message"]?.jsonObject?.text("id")?.let(changed::add) }
            }
        }
        // Never advance the cursor past unseen changes. Oversized histories restart a bounded full scan.
        if (changed.size > limit.coerceIn(1, 200)) return this.page(account, newerThan, limit, null)
        val rows = changed.mapNotNull { id ->
            val row = fetch(account, id)
            if (row == null || row.receivedAt < newerThan || row.labels.any { it == "TRASH" || it == "SPAM" }) {
                deleted += id; null
            } else { deleted.remove(id); row }
        }
        val next = page.text("nextPageToken")
        val newCursor = buildJsonObject {
            put("history", if (next != null) history else page.text("historyId") ?: history)
            next?.let { put("page", it) }
        }.toString()
        return GmailSyncPage(rows, deleted, newCursor)
    }

    private suspend fun fetch(account: String, id: String): GmailMessageRow? = try {
        val message = get(account, "/messages/${GoogleDeviceApi.encode(id)}", mapOf("format" to "full"))
        val payload = message["payload"]?.jsonObject ?: buildJsonObject { }
        val headers = payload.array("headers").associate { it.jsonObject.text("name").orEmpty().lowercase() to it.jsonObject.text("value").orEmpty() }
        GmailMessageRow(id, message.text("threadId") ?: id, headers["subject"].orEmpty().take(240), headers["from"],
            plainText(payload).ifBlank { message.text("snippet").orEmpty() }.take(16000),
            message.text("internalDate")?.toLongOrNull() ?: 0L,
            message.array("labelIds").map { it.jsonPrimitive.content }.toSet())
    } catch (error: GoogleApiException) { if (error.status == 404) null else throw error }

    private suspend fun get(account: String, path: String, query: Map<String, String> = emptyMap()) = api.get(GoogleService.GMAIL, account, path, query)

    override fun open(uri: String): Boolean {
        if (DecoySessionState.isDecoy.value) return false
        val parsed = Uri.parse(uri)
        if (parsed.scheme != "https" || parsed.host != "mail.google.com") return false
        return runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, parsed).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }

    companion object {
        internal fun plainText(part: JsonObject, depth: Int = 0): String {
            if (depth > 8) return ""
            val mime = part.text("mimeType").orEmpty()
            if (mime == "text/plain" && part.text("filename").isNullOrBlank()) {
                val encoded = part["body"]?.jsonObject?.text("data") ?: return ""
                if (encoded.length > 100_000) return ""
                return runCatching { String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8).take(16000) }.getOrDefault("")
            }
            return part.array("parts").take(20).joinToString("\n") { plainText(it.jsonObject, depth + 1) }.take(16000)
        }
        private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull
        private fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())
    }
}

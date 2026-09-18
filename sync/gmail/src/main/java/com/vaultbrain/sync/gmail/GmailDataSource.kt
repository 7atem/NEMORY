package com.vaultbrain.sync.gmail

/** OAuth/API boundary. Implementations must request only Gmail's read-only scope. */
interface GmailDataSource {
    suspend fun isConfigured(): Boolean
    suspend fun authorizedAccounts(): List<String>
    suspend fun authorize(accountId: String?): Result<String>
    suspend fun revoke(accountId: String): Result<Unit>
    suspend fun messages(accountId: String, newerThan: Long, limit: Int): List<GmailMessageRow>
    suspend fun page(accountId: String, newerThan: Long, limit: Int, cursor: String?): GmailSyncPage =
        GmailSyncPage(messages(accountId, newerThan, limit))
    fun open(uri: String): Boolean
}

data class GmailSyncPage(val messages: List<GmailMessageRow>, val deletedIds: Set<String> = emptySet(), val cursor: String? = null)

data class GmailMessageRow(
    val id: String,
    val threadId: String,
    val subject: String,
    val sender: String?,
    /** Sanitized plain text only. HTML, scripts and remote images must not cross this boundary. */
    val plainText: String,
    val receivedAt: Long,
    val labels: Set<String> = emptySet()
)

/** Safe default used until a verified OAuth client is supplied by the app owner. */
class UnconfiguredGmailDataSource : GmailDataSource {
    override suspend fun isConfigured() = false
    override suspend fun authorizedAccounts() = emptyList<String>()
    override suspend fun authorize(accountId: String?) =
        Result.failure<String>(IllegalStateException("Gmail OAuth client is not configured"))
    override suspend fun revoke(accountId: String) = Result.success(Unit)
    override suspend fun messages(accountId: String, newerThan: Long, limit: Int) = emptyList<GmailMessageRow>()
    override fun open(uri: String) = false
}

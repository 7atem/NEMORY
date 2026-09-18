package com.vaultbrain.sync.gmail

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RealGmailDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : GmailDataSource {

    private val scopes = listOf("https://www.googleapis.com/auth/gmail.readonly")

    override suspend fun isConfigured(): Boolean = true

    override suspend fun authorizedAccounts(): List<String> {
        // Validation managed by repository state. We assume if they're in connections, they are authorized until proven otherwise.
        return emptyList() 
    }

    override suspend fun authorize(accountId: String?): Result<String> {
        if (accountId == null) return Result.failure(IllegalArgumentException("Account ID required"))
        return try {
            val credential = getCredential(accountId)
            // Trigger a lightweight token fetch to validate
            withContext(Dispatchers.IO) {
                credential.token
            }
            Result.success(accountId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun revoke(accountId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val credential = getCredential(accountId)
            // GoogleAccountCredential doesn't easily expose explicit revocation without GoogleSignInClient,
            // but we can clear the token.
            credential.selectedAccountName = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun messages(accountId: String, newerThan: Long, limit: Int): List<GmailMessageRow> = withContext(Dispatchers.IO) {
        val service = getGmailService(accountId)
        // Convert newerThan to seconds
        val seconds = newerThan / 1000L
        val query = "newer_than:${seconds}s"
        
        val response = service.users().messages().list("me")
            .setQ(query)
            .setMaxResults(limit.toLong())
            .execute()

        val messages = response.messages ?: return@withContext emptyList()
        
        messages.mapNotNull { messageRef ->
            try {
                val msg = service.users().messages().get("me", messageRef.id)
                    .setFormat("full")
                    .execute()
                
                val subject = msg.payload?.headers?.find { it.name.equals("Subject", ignoreCase = true) }?.value ?: ""
                val sender = msg.payload?.headers?.find { it.name.equals("From", ignoreCase = true) }?.value
                
                // Extract plain text. We prioritize text/plain or try to parse text/html.
                val parts = msg.payload?.parts
                var plainText = msg.snippet ?: ""
                if (parts != null) {
                    val textPart = parts.find { it.mimeType == "text/plain" }
                    if (textPart?.body?.data != null) {
                        plainText = String(android.util.Base64.decode(textPart.body.data, android.util.Base64.URL_SAFE))
                    } else {
                        val htmlPart = parts.find { it.mimeType == "text/html" }
                        if (htmlPart?.body?.data != null) {
                            val html = String(android.util.Base64.decode(htmlPart.body.data, android.util.Base64.URL_SAFE))
                            plainText = GmailTextSanitizer.sanitize(html)
                        }
                    }
                }

                GmailMessageRow(
                    id = msg.id,
                    threadId = msg.threadId,
                    subject = subject,
                    sender = sender,
                    plainText = plainText,
                    receivedAt = msg.internalDate,
                    labels = msg.labelIds?.toSet() ?: emptySet()
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun open(uri: String): Boolean {
        // Managed by intent factory in UI logic instead of here if needed
        return true
    }
    
    private fun getCredential(accountId: String): GoogleAccountCredential {
        val credential = GoogleAccountCredential.usingOAuth2(context, scopes)
        credential.selectedAccountName = accountId
        return credential
    }

    private fun getGmailService(accountId: String): Gmail {
        return Gmail.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), getCredential(accountId))
            .setApplicationName("Nemory")
            .build()
    }
}

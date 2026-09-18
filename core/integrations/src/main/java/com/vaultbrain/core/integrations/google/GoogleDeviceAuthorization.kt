package com.vaultbrain.core.integrations.google

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.vaultbrain.core.common.model.external.ConnectionState
import com.vaultbrain.core.common.model.external.ConnectorCapability
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.integrations.model.ConnectionRecord
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class GoogleService(val connectorId: String, val scope: String) {
    GMAIL("gmail", "https://www.googleapis.com/auth/gmail.readonly"),
    TASKS("google_tasks", "https://www.googleapis.com/auth/tasks.readonly")
}

sealed interface GoogleAuthorizationStep {
    data class Consent(val intent: PendingIntent) : GoogleAuthorizationStep
    data class Connected(val accountId: String) : GoogleAuthorizationStep
}

class GoogleConsentRequired : Exception("Google authorization required")

/** Serialize sync and disconnect so an in-flight page cannot resurrect disconnected records. */
object GoogleSyncGate { val mutex = Mutex() }

/** Google Play services owns token caching. Nemory never persists access/refresh tokens. */
@Singleton
class GoogleDeviceAuthorization @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: ExternalContextRepository
) {
    private val client = Identity.getAuthorizationClient(context)

    suspend fun begin(service: GoogleService, accountId: String? = null): GoogleAuthorizationStep {
        check(!DecoySessionState.isDecoy.value)
        val result = client.authorize(request(service, accountId)).awaitResult()
        return if (result.hasResolution()) GoogleAuthorizationStep.Consent(requireNotNull(result.pendingIntent))
            else complete(service, result, accountId)
    }

    suspend fun finish(service: GoogleService, intent: Intent?, expectedAccount: String? = null): GoogleAuthorizationStep.Connected =
        complete(service, client.getAuthorizationResultFromIntent(intent), expectedAccount)

    private suspend fun complete(service: GoogleService, result: AuthorizationResult, expectedAccount: String?): GoogleAuthorizationStep.Connected {
        check(!DecoySessionState.isDecoy.value)
        if (!result.grantedScopes.contains(service.scope)) throw GoogleConsentRequired()
        val token = result.accessToken ?: throw GoogleConsentRequired()
        val profile = GoogleHttp.request("https://www.googleapis.com/oauth2/v3/userinfo", token)
        val email = profile["email"]?.jsonPrimitive?.content?.takeIf { it.contains('@') } ?: throw GoogleConsentRequired()
        require(expectedAccount == null || expectedAccount.equals(email, true))
        check(!DecoySessionState.isDecoy.value)
        val prior = repository.getConnection(service.connectorId, email)
        repository.upsertConnection((prior ?: ConnectionRecord(service.connectorId, email)).copy(
            state = ConnectionState.CONNECTED, accountName = email, lastError = null,
            capabilities = setOf(ConnectorCapability.READ, ConnectorCapability.SEARCH,
                ConnectorCapability.OPEN_ORIGINAL, ConnectorCapability.BACKGROUND_SYNC),
            updatedAt = System.currentTimeMillis()
        ))
        return GoogleAuthorizationStep.Connected(email)
    }

    suspend fun token(service: GoogleService, accountId: String): String {
        check(!DecoySessionState.isDecoy.value)
        val connection = repository.getConnection(service.connectorId, accountId)
        if (connection?.state != ConnectionState.CONNECTED) throw GoogleConsentRequired()
        val result = client.authorize(request(service, accountId)).awaitResult()
        if (result.hasResolution() || !result.grantedScopes.contains(service.scope) || result.accessToken.isNullOrBlank()) {
            revokeLocal(service, accountId)
            throw GoogleConsentRequired()
        }
        check(!DecoySessionState.isDecoy.value)
        return requireNotNull(result.accessToken)
    }

    suspend fun clearToken(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).awaitResult()
    }

    suspend fun revokeLocal(service: GoogleService, accountId: String) {
        repository.updateConnectionState(service.connectorId, accountId, ConnectionState.PERMISSION_REVOKED)
        repository.deleteRecordsForConnection(service.connectorId, accountId)
    }

    /** Disconnecting a Google account clears both linked services even when offline. */
    suspend fun disconnect(accountId: String) = GoogleSyncGate.mutex.withLock {
        check(!DecoySessionState.isDecoy.value)
        GoogleService.entries.forEach { service ->
            repository.deleteRecordsForConnection(service.connectorId, accountId)
            repository.deleteConnection(service.connectorId, accountId)
        }
        try {
            client.revokeAccess(RevokeAccessRequest.builder().setAccount(Account(accountId, "com.google"))
                .setScopes(GoogleService.entries.map { Scope(it.scope) } + Scope(EMAIL_SCOPE)).build()).awaitResult()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Local disconnect takes effect even offline; Google settings can revoke the grant. */ }
    }

    private fun request(service: GoogleService, accountId: String?) = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(service.scope), Scope(EMAIL_SCOPE)))
        .apply { accountId?.let { setAccount(Account(it, "com.google")) } }.build()

    companion object { private const val EMAIL_SCOPE = "https://www.googleapis.com/auth/userinfo.email" }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

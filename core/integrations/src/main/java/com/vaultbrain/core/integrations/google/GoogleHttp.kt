package com.vaultbrain.core.integrations.google

import com.vaultbrain.core.common.security.DecoySessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import javax.inject.Inject

class GoogleApiException(val status: Int) : Exception("Google API request failed ($status)")

/** Fixed Google hosts only; redirects are disabled so bearer tokens cannot escape. */
internal object GoogleHttp {
    suspend fun request(url: String, token: String): JsonObject = withContext(Dispatchers.IO) {
        check(!DecoySessionState.isDecoy.value)
        val endpoint = URL(url)
        require(endpoint.protocol == "https" && endpoint.host in setOf("www.googleapis.com", "gmail.googleapis.com", "tasks.googleapis.com"))
        val connection = endpoint.openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) throw GoogleApiException(status)
            val bytes = connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 2_000_000)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            require(bytes.size <= 2_000_000)
            currentCoroutineContext().ensureActive()
            check(!DecoySessionState.isDecoy.value)
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        } finally { connection.disconnect() }
    }
}

class GoogleDeviceApi @Inject constructor(private val authorization: GoogleDeviceAuthorization) {
    suspend fun get(service: GoogleService, accountId: String, path: String, query: Map<String, String> = emptyMap()): JsonObject {
        require(path.startsWith('/') && !path.contains(".."))
        val base = when (service) {
            GoogleService.GMAIL -> "https://gmail.googleapis.com/gmail/v1/users/me"
            GoogleService.TASKS -> "https://tasks.googleapis.com/tasks/v1"
        }
        val url = base + path + if (query.isEmpty()) "" else query.entries.joinToString("&", "?") { "${encode(it.key)}=${encode(it.value)}" }
        var token = authorization.token(service, accountId)
        return try { GoogleHttp.request(url, token) }
        catch (failure: GoogleApiException) {
            if (failure.status != 401) throw failure
            authorization.clearToken(token)
            token = authorization.token(service, accountId)
            try { GoogleHttp.request(url, token) }
            catch (second: GoogleApiException) {
                if (second.status == 401) authorization.revokeLocal(service, accountId)
                throw second
            }
        }
    }

    companion object { fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20") }
}

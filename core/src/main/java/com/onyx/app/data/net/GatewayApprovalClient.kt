package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Répond à une demande d'approbation d'un run Hermes
 * (`POST /v1/runs/{run_id}/approval`).
 *
 * Miroir de `HermesSessionsTransport.respondApproval` côté référence
 * (Echo/Services/Transport/HermesSessionsTransport:489) : le corps porte le `choice`
 * (`once` | `session` | `always` | `deny`) et, quand on l'a, le `request_id` d'origine.
 */
class GatewayApprovalClient(
    private val config: ServerConfig,
    private val httpClient: OkHttpClient = OnyxHttp.clientFor(config),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Transmet la décision. Lève une [TransportError] si le gateway refuse. */
    suspend fun respond(runId: String, requestId: String?, choice: String) = withContext(Dispatchers.IO) {
        if (!config.isUsable) throw TransportError.BadUrl(OnyxLocalization["empty base URL"])

        val payload = ApprovalBody(
            choice = choice,
            requestId = requestId?.takeIf { it.isNotEmpty() },
        )
        val body = json.encodeToString(ApprovalBody.serializer(), payload).toRequestBody(JSON)

        val request = Request.Builder()
            .url(config.apiBaseUrl + "/v1/runs/$runId/approval")
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .apply {
                val key = config.effectiveApiKey.trim()
                if (key.isNotEmpty()) header("Authorization", "Bearer $key")
            }
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = runCatching { response.peekBody(2_000L).string() }.getOrDefault("")
                    throw TransportError.Http(response.code, detail)
                }
            }
        } catch (error: IOException) {
            throw TransportError.from(error, afterHeaders = false)
        }
    }

    @Serializable
    private data class ApprovalBody(
        val choice: String,
        @kotlinx.serialization.SerialName("request_id") val requestId: String? = null,
    )

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

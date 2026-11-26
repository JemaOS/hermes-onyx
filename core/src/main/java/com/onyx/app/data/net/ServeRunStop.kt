package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * « Arrêter » — l'ordre explicite, chez l'hôte.
 *
 * Depuis que le client annonce `X-Hermes-Keep-Running`, **partir n'est plus arrêter** : fermer
 * l'app, perdre le réseau ou tuer l'écoute laisse le tour se finir sur le serveur. Le bouton
 * d'arrêt, lui, doit couper le travail pour de bon — c'est `POST /v1/runs/{run_id}/stop`, sur
 * l'identifiant du run que le flux a annoncé ([TurnEvent.RunId]).
 *
 * Sans identifiant de run (flux jamais ouvert, transport sans run), il n'y a rien à arrêter côté
 * hôte : [stop] rend `false` et l'app se contente de couper son écoute, comme avant.
 */
class ServeRunStop(
    private val config: ServerConfig,
    private val httpClient: OkHttpClient = OnyxHttp.shortClient(),
) {

    /** Vrai si l'hôte a accepté l'ordre d'arrêt. */
    suspend fun stop(runId: String): Boolean = withContext(Dispatchers.IO) {
        val base = config.apiBaseUrl.trim().trimEnd('/')
        if (base.isEmpty() || runId.isBlank()) return@withContext false
        val url = (base + "/v1/runs/").toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegment(runId.trim())
            ?.addPathSegment("stop")
            ?.build()
            ?: return@withContext false
        val request = Request.Builder()
            .url(url)
            .post("{}".toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .apply {
                val key = config.effectiveApiKey.trim()
                if (key.isNotEmpty()) header("Authorization", "Bearer $key")
            }
            .build()
        try {
            httpClient.newCall(request).execute().use { response -> response.isSuccessful }
        } catch (error: IOException) {
            false
        }
    }
}

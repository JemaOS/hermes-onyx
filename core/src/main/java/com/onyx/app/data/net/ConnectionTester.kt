package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** La sonde de `ConnectionTester`, appliquée à la configuration qui vient d'être sauvée. */
object ConnectionTester {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun current(config: ServerConfig): ConnectionProbe = when (config.transport) {
        Transport.HERMES_SERVE -> dashboard(config)
        else -> ChatCompletionsClient(config).testConnection()
    }

    private suspend fun dashboard(config: ServerConfig): ConnectionProbe = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val base = config.serveBase.toHttpUrlOrNull()
            ?: return@withContext ConnectionProbe(
                false,
                "The Dashboard address isn't a valid URL.",
                0,
                emptyList(),
            )
        val request = Request.Builder()
            .url(base.newBuilder().addPathSegments("api/status").build())
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .build()
        val client = OnyxHttp.clientFor(config).newBuilder()
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val host = base.host
                if (response.code == 403 || response.code == 302) {
                    return@withContext ConnectionProbe(
                        false,
                        "$host is behind an access gate (HTTP ${response.code}). Add its headers under Settings → Connection details.",
                        elapsed(started),
                        emptyList(),
                    )
                }
                // Le corps est lu **avant** les verdicts : un Dashboard qui refuse dit *pourquoi*
                // dans son corps, et c'est cela qu'il faut montrer.
                val refusal = response.body?.string().orEmpty()
                if (response.code != 200) {
                    return@withContext ConnectionProbe(
                        false,
                        ProbeFailure.message(
                            address = base.host,
                            reason = "Hermes Dashboard answered HTTP ${response.code}.",
                            rawBody = refusal,
                        ),
                        elapsed(started),
                        emptyList(),
                    )
                }
                val body = refusal
                val status = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                val version = (status?.get("version") as? JsonPrimitive)?.contentOrNull ?: "unknown version"
                val authRequired = (status?.get("auth_required") as? JsonPrimitive)?.booleanOrNull == true
                if (!authRequired) {
                    return@withContext ConnectionProbe(
                        true,
                        "Connected to Hermes Dashboard $version (no login required).",
                        elapsed(started),
                        emptyList(),
                    )
                }
                return@withContext try {
                    ServeClient(config).login(force = true)
                    ConnectionProbe(
                        true,
                        "Connected to Hermes Dashboard $version. Login accepted.",
                        elapsed(started),
                        emptyList(),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val why = (error as? TransportError)?.userMessage() ?: error.message.orEmpty()
                    ConnectionProbe(
                        false,
                        "Hermes Dashboard $version reachable, but login failed: $why",
                        elapsed(started),
                        emptyList(),
                    )
                }
            }
        } catch (error: IOException) {
            ConnectionProbe(
                false,
                "No Hermes Dashboard answered at ${base.host}.",
                elapsed(started),
                emptyList(),
            )
        }
    }

    private fun elapsed(started: Long): Long = System.currentTimeMillis() - started
}

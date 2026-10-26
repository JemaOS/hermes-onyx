package com.onyx.app.voice

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.net.OnyxHttp
import com.onyx.app.data.net.ServeClient
import com.onyx.app.data.net.TransportError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.Base64
import java.util.Locale

/** Une réponse audio prête à être jouée par SpeechAudioPlayer. */
class SpeechAudio(
    val bytes: ByteArray,
    val mimeType: String,
    val provider: String? = null,
) {
    /** Kokoro répond en PCM brut; un serveur qui renvoie tout de même un WAV est détecté au RIFF. */
    val isRawPcm: Boolean
        get() = mimeType.substringBefore(';').equals("audio/pcm", ignoreCase = true) &&
            !hasRiffHeader(bytes)
}

/** Normalise l'adresse du serveur Kokoro sans lui ajouter un `/v1` qu'il n'a pas. */
fun normalizedKokoroBase(raw: String): String? {
    var value = raw.trim()
    while (value.endsWith('/')) value = value.dropLast(1)
    if (value.isEmpty()) return null
    if (!value.contains("://")) value = "https://$value"
    return value
}

/** Client OpenAI-compatible du serveur Kokoro (`KokoroPlayer.voiceIDs` et `fetch` ). */
class KokoroSpeechClient(
    private val voicesClient: OkHttpClient = OnyxHttp.shortClient(6),
    private val speechClient: OkHttpClient = OnyxHttp.shortClient(60),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun voiceIds(baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        val base = normalizedKokoroBase(baseUrl)
            ?: throw TransportError.BadUrl("Set the server TTS address first.")
        val request = Request.Builder()
            .url(endpoint(base, "v1/audio/voices"))
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .build()
        try {
            voicesClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw TransportError.Http(response.code, body.take(300))
                val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse {
                    throw TransportError.Malformed("Kokoro sent an unreadable voice list")
                }
                val entries = root["voices"] as? JsonArray
                    ?: throw TransportError.Malformed("Kokoro sent no voices")
                entries.mapNotNull { entry ->
                    when (entry) {
                        is JsonObject -> (entry["id"] as? JsonPrimitive)?.contentOrNull
                        is JsonPrimitive -> entry.contentOrNull
                        else -> null
                    }
                }.sorted()
            }
        } catch (error: IOException) {
            throw TransportError.from(error, afterHeaders = false)
        }
    }

    /** Demande le PCM 24 kHz mono du modèle; la lecture sait aussi reconnaître un WAV. */
    suspend fun speak(
        text: String,
        baseUrl: String,
        voice: String,
        speed: Float = 1.0f,
    ): SpeechAudio = withContext(Dispatchers.IO) {
        val base = normalizedKokoroBase(baseUrl)
            ?: throw TransportError.BadUrl("Set the server TTS address first.")
        val payload = buildJsonObject {
            put("model", "kokoro")
            put("input", text)
            put("voice", voice)
            put("speed", speed.coerceIn(0.5f, 2.0f).toDouble())
            put("response_format", "pcm")
            put("stream", true)
        }
        val request = Request.Builder()
            .url(endpoint(base, "v1/audio/speech"))
            .post(payload.toString().toRequestBody(JSON))
            .header("Accept", "audio/pcm, audio/wav, application/octet-stream")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .build()
        try {
            speechClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = runCatching { response.peekBody(2_000L).string() }.getOrDefault("")
                    throw TransportError.Http(response.code, detail.take(300))
                }
                val bytes = response.body?.bytes()
                    ?: throw TransportError.Malformed("Kokoro sent no audio")
                if (bytes.isEmpty()) throw TransportError.Malformed("Kokoro sent no audio")
                val contentType = response.header("Content-Type").orEmpty()
                val mime = if (contentType.contains("wav", ignoreCase = true) || hasRiffHeader(bytes)) {
                    "audio/wav"
                } else {
                    "audio/pcm"
                }
                SpeechAudio(bytes, mime)
            }
        } catch (error: IOException) {
            throw TransportError.from(error, afterHeaders = true)
        }
    }

    private fun endpoint(base: String, path: String): okhttp3.HttpUrl {
        val parsed = base.toHttpUrlOrNull() ?: throw TransportError.BadUrl(base)
        return parsed.newBuilder().addPathSegments(path).build()
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** La voix configurée sur le Hermes de l'utilisateur, par la session du Dashboard. */
class HermesSpeechClient(
    private val server: ServerConfig,
    private val client: ServeClient = ServeClient(server),
) {
    val isAvailable: Boolean get() = server.hasServeCredentials

    /** La réponse entière porte `data_url` et le provider: c'est ce que le test de réglages affiche. */
    suspend fun speak(text: String): SpeechAudio {
        val answer = client.request(
            path = "api/audio/speak",
            method = "POST",
            query = server.effectiveProfile?.let { mapOf("profile" to it) }.orEmpty(),
            body = buildJsonObject { put("text", text) },
            timeoutSeconds = 60,
        ) as? JsonObject ?: throw TransportError.Malformed("Hermes sent no audio")
        val dataUrl = (answer["data_url"] as? JsonPrimitive)?.contentOrNull
            ?: throw TransportError.Malformed("Hermes sent no audio")
        val declaredMime = (answer["mime_type"] as? JsonPrimitive)?.contentOrNull
        val provider = (answer["provider"] as? JsonPrimitive)?.contentOrNull
        return decodeSpeechDataUrl(dataUrl, declaredMime, provider)
            ?: throw TransportError.Malformed("Hermes sent no audio")
    }
}

/** Décode `data:audio/mpeg;base64,…` sans faire transiter de clé de provider par le téléphone. */
fun decodeSpeechDataUrl(url: String, declaredMime: String? = null, provider: String? = null): SpeechAudio? {
    if (!url.startsWith("data:")) return null
    val comma = url.indexOf(',')
    if (comma < 0) return null
    val header = url.substring(5, comma)
    if (!header.endsWith(";base64", ignoreCase = true)) return null
    val encoded = url.substring(comma + 1)
    val bytes = try {
        Base64.getDecoder().decode(encoded)
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (bytes.isEmpty()) return null
    val mime = declaredMime?.takeIf { it.isNotBlank() }
        ?: header.dropLast(";base64".length).ifBlank { "application/octet-stream" }
    return SpeechAudio(bytes, mime, provider)
}

/** Le nom donné au provider dans HermesVoiceRows, mot pour mot du modèle. */
fun hermesProviderName(id: String): String = when (id.lowercase(Locale.ROOT)) {
    "" -> "your Hermes"
    "edge" -> "Edge, Microsoft's online voice"
    "elevenlabs" -> "ElevenLabs"
    "openai" -> "OpenAI, or a server like it"
    "gemini" -> "Google Gemini"
    "xai" -> "xAI"
    "minimax" -> "MiniMax"
    "mistral" -> "Mistral"
    "deepinfra" -> "DeepInfra"
    "neutts" -> "NeuTTS, on your server"
    "kittentts" -> "KittenTTS, on your server"
    "piper" -> "Piper, on your server"
    else -> id
}

internal fun hasRiffHeader(bytes: ByteArray): Boolean = bytes.size >= 12 &&
    bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
    bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
    bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte() &&
    bytes[10] == 'V'.code.toByte() && bytes[11] == 'E'.code.toByte()

package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.localization.OnyxLocalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Le client du Dashboard Hermes (`hermes serve`).
 *
 * Miroir de `HermesServeClient` côté référence
 * (Echo/Services/Transport/HermesServeClient) : la même connexion (un cookie de session
 * obtenu par `auth/password-login`), les mêmes routes (`api/…`), et la même façon de lire des
 * formes JSON qui varient d'une version du serveur à l'autre.
 *
 * C'est lui qui ouvre les écrans que seul le Dashboard connaît : Gateway, Fichiers, Contexte,
 * Cron, Kanban.
 */
class ServeClient(
    private val config: ServerConfig,
    private val httpClient: OkHttpClient = clientFor(config),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    /** Le profil Hermes visé, quand il y en a un autre que celui par défaut. */
    val profile: String? get() = config.effectiveProfile

    /** Faux tant que le Dashboard n'a pas d'adresse et d'identifiants. */
    val isConfigured: Boolean get() = config.hasServeCredentials

    private var loggedInAt: Long = 0

    /** L'adresse de base, ou une erreur claire plutôt qu'une URL vide. */
    private fun base(): HttpUrl =
        config.serveBase.toHttpUrlOrNull() ?: throw TransportError.BadUrl(OnyxLocalization["empty Dashboard address"])

    /**
     * Ouvre la session si besoin.
     *
     * Une sonde bon marché (`api/sessions?limit=1`) vérifie qu'un cookie d'une session précédente
     * vit encore, comme sur la référence ; sinon on s'authentifie avec `auth/password-login`.
     */
    suspend fun login(force: Boolean = false) = withContext(Dispatchers.IO) {
        val base = base()
        if (!config.hasServeCredentials) throw TransportError.SignInRequired

        // « Une clé » : le serveur accepte la clé d'installation en `Bearer` sur `/api/*` et
        // entretient lui-même la session du Dashboard. Aucun identifiant à envoyer, aucun mot de
        // passe à demander : la clé (posée par l'intercepteur de [clientFor]) suffit, et il n'y a
        // aucune sonde à faire — il n'y a pas de cookie de session à valider ici.
        if (config.servesWithKey) {
            loggedInAt = System.currentTimeMillis()
            return@withContext
        }

        if (!force && loggedInAt > 0 && System.currentTimeMillis() - loggedInAt < SESSION_TTL_MS) return@withContext

        val probe = base.newBuilder().addPathSegments("api/sessions").addQueryParameter("limit", "1").build()
        if (probeCall(probe)) {
            loggedInAt = System.currentTimeMillis()
            return@withContext
        }

        val body = buildJsonObject {
            put("provider", "basic")
            put("username", config.serveUsername)
            put("password", config.servePassword)
        }
        val request = Request.Builder()
            .url(base.newBuilder().addPathSegments(AUTH_PATH).build())
            .post(json.encodeToString(JsonElement.serializer(), body).toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Le corps est gardé tel quel : c'est lui qui dit *pourquoi* (« Invalid
                    // credentials », « no_cookie », « session_expired »), et c'est de lui que
                    // `TransportError.userMessage` tire un message clair. Un mot de passe refusé
                    // ne doit jamais être remplacé par un « login rejected » anonyme.
                    val detail = runCatching { response.peekBody(2_000L).string() }.getOrDefault("")
                    throw TransportError.Http(response.code, detail)
                }
            }
        } catch (error: IOException) {
            throw TransportError.from(error, afterHeaders = false)
        }
        loggedInAt = System.currentTimeMillis()
    }

    private fun probeCall(url: HttpUrl): Boolean {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .build()
        return runCatching { httpClient.newCall(request).execute().use { it.code == 200 } }.getOrDefault(false)
    }

    /**
     * Un aller-retour JSON. `timeoutSeconds` sert aux routes lentes (test d'un serveur MCP,
     * déclenchement d'une tâche) que le délai ordinaire couperait.
     *
     * Un cookie mort est rattrapé **une fois** : sans cette reprise, un écran ouvert après
     * l'expiration de la session — ou après un redémarrage du dashboard — restait bloqué sur
     * « Non autorisé (401). Vérifiez la clé API. » alors que les identifiants étaient bons.
     */
    suspend fun request(
        path: String,
        method: String = "GET",
        query: Map<String, String> = emptyMap(),
        body: JsonElement? = null,
        timeoutSeconds: Long? = null,
    ): JsonElement = withContext(Dispatchers.IO) {
        repeat(2) { attempt ->
            login(force = attempt > 0)
            try {
                return@withContext send(path, method, query, body, timeoutSeconds)
            } catch (error: TransportError.Http) {
                if (error.status != 401 || attempt > 0) throw error
                loggedInAt = 0
            }
        }
        error(OnyxLocalization["Dashboard session rejected twice in a row"])
    }

    /**
     * Un ticket à usage unique pour ouvrir un socket du Dashboard (`POST api/auth/ws-ticket`).
     *
     * Les sockets du Dashboard refusent un cookie seul : `api/ws` comme le socket d'événements
     * d'un plugin demandent ce ticket (`?ticket=…`), exactement comme
     * `HermesServeClient.authenticate()` côté référence. La session est ouverte au passage.
     */
    suspend fun socketTicket(): String {
        val payload = request(path = "api/auth/ws-ticket", method = "POST", body = emptyBody()).asObject()
        return payload?.firstString("ticket")
            ?: throw TransportError.Malformed(OnyxLocalization["unreadable ticket"])
    }

    /**
     * Un aller-retour JSON sans corps (`DELETE`).
     *
     * Les routes de suppression du Dashboard refusent un corps vide `{}` là où elles attendent
     * une requête sans corps (Hermes n'a pas d'appel socket pour supprimer un profil : c'est une
     * route REST, `DELETE api/profiles/{name}`, et `HermesServeClient.deleteProfile` de la référence part
     * sans corps). Même reprise qu'ailleurs sur un cookie mort.
     */
    suspend fun delete(path: String, timeoutSeconds: Long? = null): JsonElement = withContext(Dispatchers.IO) {
        repeat(2) { attempt ->
            login(force = attempt > 0)
            try {
                return@withContext send(path, "DELETE", emptyMap(), null, timeoutSeconds, sendBody = false)
            } catch (error: TransportError.Http) {
                if (error.status != 401 || attempt > 0) throw error
                loggedInAt = 0
            }
        }
        error(OnyxLocalization["Dashboard session rejected twice in a row"])
    }

    /** L'envoi brut ; la session est supposée ouverte (voir [request]). */
    private fun send(
        path: String,
        method: String,
        query: Map<String, String>,
        body: JsonElement?,
        timeoutSeconds: Long?,
        sendBody: Boolean = true,
    ): JsonElement {
        val base = base()
        val builder = base.newBuilder().addPathSegments(path.trimStart('/'))
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val url = builder.build()

        val requestBody: RequestBody? = if (!sendBody) null else when (method) {
            "GET", "HEAD" -> null
            else -> (body?.let { json.encodeToString(JsonElement.serializer(), it) } ?: "{}")
                .toRequestBody(JSON)
        }

        val request = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .header("Accept", "application/json")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .apply { if (requestBody != null) header("Content-Type", "application/json") }
            .build()

        val client = if (timeoutSeconds != null) {
            httpClient.newBuilder()
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build()
        } else {
            httpClient
        }

        return try {
            client.newCall(request).execute().use { response ->
                val text = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                if (!response.isSuccessful) {
                    throw TransportError.Http(response.code, text.take(300))
                }
                if (text.isBlank()) {
                    kotlinx.serialization.json.JsonNull
                } else {
                    runCatching { json.parseToJsonElement(text) }
                        .getOrElse { throw TransportError.Malformed(OnyxLocalization.format("unreadable response: %1\$s", text.take(200))) }
                }
            }
        } catch (error: IOException) {
            throw TransportError.from(error, afterHeaders = true)
        }
    }

    /** Le corps brut d'un téléchargement (une image, un fichier). */
    suspend fun bytes(path: String, query: Map<String, String> = emptyMap()): ByteArray = withContext(Dispatchers.IO) {
        login()
        val builder = base().newBuilder().addPathSegments(path.trimStart('/'))
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val request = Request.Builder()
            .url(builder.build())
            .get()
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw TransportError.Http(response.code, "")
                response.body?.bytes() ?: throw TransportError.Malformed(OnyxLocalization["empty body"])
            }
        } catch (error: IOException) {
            throw TransportError.from(error, afterHeaders = true)
        }
    }

    // MARK: - Textes du serveur

    /** Une chaîne plate (les routes qui ne rendent pas de JSON : un texte, un markdown). */
    suspend fun text(path: String, query: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        login()
        val builder = base().newBuilder().addPathSegments(path.trimStart('/'))
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val request = Request.Builder()
            .url(builder.build())
            .get()
            .header("Accept", "text/plain, application/json, */*")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw TransportError.Http(response.code, "")
                response.body?.string().orEmpty()
            }
        } catch (error: IOException) {
            throw TransportError.from(error, afterHeaders = true)
        }
    }

    /** Envoie un texte brut (l'édition d'un fichier du serveur). */
    suspend fun putText(path: String, text: String, query: Map<String, String> = emptyMap()) {
        withContext(Dispatchers.IO) {
            login()
            val builder = base().newBuilder().addPathSegments(path.trimStart('/'))
            query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
            val request = Request.Builder()
                .url(builder.build())
                .post(text.toRequestBody("text/plain; charset=utf-8".toMediaType()))
                .header("User-Agent", OnyxHttp.USER_AGENT)
                .build()
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val detail = runCatching { response.peekBody(1_000L).string() }.getOrDefault("")
                        throw TransportError.Http(response.code, detail)
                    }
                }
            } catch (error: IOException) {
                throw TransportError.from(error, afterHeaders = true)
            }
        }
    }

    /** Un `PUT` JSON, pour les routes qui n'acceptent pas de texte. */
    suspend fun putJson(path: String, body: JsonObject) {
        request(path = path, method = "PUT", body = body)
    }

    /**
     * Un envoi multipart (le téléversement d'un fichier) : la route attend un formulaire avec la
     * destination, l'autorisation d'écraser, et le fichier lui-même.
     */
    suspend fun multipart(
        path: String,
        fields: Map<String, String>,
        fileName: String,
        bytes: ByteArray,
        contentType: String = "application/octet-stream",
    ): JsonElement = withContext(Dispatchers.IO) {
        login()
        val builder = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
        fields.forEach { (key, value) -> builder.addFormDataPart(key, value) }
        builder.addFormDataPart(
            "file",
            fileName,
            bytes.toRequestBody(contentType.toMediaType()),
        )
        val request = Request.Builder()
            .url(base().newBuilder().addPathSegments(path.trimStart('/')).build())
            .post(builder.build())
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val text = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                if (!response.isSuccessful) throw TransportError.Http(response.code, text.take(300))
                if (text.isBlank()) return@use kotlinx.serialization.json.JsonNull
                runCatching { json.parseToJsonElement(text) }.getOrDefault(kotlinx.serialization.json.JsonNull)
            }
        } catch (error: IOException) {
            throw TransportError.from(error, afterHeaders = true)
        }
    }

    /** Un objet JSON vide, pratique pour les routes sans corps. */
    fun emptyBody(): JsonObject = buildJsonObject { }

    /** Un tableau JSON de chaînes. */
    fun stringArray(values: List<String>): JsonElement = buildJsonArray { values.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }

    companion object {
        /** La route qui ouvre une session. */
        const val AUTH_PATH = "auth/password-login"

        /** Le temps pendant lequel on croit la session vivante avant de la reprober. */
        private const val SESSION_TTL_MS = 5 * 60 * 1000L

        private val JSON = "application/json; charset=utf-8".toMediaType()

        /**
         * Un client pour le Dashboard : le bocal à cookies est partagé par tout le processus, et
         * les délais laissent le temps aux routes lentes (test MCP, `trigger` d'une tâche).
         */
        fun clientFor(config: ServerConfig): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .cookieJar(ServeCookieJar)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
            // Le serveur « une clé » (le compagnon) accepte la clé d'installation en `Bearer` sur
            // tout `/api/*` : c'est son identité, il n'y a pas de session par mot de passe à ouvrir.
            if (config.servesWithKey) builder.addInterceptor(ServeKeyInterceptor(config.effectiveApiKey))
            return builder.build()
        }
    }
}

/**
 * Pose `Authorization: Bearer <clé du serveur>` sur les appels du Dashboard quand la clé fait
 * l'identité — le cas du service compagnon « une clé ». Un en-tête déjà posé n'est pas écrasé.
 */
private class ServeKeyInterceptor(private val key: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Authorization") != null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer $key").build())
    }
}

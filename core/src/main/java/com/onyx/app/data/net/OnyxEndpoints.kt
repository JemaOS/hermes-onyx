package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Ce qu'un serveur Onyx annonce de lui-même, à `GET /api/onyx/endpoints`.
 *
 * Le mode « Simple » n'a besoin que d'une adresse et d'une clé : ce manifeste porte tout le reste —
 * le modèle, l'adresse des voix, l'adresse et le jeton du pont « Ordinateur », la langue. Il est lu
 * **après** la clé, jamais sans : l'en-tête `Authorization: Bearer <clé>` l'accompagne.
 *
 * Un serveur plus ancien répond **404** : le manifeste vaut alors `null`, et l'app revient
 * exactement au comportement d'avant (adresse + clé, le reste à la main). Rien ne casse.
 */
data class OnyxEndpoints(
    val version: Int = 0,
    val server: String = "",
    val model: String = "",
    val voices: String = "",
    val bridge: String = "",
    val bridgeToken: String = "",
    val language: String = "",
    val capabilities: List<String> = emptyList(),
) {
    /** Vrai dès qu'il apporte quelque chose : un manifeste vide ne remplit rien. */
    val isUsable: Boolean
        get() = model.isNotEmpty() || voices.isNotEmpty() || bridge.isNotEmpty() ||
            bridgeToken.isNotEmpty() || language.isNotEmpty()

    companion object {
        /** Le chemin du contrat, relatif à l'adresse du serveur. */
        const val PATH = "api/onyx/endpoints"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * Lecture **pure** du manifeste : aucune I/O, donc éprouvable sans réseau.
         *
         * `null` quand ce n'est pas du JSON lisible ; une chaîne absente devient une chaîne vide,
         * jamais une panne — un manifeste partiel reste utile.
         */
        fun parse(text: String): OnyxEndpoints? {
            val root = runCatching { json.parseToJsonElement(text.trim()).jsonObject }.getOrNull()
                ?: return null
            return OnyxEndpoints(
                version = (root.scalar("version"))?.toIntOrNull() ?: 0,
                server = root.scalar("server").orEmpty(),
                model = root.scalar("model").orEmpty(),
                voices = addressOrEmpty(root.scalar("voices")),
                bridge = addressOrEmpty(root.scalar("bridge")),
                bridgeToken = root.scalar("bridgeToken").orEmpty().take(200),
                language = root.scalar("language").orEmpty().take(20),
                capabilities = (root["capabilities"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .orEmpty(),
            )
        }

        private fun JsonObject.scalar(name: String): String? =
            (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

        /** Seules les adresses http(s) sont gardées : une valeur douteuse ne vaut pas mieux que rien. */
        private fun addressOrEmpty(raw: String?): String {
            if (raw.isNullOrEmpty()) return ""
            val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return ""
            val scheme = uri.scheme?.lowercase() ?: return ""
            if (scheme != "http" && scheme != "https") return ""
            if (uri.host.isNullOrEmpty() || uri.userInfo != null) return ""
            return uri.toString()
        }
    }
}

/**
 * Ce que la découverte ramène pour remplir l'écran « Simple ».
 *
 * [reachable] dit si le serveur a répondu — par le manifeste **ou** par `/v1/models` ; c'est ce qui
 * décide d'afficher « Connecté » plutôt qu'une erreur. Le reste peut être vide sans bloquer : le
 * formulaire n'aura simplement rien de plus à remplir.
 */
data class DiscoveredSetup(
    val reachable: Boolean = false,
    val model: String = "",
    val voices: String = "",
    val bridge: String = "",
    val bridgeToken: String = "",
    val language: String = "",
    val models: List<String> = emptyList(),
    /** Ce que le serveur annonce savoir porter (`bots`, `groups`, `voices`…). */
    val capabilities: List<String> = emptyList(),
) {
    /** Vrai quand le manifeste a apporté autre chose que le modèle. */
    val hasExtras: Boolean
        get() = voices.isNotEmpty() || bridge.isNotEmpty() || bridgeToken.isNotEmpty() ||
            language.isNotEmpty()

    /**
     * Vrai quand le serveur annonce lui-même porter les **bots** — donc le Dashboard (`/api/…`).
     *
     * C'est le signal du compagnon « une clé » : il sert le chat (`/v1/…`) et le Dashboard
     * (`/api/…`) **à la même adresse, sous la même clé**. Rien à lui demander de plus : le mode
     * « Simple » peut alors faire pointer le Dashboard sur l'adresse déjà saisie.
     */
    val declaresDashboard: Boolean
        get() = capabilities.any { it.equals("bots", ignoreCase = true) || it.equals("groups", ignoreCase = true) }

    companion object {
        /**
         * Assemble ce qu'on a lu : le manifeste fait foi, `/v1/models` prend le relais pour le
         * modèle quand le serveur n'a pas de manifeste. Pur, donc éprouvable.
         */
        fun merge(
            endpoints: OnyxEndpoints?,
            probe: ConnectionProbe?,
            probeReachable: Boolean,
        ): DiscoveredSetup = DiscoveredSetup(
            reachable = endpoints != null || probeReachable,
            model = endpoints?.model?.takeIf { it.isNotEmpty() }
                ?: probe?.models?.firstOrNull().orEmpty(),
            voices = endpoints?.voices.orEmpty(),
            bridge = endpoints?.bridge.orEmpty(),
            bridgeToken = endpoints?.bridgeToken.orEmpty(),
            language = endpoints?.language.orEmpty(),
            models = probe?.models.orEmpty(),
            capabilities = endpoints?.capabilities.orEmpty(),
        )
    }
}

/** Va chercher le manifeste d'un serveur, et retombe proprement sur `/v1/models`. */
object OnyxDiscovery {

    /**
     * `GET /api/onyx/endpoints` avec la clé d'installation.
     *
     * Rend `null` — jamais une exception — pour un 404 (serveur plus ancien), un réseau muet ou une
     * réponse illisible : l'appelant garde alors le comportement d'avant.
     */
    suspend fun endpoints(
        baseUrl: String,
        key: String,
        accessID: String = "",
        accessSecret: String = "",
        timeoutSeconds: Long = 8L,
    ): OnyxEndpoints? = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().toHttpUrlOrNull() ?: return@withContext null
        val config = ServerConfig(
            id = "onyx-discovery",
            baseUrl = baseUrl.trim(),
            apiKey = key,
            accessID = accessID,
            accessSecret = accessSecret,
            transport = Transport.CHAT_COMPLETIONS,
        )
        val request = Request.Builder()
            .url(base.newBuilder().addPathSegments(OnyxEndpoints.PATH).build())
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .apply { if (key.isNotEmpty()) header("Authorization", "Bearer $key") }
            .build()
        val client = OnyxHttp.clientFor(config).newBuilder()
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                OnyxEndpoints.parse(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    /**
     * Le geste complet du mode « Simple » : d'abord `/v1/models` (la preuve que ça répond), puis le
     * manifeste. Un 404 du manifeste ne bloque pas — l'app garde adresse + clé.
     */
    suspend fun discover(
        baseUrl: String,
        key: String,
        accessID: String = "",
        accessSecret: String = "",
    ): DiscoveredSetup = withContext(Dispatchers.IO) {
        val config = ServerConfig(
            id = "onyx-discovery",
            baseUrl = baseUrl.trim(),
            apiKey = key,
            accessID = accessID,
            accessSecret = accessSecret,
            transport = Transport.CHAT_COMPLETIONS,
        )
        val probe = runCatching { ChatCompletionsClient(config).testConnection() }.getOrNull()
        val endpoints = endpoints(baseUrl, key, accessID, accessSecret)
        DiscoveredSetup.merge(endpoints, probe, probe?.ok == true)
    }
}

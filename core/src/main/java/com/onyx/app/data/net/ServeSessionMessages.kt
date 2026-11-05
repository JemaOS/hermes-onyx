package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * La réponse d'un tour que l'app n'a pas écouté jusqu'au bout.
 *
 * **Le défaut corrigé** : fermer l'app pendant qu'un bot travaille tuait le tour. Depuis que le
 * client annonce `X-Hermes-Keep-Running` (`ChatCompletionsClient.KEEP_RUNNING_HEADER`), le serveur
 * **finit** le run sans personne à l'écoute et écrit la réponse dans la session
 * (`GET /api/sessions/{id}/messages`). Encore faut-il que l'app la relise : c'est ce que fait cette
 * classe — au retour sur la conversation, la bulle restée vide se remplit avec ce que l'hôte a
 * écrit pendant l'absence.
 *
 * Rien n'est inventé : sans message d'assistant après le dernier message de l'utilisateur, [reply]
 * rend `null` (le tour tourne encore, ou il n'a rien produit) et l'app ne touche à rien.
 */
class ServeSessionMessages(
    private val config: ServerConfig,
    private val httpClient: OkHttpClient = OnyxHttp.clientFor(config),
) {

    /**
     * Les messages de la session, du plus ancien au plus récent ; vide si le serveur ne les donne pas.
     *
     * [limit] borne la lecture aux **derniers** [limit] messages (`order=latest`, que le serveur
     * rend en ordre chronologique). Sans lui, la route répond sa page par défaut — 500 messages,
     * 1,17 Mo mesurés sur une conversation réelle, relus toutes les 5 s tant qu'un tour est en
     * cours : plusieurs dizaines de Mo par conversation sur un réseau mobile. Les chemins qui
     * n'ont besoin que de la **fin** du fil (le sondage d'une réponse) passent donc un [limit] ;
     * ceux qui peignent le fil entier (ouverture d'une conversation) n'en passent pas.
     *
     * [offset] remonte dans le fil, toujours avec [limit] : le serveur pagine **depuis la fin**
     * (`order=latest`), donc `offset=60&limit=60` rend les 60 messages juste avant les 60 derniers,
     * en ordre chronologique. C'est la mécanique de « messages précédents ».
     */
    suspend fun rows(
        sessionId: String,
        profile: String? = null,
        limit: Int? = null,
        offset: Int = 0,
    ): List<Row> = withContext(Dispatchers.IO) {
        val base = config.apiBaseUrl.trim().trimEnd('/')
        if (base.isEmpty() || sessionId.isBlank()) return@withContext emptyList()
        // Le profil de la **conversation** d'abord : une session rangée dans `profiles/coder`
        // n'existe pas dans le profil par défaut, et le serveur répondait
        // `404 session_not_found` à chaque sondage (journal du 2026-11-05, une fois par 5 s).
        // L'en-tête suffisait en théorie ; il est ici **dans l'URL** en plus, parce qu'un relais
        // peut le laisser tomber — la query, elle, traverse tout.
        val effective = profile?.trim()?.takeIf { it.isNotEmpty() } ?: config.effectiveProfile
        val url = (base + "/api/sessions/").toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegment(sessionId.trim())
            ?.addPathSegment("messages")
            ?.apply { if (effective != null) addQueryParameter("profile", effective) }
            ?.apply {
                if (limit != null && limit > 0) {
                    addQueryParameter("limit", limit.toString())
                    addQueryParameter("order", "latest")
                    if (offset > 0) addQueryParameter("offset", offset.toString())
                }
            }
            ?.build()
            ?: return@withContext emptyList()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .apply {
                val key = config.effectiveApiKey.trim()
                if (key.isNotEmpty()) header("Authorization", "Bearer $key")
            }
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string().orEmpty()
                parseRows(body)
            }
        } catch (error: IOException) {
            emptyList()
        }
    }

    /**
     * La dernière réponse de l'hôte pour cette session, ou `null` si le tour n'a rien écrit encore.
     *
     * Ne lit que la **fin** du fil ([REPLY_TAIL] lignes) : c'est tout ce que [trailingReply] peut
     * consommer, et c'est le seul appel répété — toutes les cinq secondes pendant qu'un tour se
     * tait, puis à chaque tentative de rattrapage. Lire le fil entier à chaque fois coûtait
     * 1,17 Mo par sondage sur une conversation réelle.
     */
    suspend fun reply(sessionId: String, profile: String? = null): String? =
        trailingReply(rows(sessionId, profile, limit = REPLY_TAIL))

    /** Un message tel que la session de l'hôte le tient : son rôle et son texte visible. */
    data class Row(val role: String, val text: String)

    companion object {

        /**
         * La fenêtre du sondage : les derniers messages qu'un tour d'agent peut occuper.
         *
         * Mesuré sur une vraie conversation (`20261105_195844_06443a`) : 40 lignes = 97 Ko, contre
         * 1,17 Mo pour la page de 500 que la route rend par défaut. Assez large pour qu'un tour
         * bavard (raisonnement, appels d'outil, réponse) tienne derrière son message d'utilisateur ;
         * une fenêtre trop courte ne fait **rien de faux** — `trailingReply` rend `null` et la bulle
         * ne bouge pas — elle rate seulement le rattrapage.
         */
        const val REPLY_TAIL = 40

        /**
         * La page du fil ouvert : 60 messages au lieu des 500 que la route rend par défaut.
         *
         * 500 messages = **1 166 980 octets** mesurés sur une conversation réelle, à chaque
         * ouverture, alors que l'écran n'affiche que la fin. 60 messages pèsent **101 à 135 Ko**
         * sur la même conversation (mesuré page par page), et « messages précédents » remonte de
         * page en page ([rows] avec `offset`).
         */
        const val THREAD_PAGE = 60

        /** La réponse à afficher : le texte du premier assistant non vide **après** le dernier mot de l'utilisateur. */
        fun trailingReply(rows: List<Row>): String? {
            for (row in rows.asReversed()) {
                when (row.role) {
                    // Le dernier message de l'utilisateur n'a pas encore de réponse : rien à reprendre.
                    "user" -> return null
                    "assistant" -> if (row.text.isNotBlank()) return row.text
                }
            }
            return null
        }

        /**
         * Ce qu'il faut garder de deux versions d'une même réponse : celle du flux et celle de
         * l'hôte. L'hôte gagne quand il **prolonge** ce qu'on a déjà lu (le flux s'est arrêté au
         * milieu) ; sinon la bulle ne bouge pas — une réponse divergente n'est pas une réponse plus
         * complète, et l'écraser effacerait ce que l'utilisateur a sous les yeux.
         */
        fun merge(streamed: String, fromHost: String?): String? {
            val host = fromHost?.takeIf { it.isNotBlank() } ?: return null
            val seen = streamed.trim()
            if (seen.isEmpty()) return host
            return when {
                host.trim().startsWith(seen) -> host
                seen.startsWith(host.trim()) -> null
                else -> null
            }
        }

        /** Lit `{"messages":[{role, content}]}` — un contenu en tableau de blocs est mis bout à bout. */
        fun parseRows(body: String): List<Row> {
            if (body.isBlank()) return emptyList()
            val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
            val array = when (root) {
                is JsonArray -> root
                is JsonObject -> (root["messages"] ?: root["data"])?.let { element ->
                    runCatching { element.jsonArray }.getOrNull()
                }
                else -> null
            } ?: return emptyList()
            return rowsOf(array)
        }

        /**
         * Les mêmes lignes, depuis un tableau **déjà parsé**.
         *
         * C'est ce que rend `session.resume` quand on demande l'historique : le rattachement après
         * une coupure a besoin de la réponse que l'hôte a terminée pendant l'absence.
         */
        fun rowsOf(array: JsonArray): List<Row> = array.mapNotNull { element ->
            val row = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val role = (row["role"] as? JsonPrimitive)?.content?.trim()?.lowercase()
                ?: return@mapNotNull null
            Row(role = role, text = textOf(row["content"]))
        }

        /** Le texte d'un contenu : une chaîne, ou des blocs dont on ne garde que les parties texte. */
        private fun textOf(content: Any?): String = when (content) {
            is JsonPrimitive -> content.content.takeIf { content.isString } ?: ""
            is JsonArray -> content.mapNotNull { block ->
                (block as? JsonObject)?.let { obj ->
                    (obj["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                }
            }.joinToString("")
            is JsonObject -> (content["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
            else -> ""
        }
    }
}

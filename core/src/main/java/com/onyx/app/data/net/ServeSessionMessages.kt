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

    /** Les messages de la session, du plus ancien au plus récent ; vide si le serveur ne les donne pas. */
    suspend fun rows(sessionId: String, profile: String? = null): List<Row> = withContext(Dispatchers.IO) {
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

    /** La dernière réponse de l'hôte pour cette session, ou `null` si le tour n'a rien écrit encore. */
    suspend fun reply(sessionId: String, profile: String? = null): String? =
        trailingReply(rows(sessionId, profile))

    /** Un message tel que la session de l'hôte le tient : son rôle et son texte visible. */
    data class Row(val role: String, val text: String)

    companion object {

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

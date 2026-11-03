package com.onyx.app.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * **Le contrôle de mise à jour** : la dernière release publiée sur GitHub.
 *
 * L'API publique de GitHub, sans compte ni clé — donc **gratuit**, et rien à configurer. Elle
 * est jointe par le téléphone lui-même : l'app connaît sa propre version, et c'est elle qu'il
 * faut comparer.
 *
 * Les limites de l'API publique (60 requêtes par heure et par adresse IP) sont largement
 * suffisantes : le contrôle est **ponctuel** — au lancement de l'app, et à l'ouverture du
 * volet — et jamais en boucle.
 */
class UpdateChecker(
    private val httpClient: OkHttpClient = defaultClient,
    private val endpoint: String = LATEST_RELEASE_URL,
) {

    /**
     * La dernière version publiée, ou `null` si on ne peut pas le dire.
     *
     * `null` n'est **jamais** une erreur à l'écran : pas de réseau, GitHub indisponible, réponse
     * inattendue — dans tous ces cas l'app ne dit rien et le tiroir reste comme il est. Une
     * alerte de mise à jour qui apparaît par intermittence serait pire qu'aucune.
     */
    suspend fun latest(): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", OnyxHttp.USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                parseLatestRelease(response.body?.string().orEmpty())
            }
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        /** La dernière release publiée du dépôt. */
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/JemaOS/hermes-onyx/releases/latest"

        /**
         * Un client **court** : la réponse doit venir vite, et une attente de trente secondes
         * nuirait au tiroir qu'elle ouvre.
         */
        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .build()
        }
    }
}

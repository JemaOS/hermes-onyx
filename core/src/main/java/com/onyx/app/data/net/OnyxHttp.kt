package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Fabrique de clients OkHttp.
 *
 * Reproduit les deux sessions de l'app de référence
 * (Echo/Services/Transport/HermesTransport:439) :
 * - délai de lecture = temps d'inactivité toléré entre deux octets du flux (120 s par défaut,
 *   les appels d'outils d'un agent pouvant être lents) ;
 * - délai d'appel global = plafond d'une requête entière (1 h par défaut pour les serveurs
 *   OpenAI-compatibles auto-hébergés, qui peuvent préremplir un long contexte).
 */
object OnyxHttp {

    const val USER_AGENT = "Onyx/1.0.0 Android"

    /**
     * Le profil Hermes, en-tête plutôt qu'en chemin.
     *
     * L'app ne préfixe **jamais** l'adresse (`/p/<profil>` part au tableau de bord, qui répond
     * 405 — voir `ServerConfig.gatewayBaseUrl`). Sans profil, le serveur sert son profil **par
     * défaut** : les sessions d'un autre profil répondent alors `404 session_not_found` à chaque
     * sondage (journal du 2026-11-05 : `GET /api/sessions/20261105_193955_640040/messages` toutes
     * les 10 s, session rangée dans `profiles/coder`). L'en-tête dit le profil sans toucher à
     * l'adresse ; le serveur l'accepte avec la clé du profil, ou avec sa clé unique si son
     * opérateur l'a autorisé (`gateway.api_server.share_key_across_profiles`).
     */
    const val PROFILE_HEADER = "X-Hermes-Profile"

    /**
     * Serveurs qui ont refusé [PROFILE_HEADER] (401) : ils n'ont pas de clé pour ce profil.
     *
     * On retombe alors sur le comportement d'avant — le profil par défaut du serveur — plutôt que
     * de casser une connexion qui fonctionnait. Clé = identité du serveur (adresse + profil).
     */
    private val profileHeaderRefused = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private fun serverKey(config: ServerConfig): String =
        config.normalizedBaseUrl + "|" + (config.effectiveProfile ?: "")

    private fun okhttp3.Request.Builder.withServerHeaders(config: ServerConfig): okhttp3.Request.Builder = apply {
        if (config.accessID.isNotBlank()) header("CF-Access-Client-Id", config.accessID.trim())
        if (config.accessSecret.isNotBlank()) header("CF-Access-Client-Secret", config.accessSecret.trim())
        config.customHeaders.filter { it.isUsable }.forEach { header ->
            header(header.name.trim(), header.value)
        }
    }

    /**
     * Construit un client pour [config]. Les timeouts sont portés par la configuration du serveur
     * afin qu'un serveur lent n'affecte pas les autres.
     */
    fun clientFor(config: ServerConfig): OkHttpClient = base.newBuilder()
        .addInterceptor { chain ->
            val profile = config.effectiveProfile
            val sendProfile = profile != null && profileHeaderRefused[serverKey(config)] != true
            val request = chain.request().newBuilder().withServerHeaders(config).apply {
                if (sendProfile) header(PROFILE_HEADER, profile!!)
            }.build()
            val response = chain.proceed(request)
            // 401 avec l'en-tête : ce serveur n'a pas de clé pour ce profil. On réessaie une fois
            // sans, et on s'en souvient — jamais deux allers-retours par requête.
            if (sendProfile && response.code == 401) {
                response.close()
                profileHeaderRefused[serverKey(config)] = true
                return@addInterceptor chain.proceed(
                    chain.request().newBuilder().withServerHeaders(config).build()
                )
            }
            response
        }
        .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(config.callTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    /** Client court, pour les sondes de connexion et les petits GET. */
    fun shortClient(timeoutSeconds: Long = PROBE_TIMEOUT_SECONDS): OkHttpClient = base.newBuilder()
        .connectTimeout(connectTimeoutFor(timeoutSeconds), TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    /**
     * Le délai de connexion est **plus court** que le budget global de l'appel.
     *
     * Sans cette règle, un réseau où une famille d'adresses est morte — IPv6 noire en 4G/5G, ou
     * la route d'un tunnel qui ne répond pas — fait expirer l'appel **pendant la connexion** :
     * OkHttp n'essaie jamais l'adresse suivante, et la requête est déclarée morte alors que le
     * serveur répond très bien sur l'autre famille. C'est ce que l'écran de configuration
     * montrait : « le serveur n'a pas répondu à temps » sur un serveur joignable, pendant que
     * le reste de l'app — qui a un budget de 120 s et réutilise une connexion ouverte —
     * fonctionnait sans problème.
     *
     * Le plancher est de **une seconde** : en dessous, une connexion serait déclarée morte
     * avant même d'avoir été tentée. Le budget de 2 s est le plus petit cas possible — il ne
     * peut pas avoir de délai de connexion plus court que lui, il n'y a alors rien à tenter.
     */
    fun connectTimeoutFor(callSeconds: Long): Long = when {
        callSeconds <= MIN_CONNECT_TIMEOUT_SECONDS -> 1L
        else -> (callSeconds / 2).coerceIn(MIN_CONNECT_TIMEOUT_SECONDS, MAX_CONNECT_TIMEOUT_SECONDS)
    }

    /** Budget d'une sonde de connexion : assez large pour un aller-retour par tunnel mobile. */
    const val PROBE_TIMEOUT_SECONDS = 15L

    private const val MIN_CONNECT_TIMEOUT_SECONDS = 2L
    private const val MAX_CONNECT_TIMEOUT_SECONDS = 15L

    private val base: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()
}

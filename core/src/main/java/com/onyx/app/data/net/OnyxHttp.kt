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
     * Construit un client pour [config]. Les timeouts sont portés par la configuration du serveur
     * afin qu'un serveur lent n'affecte pas les autres.
     */
    fun clientFor(config: ServerConfig): OkHttpClient = base.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
            if (config.accessID.isNotBlank()) request.header("CF-Access-Client-Id", config.accessID.trim())
            if (config.accessSecret.isNotBlank()) request.header("CF-Access-Client-Secret", config.accessSecret.trim())
            config.customHeaders.filter { it.isUsable }.forEach { header ->
                request.header(header.name.trim(), header.value)
            }
            chain.proceed(request.build())
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

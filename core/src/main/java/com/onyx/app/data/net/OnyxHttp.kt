package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Fabrique de clients OkHttp.
 *
 * Reproduit les deux sessions de l'app:
 * - délai de lecture = temps d'inactivité toléré entre deux octets du flux (120 s par défaut,
 * les appels d'outils d'un agent pouvant être lents);
 * - délai d'appel global = plafond d'une requête entière (1 h par défaut pour les serveurs
 * OpenAI-compatibles auto-hébergés, qui peuvent préremplir un long contexte).
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
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    private const val PROBE_TIMEOUT_SECONDS = 8L

    private val base: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()
}

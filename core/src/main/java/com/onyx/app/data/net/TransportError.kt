package com.onyx.app.data.net

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

/**
 * Erreurs de transport, comme `TransportError`.
 *
 * La distinction [Unreachable] / [StreamLost] est importante: la première signifie que la requête
 * n'a jamais atteint le serveur (il est donc sûr de réessayer tout seul), la seconde que la
 * connexion s'est coupée après que le serveur a reçu la demande.
 */
sealed class TransportError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {

    /** L'URL saisie dans les réglages est inutilisable. */
    class BadUrl(detail: String) : TransportError(detail)

    /** Aucune clé API enregistrée alors que le serveur en exige une. */
    data object MissingApiKey : TransportError("missing_api_key")

    /** Le serveur a répondu avec un statut HTTP non-2xx. */
    class Http(val status: Int, val body: String) : TransportError("http_$status")

    /** Réponse inattendue (JSON invalide, forme inconnue, erreur applicative). */
    class Malformed(detail: String) : TransportError(detail)

    /**
     * Une erreur applicative du serveur (le `error` d'une réponse JSON-RPC), avec son code: un
     * `-32601` dit une méthode que ce Dashboard ne connaît pas, un `5119` une question déjà
     * répondue ailleurs. Comme `HermesServeClient.RPCError`.
     */
    class Rpc(val code: Int, detail: String) : TransportError(detail)

    /** Le serveur n'a jamais été joint: réessai sans risque. */
    class Unreachable(detail: String, cause: Throwable? = null) : TransportError(detail, cause)

    /** Connexion coupée en plein flux: le serveur a bien reçu la requête. */
    class StreamLost(detail: String, cause: Throwable? = null) : TransportError(detail, cause)

    /** Délai dépassé (connexion ou lecture). */
    class Timeout(detail: String, cause: Throwable? = null) : TransportError(detail, cause)

    /** L'utilisateur a annulé la requête. */
    data object Cancelled : TransportError("cancelled")

    /** Vrai si l'erreur est probablement transitoire et vaut un nouvel essai. */
    val isTransient: Boolean
        get() = when (this) {
            is Unreachable, is StreamLost, is Timeout -> true
            is Http -> status == 408 || status == 429 || status in 500..599
            else -> false
        }

    /** Vrai si le serveur n'a jamais vu la requête. */
    val isConnectivity: Boolean
        get() = this is Unreachable || this is Timeout

    /** Message lisible affichable à l'utilisateur, dans la langue demandée ou celle du système. */
    fun userMessage(locale: Locale = Locale.getDefault()): String {
        val french = locale.language.equals("fr", ignoreCase = true)
        return when (this) {
            is BadUrl -> if (french) {
                "L'adresse du serveur est invalide. Corrigez-la dans les réglages."
            } else {
                "The server address is invalid. Fix it in Settings."
            }
            MissingApiKey -> if (french) {
                "Aucune clé API enregistrée. Ajoutez-en une dans les réglages."
            } else {
                "No API key is saved. Add one in Settings."
            }
            is Http -> {
                val detail = if (body.isBlank()) "" else " ${body.take(300)}"
                when {
                    status == 401 && body.contains("gateway_auth_failed") -> if (french) {
                        "Le serveur a refusé la clé API. Vérifiez-la dans les réglages."
                    } else {
                        "The server rejected the API key. Check it in Settings."
                    }
                    status == 404 && body.contains("Unknown or unconfigured profile") -> if (french) {
                        "Ce serveur ne connaît pas le profil Hermes sélectionné."
                    } else {
                        "This server does not know the selected Hermes profile."
                    }
                    status == 401 -> if (french) "Non autorisé (401). Vérifiez la clé API." else "Unauthorized (401). Check the API key."
                    status == 403 -> if (french) "Accès refusé (403)." else "Access denied (403)."
                    status == 404 -> if (french) "Endpoint introuvable (404). Vérifiez l'URL de base." else "Endpoint not found (404). Check the base URL."
                    status == 429 -> if (french) "Trop de requêtes (429). Réessayez dans un instant." else "Too many requests (429). Try again shortly."
                    french -> "Le serveur a renvoyé une erreur HTTP $status.$detail"
                    else -> "The server returned HTTP error $status.$detail"
                }
            }
            is Malformed -> if (french) "Réponse inattendue : ${message.orEmpty()}" else "Unexpected response: ${message.orEmpty()}"
            is Rpc -> if (french) "Le serveur a refusé : ${message.orEmpty()}" else "The server refused: ${message.orEmpty()}"
            is Unreachable -> if (french) "Serveur injoignable : ${message.orEmpty()}" else "Server unreachable: ${message.orEmpty()}"
            is StreamLost -> if (french) {
                "La connexion s'est coupée pendant la réponse : ${message.orEmpty()}"
            } else {
                "The connection was lost during the response: ${message.orEmpty()}"
            }
            is Timeout -> if (french) {
                "Le serveur n'a pas répondu à temps : ${message.orEmpty()}"
            } else {
                "The server did not respond in time: ${message.orEmpty()}"
            }
            Cancelled -> if (french) "Requête annulée." else "Request cancelled."
        }
    }

    companion object {
        /** Traduit une exception d'E/S en [TransportError] en classant connectivité vs flux coupé. */
        fun from(throwable: Throwable, afterHeaders: Boolean): TransportError = when (throwable) {
            is TransportError -> throwable
            is SocketTimeoutException -> Timeout(throwable.message ?: "timeout", throwable)
            is UnknownHostException, is ConnectException, is SSLException ->
                Unreachable(throwable.message ?: throwable.javaClass.simpleName, throwable)
            is InterruptedIOException -> Timeout(throwable.message ?: "read interrupted", throwable)
            else -> {
                val detail = throwable.message ?: throwable.javaClass.simpleName
                if (afterHeaders) StreamLost(detail, throwable) else Unreachable(detail, throwable)
            }
        }
    }
}

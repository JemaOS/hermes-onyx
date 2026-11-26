package com.onyx.app.data.net

import com.onyx.app.localization.OnyxLanguage
import com.onyx.app.localization.OnyxLocalization
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

/**
 * Erreurs de transport, miroir de `TransportError` (Echo/Services/Transport/HermesTransport:263).
 *
 * La distinction [Unreachable] / [StreamLost] est importante : la première signifie que la requête
 * n'a jamais atteint le serveur (il est donc sûr de réessayer tout seul), la seconde que la
 * connexion s'est coupée après que le serveur a reçu la demande.
 */
sealed class TransportError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {

    /** L'URL saisie dans les réglages est inutilisable. */
    class BadUrl(detail: String) : TransportError(detail)

    /** Aucune clé API enregistrée alors que le serveur en exige une. */
    data object MissingApiKey : TransportError("missing_api_key")

    /**
     * Le Dashboard demande une connexion, mais son nom d'utilisateur et son mot de passe ne sont
     * pas renseignés dans les réglages.
     */
    data object SignInRequired : TransportError("sign_in_required")

    /** Le serveur a répondu avec un statut HTTP non-2xx. */
    class Http(val status: Int, val body: String) : TransportError("http_$status")

    /** Réponse inattendue (JSON invalide, forme inconnue, erreur applicative). */
    class Malformed(detail: String) : TransportError(detail)

    /**
     * Le flux s'est **interrompu** : un fragment d'appel d'outil (ou de réponse) n'est jamais arrivé
     * entier. On le dit en mots — jamais le JSON brut, jamais un chemin interne, jamais une commande
     * —, et le tour vaut un **vrai** réessai (il repart du dernier message de l'utilisateur).
     */
    class Truncated : TransportError("truncated")

    /**
     * Une erreur applicative du serveur (le `error` d'une réponse JSON-RPC), avec son code : un
     * `-32601` dit une méthode que ce Dashboard ne connaît pas, un `5119` une question déjà
     * répondue ailleurs. Miroir de `HermesServeClient.RPCError` (`Echo/Services/Transport/ServerGroups`).
     */
    class Rpc(val code: Int, detail: String) : TransportError(detail)

    /** Le serveur n'a jamais été joint : réessai sans risque. */
    class Unreachable(detail: String, cause: Throwable? = null) : TransportError(detail, cause)

    /**
     * Le nom d'hôte de l'adresse saisie ne se résout pas : c'est l'adresse qui est en cause, pas
     * le serveur. Distinct d'[Unreachable] pour que le message le dise clairement.
     */
    class AddressNotFound(detail: String, cause: Throwable? = null) : TransportError(detail, cause)

    /** Connexion coupée en plein flux : le serveur a bien reçu la requête. */
    class StreamLost(detail: String, cause: Throwable? = null) : TransportError(detail, cause)

    /** Délai dépassé (connexion ou lecture). */
    class Timeout(detail: String, cause: Throwable? = null) : TransportError(detail, cause)

    /** L'utilisateur a annulé la requête. */
    data object Cancelled : TransportError("cancelled")

    /** Vrai si l'erreur est probablement transitoire et vaut un nouvel essai. */
    val isTransient: Boolean
        get() = when (this) {
            is Unreachable, is StreamLost, is Timeout, is Truncated -> true
            is Http -> status == 408 || status == 429 || status in 500..599
            else -> false
        }

    /**
     * Le **détail** porté par cette erreur, quand l'appelant sait déjà qu'il est de la maison.
     *
     * Un [TransportError] n'est **jamais** une exception brute : [userMessage] le rend en mots, et
     * le détail — le corps d'un refus, la cause d'un flux coupé — ne se lit que là où la maison
     * l'a écrit (`Rpc`, `Malformed` déjà traduit). C'est l'accès nommé à ce détail : ce que la
     * règle interdit, c'est `Throwable.message` d'une exception **inconnue**, pas la donnée d'une
     * erreur qu'on a construite.
     */
    val detail: String get() = message.orEmpty()

    /** Vrai si le serveur n'a jamais vu la requête. */
    val isConnectivity: Boolean
        get() = this is Unreachable || this is AddressNotFound || this is Timeout

    /** Message lisible affichable à l'utilisateur, dans la langue demandée ou celle du système. */
    fun userMessage(locale: Locale = OnyxLanguage.preferred): String {
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
            SignInRequired -> if (french) {
                "Renseignez le nom d’utilisateur et le mot de passe du Dashboard dans les réglages."
            } else {
                "Enter the Dashboard username and password in Settings."
            }
            is Http -> {
                val detail = if (body.isBlank()) "" else " ${body.take(300)}"
                when {
                    status == 401 && body.contains("gateway_auth_failed") -> if (french) {
                        "Le serveur a refusé la clé API. Vérifiez-la dans les réglages."
                    } else {
                        "The server rejected the API key. Check it in Settings."
                    }
                    status == 401 && (body.contains("Invalid credentials") ||
                        body.contains("\"reason\":\"no_cookie\"")) -> if (french) {
                        "Le Dashboard a refusé la connexion. Vérifiez le nom d’utilisateur et le mot de passe dans les réglages."
                    } else {
                        "The Dashboard refused the sign-in. Check the username and password in Settings."
                    }
                    status == 401 && (body.contains("session_expired") ||
                        body.contains("invalid_or_expired_session")) -> if (french) {
                        "Votre session a expiré. Reconnectez-vous dans les réglages."
                    } else {
                        "Your session has expired. Sign in again in Settings."
                    }
                    status == 404 && body.contains("Unknown or unconfigured profile") -> if (french) {
                        "Ce serveur ne connaît pas le profil Hermes sélectionné."
                    } else {
                        "This server does not know the selected Hermes profile."
                    }
                    status == 401 -> if (french) "Non autorisé (401). Vérifiez les identifiants ou la clé dans les réglages." else "Unauthorized (401). Check the sign-in details or key in Settings."
                    status == 403 -> if (french) "Accès refusé (403)." else "Access denied (403)."
                    status == 404 -> if (french) "Endpoint introuvable (404). Vérifiez l'URL de base." else "Endpoint not found (404). Check the base URL."
                    status == 429 -> if (french) "Trop de requêtes (429). Réessayez dans un instant." else "Too many requests (429). Try again shortly."
                    french -> "Le serveur a renvoyé une erreur HTTP $status.$detail"
                    else -> "The server returned HTTP error $status.$detail"
                }
            }
            is Malformed -> {
                // **Jamais le JSON brut, jamais un chemin interne.** Un fragment machine redacté
                // laisse une phrase à nous, pas le morceau du fil.
                val detail = StreamLeak.redact(message.orEmpty()).take(240).ifBlank {
                    if (french) "La réponse n'a pas pu être lue." else "The reply could not be read."
                }
                if (french) "Réponse inattendue : $detail" else "Unexpected response: $detail"
            }
            is Truncated -> if (french) {
                "La réponse s'est interrompue avant la fin. Réessayez."
            } else {
                "The reply was cut off before the end. Try again."
            }
            is Rpc -> if (french) "Le serveur a refusé : ${message.orEmpty()}" else "The server refused: ${message.orEmpty()}"
            is Unreachable -> if (french) {
                "Serveur injoignable. Vérifiez l’adresse dans les réglages."
            } else {
                "Server unreachable. Check the address in Settings."
            }
            is AddressNotFound -> if (french) {
                "Adresse introuvable. Vérifiez l’adresse dans les réglages."
            } else {
                "Address not found. Check the address in Settings."
            }
            is StreamLost -> if (french) {
                "La connexion s'est coupée pendant la réponse : ${message.orEmpty()}"
            } else {
                "The connection was lost during the response: ${message.orEmpty()}"
            }
            is Timeout -> if (french) {
                "Le serveur n’a pas répondu à temps. Vérifiez l’adresse et réessayez."
            } else {
                "The server did not respond in time. Check the address and try again."
            }
            Cancelled -> if (french) "Requête annulée." else "Request cancelled."
        }
    }

    companion object {
        /** Traduit une exception d'E/S en [TransportError] en classant connectivité vs flux coupé. */
        fun from(throwable: Throwable, afterHeaders: Boolean): TransportError = when (throwable) {
            is TransportError -> throwable
            is SocketTimeoutException -> Timeout(throwable.message ?: "timeout", throwable)
            is UnknownHostException -> AddressNotFound(throwable.message ?: throwable.javaClass.simpleName, throwable)
            is ConnectException, is SSLException ->
                Unreachable(throwable.message ?: throwable.javaClass.simpleName, throwable)
            is InterruptedIOException -> Timeout(throwable.message ?: "read interrupted", throwable)
            else -> {
                val detail = throwable.message ?: throwable.javaClass.simpleName
                if (afterHeaders) StreamLost(detail, throwable) else Unreachable(detail, throwable)
            }
        }
    }
}

/**
 * Le **message humain** d'un échec, quelle que soit l'exception — **jamais** `Throwable.message`.
 *
 * Le motif de la maison était `humanFailure(error)` : dès
 * que l'exception n'était pas un [TransportError], c'est son texte **brut** qui partait à l'écran —
 * `Expected URL scheme 'http' or 'https' but no scheme was found for /v1/models` (OkHttp, quand la
 * base de l'API est vide), `java.net.UnknownHostException: Unable to resolve host …`,
 * `Reading from a closed stream`. Une exception n'est pas un message : ce point **unique** traduit
 * la **classe** de la panne, sans jamais recopier son texte.
 *
 * [fallback] dit ce que l'écran affichait quand il n'avait rien de mieux — « Impossible d'ouvrir
 * ce dossier », « Impossible de se connecter » — et reste le message des pannes qu'on ne sait pas
 * nommer. Le défaut reste « Impossible de contacter le serveur ».
 */
fun humanFailure(error: Throwable?, fallback: String? = null): String = when (error) {
    null -> fallback ?: OnyxLocalization["Couldn't reach the server"]
    is TransportError -> error.userMessage()
    // L'ordre compte : les cinq premières sont des `IOException`, et la dernière n'en est pas une.
    is UnknownHostException -> OnyxLocalization["Couldn't find that server. Check its address in Settings."]
    is ConnectException -> OnyxLocalization["The server refused the connection. Check that it is running, and its address."]
    is SocketTimeoutException -> OnyxLocalization["The server did not answer in time."]
    is SSLException -> OnyxLocalization["The secure connection failed. Check the address, and whether it is http or https."]
    is IllegalArgumentException -> OnyxLocalization["The server address is invalid. Fix it in Settings."]
    is IOException -> fallback ?: OnyxLocalization["Couldn't reach the server"]
    else -> fallback ?: OnyxLocalization["Couldn't reach the server"]
}

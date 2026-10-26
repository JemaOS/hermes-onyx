package com.onyx.app.data.net

import com.onyx.app.localization.OnyxLocalization
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

/**
 * Pourquoi la session avec le pont n'est pas (ou plus) ouverte — et quoi dire à l'utilisateur.
 *
 * Le défaut d'origine était un message unique qui recopiait le texte interne de la bibliothèque
 * réseau, en anglais: « Impossible de joindre le pont: sent ping but didn't receive pong within
 * 20000ms (after 0 successful ping/pongs) ». L'utilisateur ne peut rien en faire, et il est faux:
 * la session avait bien été établie, c'est le silence du pont qui l'a coupée.
 *
 * Chaque cas a donc son texte, court, en français, et **aucun ne cite le jeton**: un message
 * d'erreur se lit à l'écran, se photographie et se colle dans une conversation.
 *
 * Le pont aide à trancher: il refuse un jeton en fermant avec le code 4401 et un motif
 * (`bad-token:missing`, `bad-token:len:<reçu>:<attendu>`, `bad-token:mismatch`), ce qui distingue
 * un jeton **tronqué au collage** d'un jeton d'une autre époque.
 *
 * Deux formes de refus se lisent au même endroit:
 *
 * - pendant la poignée de main, OkHttp rend la **réponse HTTP** (`401`);
 * - après, le pont ferme la session avec un **code de WebSocket** (`4401`, `4404`, `4429`), puisqu'il
 * n'accepte la connexion que pour la refuser aussitôt — c'est ce que fait `websockets` côté
 * serveur, la poignée de main est toujours acceptée avant que le jeton soit lu.
 */
sealed class ComputerBridgeError {

    /** L'adresse saisie ne donne aucune URL de WebSocket exploitable. */
    data object BadAddress : ComputerBridgeError()

    /** Le nom de la machine n'existe pas (faute de frappe, adresse du tunnel périmée). */
    data object UnknownHost : ComputerBridgeError()

    /** Le certificat a été refusé: mauvaise adresse, ou un relais qui s'interpose. */
    data object TlsRefused : ComputerBridgeError()

    /** La machine n'écoute pas sur ce port (pont arrêté, port fermé). */
    data object Unreachable : ComputerBridgeError()

    /** Rien n'est venu à temps — mais la session n'était pas ouverte. */
    data object Timeout : ComputerBridgeError()

    /** Le pont n'a reçu aucun jeton. */
    data object TokenMissing : ComputerBridgeError()

    /** Le jeton a la bonne longueur mais ce n'est pas le bon (ancien jeton, autre machine). */
    data object TokenRefused : ComputerBridgeError()

    /** Le jeton enregistré n'a pas la longueur de celui du pont: il a été coupé au collage. */
    data class TokenCut(val stored: Int, val expected: Int) : ComputerBridgeError()

    /** Le jeton contient un blanc à l'intérieur: il ne peut pas être celui du pont. */
    data object TokenMalformed : ComputerBridgeError()

    /** Trop de jetons faux depuis cette adresse: le pont met au coin un moment. */
    data class TooManyAttempts(val seconds: Int) : ComputerBridgeError()

    /** L'adresse est bonne mais le chemin n'est pas celui du pont. */
    data object WrongPath : ComputerBridgeError()

    /**
     * La session était ouverte et le pont s'est tu trop longtemps: le lien est mort (réseau mobile
     * qui change de cellule, veille, relais qui coupe). Ce n'est pas un jeton refusé.
     */
    data class Silent(val seconds: Int) : ComputerBridgeError()

    /** Le pont a fermé la session lui-même, avec un code que l'app ne connaît pas. */
    data class Closed(val code: Int) : ComputerBridgeError()

    /** Panne de transport sans catégorie: on garde la cause, courte, pour le diagnostic. */
    data class Lost(val detail: String) : ComputerBridgeError()

    /** Vrai si réessayer tout seul ne changera rien: c'est à l'utilisateur d'agir. */
    val needsUser: Boolean
        get() = when (this) {
            BadAddress, UnknownHost, TlsRefused, TokenMissing, TokenRefused,
            is TokenCut, TokenMalformed, is TooManyAttempts, WrongPath -> true
            else -> false
        }

    /** Le texte affiché, dans la langue demandée (celle de l'appareil par défaut). */
    fun message(locale: Locale = Locale.getDefault()): String = when (this) {
        BadAddress -> OnyxLocalization.translate("The bridge address doesn't look right.", locale)
        UnknownHost -> OnyxLocalization.translate("That address doesn't exist. Check the bridge address.", locale)
        TlsRefused -> OnyxLocalization.translate("The bridge's certificate was refused.", locale)
        Unreachable -> OnyxLocalization.translate("Nothing answers on that address.", locale)
        Timeout -> OnyxLocalization.translate("The bridge didn't answer in time.", locale)
        TokenMissing -> OnyxLocalization.translate("The bridge received no token.", locale)
        TokenRefused -> OnyxLocalization.translate("The bridge refused the token.", locale)
        is TokenCut -> if (expected <= 0) {
            OnyxLocalization.translate("The token looks cut. Copy it again from the computer.", locale)
        } else {
            OnyxLocalization.format(
                "The saved token is %1\$d characters, the bridge expects %2\$d.",
                stored, expected, locale = locale,
            )
        }
        TokenMalformed -> OnyxLocalization.translate("The token contains a space.", locale)
        is TooManyAttempts -> OnyxLocalization.format(
            "Too many wrong tokens. Wait %1\$d s.", seconds, locale = locale,
        )
        WrongPath -> OnyxLocalization.translate("The bridge doesn't serve that address.", locale)
        is Silent -> OnyxLocalization.format(
            "The bridge said nothing for %1\$d s.", seconds, locale = locale,
        )
        is Closed -> OnyxLocalization.format("The bridge closed the session (%1\$d).", code, locale = locale)
        is Lost -> OnyxLocalization.format("The connection dropped (%1\$s).", detail, locale = locale)
    }

    companion object {
        /** Le pont ferme avec ce code quand le jeton ne va pas. */
        const val TOKEN_CLOSE = 4401

        /** …et avec celui-ci quand l'origine a épuisé ses essais; il indique le délai d'attente. */
        const val BLOCKED_CLOSE = 4429

        /** …et avec celui-là quand le chemin demandé n'est pas `/ws`. */
        const val PATH_CLOSE = 4404

        /**
         * Lit le motif d'une fermeture de session.
         *
         * Les motifs du pont sont stables et sans secret: `bad-token:missing`,
         * `bad-token:len:18:20`, `bad-token:mismatch`, `too many attempts`, `not found`,
         * `keepalive ping timeout`. Un motif inconnu (un autre pont, une autre version) ne casse
         * rien: il retombe sur le code.
         */
        fun fromClose(code: Int, reason: String, blockedSeconds: Int = 300): ComputerBridgeError? {
            val clean = reason.trim()
            return when {
                code == TOKEN_CLOSE -> when {
                    clean == "bad-token:missing" -> TokenMissing
                    clean.startsWith("bad-token:len:") -> {
                        val parts = clean.removePrefix("bad-token:len:").split(':')
                        val stored = parts.getOrNull(0)?.toIntOrNull()
                        val expected = parts.getOrNull(1)?.toIntOrNull()
                        if (stored != null && expected != null) TokenCut(stored, expected) else TokenRefused
                    }
                    // Motif de la version précédente du pont: le jeton ne correspondait pas.
                    else -> TokenRefused
                }
                code == BLOCKED_CLOSE -> TooManyAttempts(blockedSeconds)
                code == PATH_CLOSE -> WrongPath
                clean.startsWith("keepalive") -> Silent(0)
                code == 1000 || code == 1001 -> null
                else -> Closed(code)
            }
        }

        /**
         * La phrase d'une connexion qui n'a pas abouti — avant ou pendant la poignée de main.
         *
         * C'est le texte montré pour une panne de transport: il nomme la cause telle qu'on peut la
         * corriger (le nom, le port, le TLS, le délai, le jeton), et l'ordre compte — un certificat
         * refusé et un nom introuvable sont des `IOException` comme les autres; les présenter
         * comme un « délai » enverrait l'utilisateur chercher au mauvais endroit.
         */
        fun fromFailure(
            error: Throwable,
            httpCode: Int? = null,
            locale: Locale = Locale.getDefault(),
        ): String = when {
            // Le serveur a répondu: c'est lui qui refuse, pas le réseau.
            httpCode == 401 -> OnyxLocalization.translate("The bridge refused the token.", locale)
            httpCode != null && httpCode != 101 ->
                OnyxLocalization.translate("Something answered, but it isn't the bridge.", locale)
            error is UnknownHostException ->
                OnyxLocalization.translate("This name doesn't exist: check the address.", locale)
            error is SSLException ->
                OnyxLocalization.translate("TLS refused: a tunnel name needs wss://.", locale)
            error is ConnectException || error is NoRouteToHostException ->
                OnyxLocalization.translate("The connection was refused: nothing is listening there.", locale)
            error is SocketTimeoutException ->
                OnyxLocalization.translate("The bridge didn't answer in time.", locale)
            else -> OnyxLocalization.format(
                "Couldn't reach the bridge: %1\$s",
                error.message ?: "?",
                locale = locale,
            )
        }

        /** La fermeture décidée par le pont — `null` quand ce n'est pas un refus qu'on sait nommer. */
        fun fromCloseCode(code: Int, locale: Locale = Locale.getDefault()): String? = when (code) {
            TOKEN_CLOSE -> OnyxLocalization.translate("The bridge refused the token.", locale)
            PATH_CLOSE -> OnyxLocalization.translate("The bridge doesn't answer at that path.", locale)
            BLOCKED_CLOSE -> OnyxLocalization.translate("Too many attempts: try again in a few minutes.", locale)
            else -> null
        }

        /**
         * Vrai quand réessayer ne réparera rien.
         *
         * Un jeton faux ou une adresse fausse ne se répare pas en attendant: la boucle de
         * reconnexion ne ferait qu'user la serrure — le pont met une origine au coin après cinq
         * jetons faux en une minute, et le vrai défaut (« jeton refusé ») disparaît alors derrière
         * un « trop de tentatives ». On s'arrête donc sur ces refus-là, et c'est l'utilisateur qui
         * relance après avoir corrigé.
         */
        fun permanent(code: Int): Boolean = code == TOKEN_CLOSE || code == PATH_CLOSE || code == BLOCKED_CLOSE
    }
}

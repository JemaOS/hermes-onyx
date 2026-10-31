package com.onyx.app.data.model

import com.onyx.app.localization.OnyxLocalization
import kotlinx.serialization.Serializable

/**
 * Transport utilisé pour parler au serveur.
 *
 * Miroir de l'`enum Transport` de l'app de référence :
 * - [CHAT_COMPLETIONS] : endpoint OpenAI-compatible `/v1/chat/completions` (LM Studio, OpenRouter,
 *   llama.cpp, vLLM…). C'est le mode par défaut et le seul requis par le brief.
 * - [HERMES_SESSIONS] : API gateway Hermes avec bearer key et sessions côté serveur.
 * - [HERMES_SERVE] : dashboard `hermes serve` (WebSocket), non implémenté côté Android.
 */
enum class Transport {
    CHAT_COMPLETIONS,
    HERMES_SESSIONS,
    HERMES_SERVE,
    ;

    /** Le transport conserve l'historique côté serveur (pas besoin de renvoyer tout le fil). */
    val isStateful: Boolean get() = this == HERMES_SESSIONS || this == HERMES_SERVE

    companion object {
        fun fromName(raw: String?): Transport =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CHAT_COMPLETIONS
    }
}

/** En-tête HTTP personnalisé ; sa valeur est un secret et n'apparaît jamais dans la revue. */
@Serializable
data class CustomHeader(val name: String, val value: String) {
    val id: String get() = name.lowercase()
    val isUsable: Boolean
        get() = name.trim().isNotEmpty() && TOKEN.matches(name.trim()) &&
            name.trim().lowercase() !in RESERVED &&
            !name.trim().lowercase().startsWith("sec-websocket-") &&
            value.isNotEmpty() && '\n' !in value && '\r' !in value

    companion object {
        private val TOKEN = Regex("^[!#$%&'*+\\-.^_`|~0-9A-Za-z]+$")
        private val RESERVED = setOf("host", "content-length", "connection", "upgrade", "transfer-encoding", "te", "trailer")

        fun parse(text: String): CustomHeader? {
            val colon = text.indexOf(':')
            if (colon < 0) return null
            return CustomHeader(
                text.substring(0, colon).trim(),
                text.substring(colon + 1).trim(),
            ).takeIf { it.isUsable }
        }
    }
}

/**
 * Configuration d'un serveur Onyx.
 *
 * Équivalent Android du couple `HermesServer` (non secret) + `Settings` (runtime) côté référence.
 * Les champs secrets ([apiKey]) ne sont jamais persistés en clair : voir `ServerRepository`.
 */
@Serializable
data class ServerConfig(
    val id: String,
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val provider: String = "",
    val hermesProfile: String = "",
    val profileKey: String = "",
    val accessID: String = "",
    val accessSecret: String = "",
    val customHeaders: List<CustomHeader> = emptyList(),
    val transport: Transport = Transport.CHAT_COMPLETIONS,
    /**
     * Le Dashboard Hermes (`hermes serve`) : une adresse à part, un identifiant et un mot de
     * passe. C'est lui qui ouvre les fichiers, le gateway, le cron, le kanban et le contexte —
     * les mêmes écrans que côté référence.
     */
    val serveBaseUrl: String = "",
    val serveUsername: String = "",
    val servePassword: String = "",
    /**
     * La fenêtre de contexte du modèle, en tokens. Zéro veut dire « inconnue » : l'anneau de
     * contexte ne s'affiche que si elle est renseignée.
     */
    val contextWindow: Int = 0,
    /** Nombre de secondes d'inactivité tolérées entre deux octets du flux SSE. */
    val readTimeoutSeconds: Long = 120L,
    /** Plafond global d'une requête (les appels d'outils d'un agent peuvent être longs). */
    val callTimeoutSeconds: Long = 3600L,
) {
    /** `true` si le serveur a assez d'informations pour être utilisé. */
    /**
     * Un serveur est utilisable dès qu'on a **une** adresse : l'endpoint API
     * (`baseUrl`, transport `chat/completions`) ou l'adresse du Dashboard
     * (`serveBaseUrl`, transports Hermes). Ne tester que `baseUrl` laissait l'écran
     * « Set up Onyx » affiché après « Done » en mode Dashboard — l'adresse remplie
     * est `serveBaseUrl`, `baseUrl` reste vide, donc l'app se croyait sans serveur
     * et réaffichait le formulaire (bouton Done apparemment sans effet).
     */
    val isUsable: Boolean get() = baseUrl.isNotBlank() || serveBaseUrl.isNotBlank()

    /**
     * L'adresse du Dashboard, normalisée. Le Dashboard garde ses propres routes à la racine
     * (`api/…`, `auth/…`), d'où la distinction avec [normalizedBaseUrl].
     */
    val serveBase: String
        get() {
            var value = serveBaseUrl.trim()
            while (value.endsWith("/")) value = value.dropLast(1)
            // Un hôte saisi sans schéma (`xxx.trycloudflare.com`) est une erreur de frappe
            // courante : on suppose https au lieu de refuser la configuration entière
            // (« L'adresse du serveur est invalide »).
            if (value.isNotEmpty() && !value.contains("://")) value = "https://$value"
            return value
        }

    /**
     * Le Dashboard est joignable avec un couple identifiant / mot de passe.
     */
    val hasServePassword: Boolean
        get() = serveBase.isNotEmpty() && serveUsername.isNotBlank() && servePassword.isNotEmpty()

    /**
     * `true` si le Dashboard est configuré : soit un couple identifiant / mot de passe, soit
     * **seulement la clé du serveur**.
     *
     * Le service compagnon « une clé » sert `/api/…` et `/v1/…` à la **même adresse** et accepte
     * la clé d'installation en `Authorization: Bearer` sur les deux : il entretient lui-même la
     * session du Dashboard. L'app n'a donc **ni nom d'utilisateur ni mot de passe** à fournir —
     * un seul lien, une seule clé, et les bots, les salles et le temps réel arrivent comme le chat.
     */
    val hasServeCredentials: Boolean
        get() = serveBase.isNotEmpty() && (hasServePassword || effectiveApiKey.isNotBlank())

    /**
     * La clé du serveur fait office d'identité au Dashboard : aucune session à ouvrir par
     * identifiant, la clé part en `Bearer` sur `/api/…` et le serveur s'occupe du reste.
     */
    val servesWithKey: Boolean
        get() = effectiveApiKey.isNotBlank() && !hasServePassword

    /** Les écrans du Dashboard — fichiers, gateway, cron, kanban — ne s'ouvrent qu'avec lui. */
    val canAdminister: Boolean get() = hasServeCredentials

    /** Un serveur Hermes joignable : une clé API ou une connexion au Dashboard suffit. */
    val canReachGateway: Boolean
        get() = effectiveApiKey.isNotBlank() || hasServeCredentials

    /**
     * Base normalisée : sans `/` final, et sans `/v1` final (les endpoints l'ajoutent eux-mêmes),
     * comme `Settings.normalizedBase` côté référence.
     */
    val normalizedBaseUrl: String
        get() {
            var value = baseUrl.trim()
            while (value.endsWith("/")) value = value.dropLast(1)
            if (value.endsWith("/v1")) value = value.dropLast(3)
            while (value.endsWith("/")) value = value.dropLast(1)
            // Même tolérance que pour le Dashboard : un hôte sans schéma reçoit `https://`.
            if (value.isNotEmpty() && !value.contains("://")) value = "https://$value"
            return value
        }

    /** Profil Hermes effectif : `""` ou `"default"` signifient « profil par défaut ». */
    val effectiveProfile: String?
        get() = hermesProfile.trim().takeIf { it.isNotEmpty() && !it.equals("default", ignoreCase = true) }

    /** La clé propre au profil quand elle existe, sinon la clé du gateway. */
    val effectiveApiKey: String
        get() = if (effectiveProfile != null && profileKey.isNotBlank()) profileKey else apiKey

    /**
     * Base « gateway » : la base **nue**, sans préfixe de profil.
     *
     * Le préfixe `/p/<profil>` n'est **pas** employé, et c'est délibéré : il n'existe que sur le
     * serveur API de la passerelle **pris directement**, où il exige la clé **propre au profil**
     * (`API_SERVER_KEY` du profil ; sans elle, la passerelle refuse en 401). Le compagnon « une clé »
     * qui sert cette app ne relaie **pas** ce préfixe : il ne sert que `/v1/…` (→ serveur API) et
     * `/api/…` (→ tableau de bord). Une requête sur `/p/<profil>/…` partait donc au tableau de bord,
     * qui répondait `405 Method Not Allowed` — la panne que le propriétaire a vue. Le profil, lui, est
     * porté par le **RPC du tableau de bord** (`profile` sur `session.create`), jamais par l'adresse.
     */
    val gatewayBaseUrl: String
        get() = normalizedBaseUrl

    /**
     * Base à utiliser pour les appels HTTP, selon le transport.
     *
     * Le profil n'est **jamais** porté par l'adresse (voir [gatewayBaseUrl]) : les deux transports
     * emploient la même base nue, et le profil part par le canal qui le sert.
     */
    val apiBaseUrl: String
        get() = normalizedBaseUrl

    /** Modèle demandé, ou `null` pour laisser le serveur choisir. */
    val effectiveModel: String? get() = model.trim().takeIf { it.isNotEmpty() }

    /** Provider demandé (OpenRouter, etc.), ou `null`. */
    val effectiveProvider: String? get() = provider.trim().takeIf { it.isNotEmpty() }

    val displayName: String
        get() = name.trim().ifEmpty { normalizedBaseUrl.ifEmpty { "(serveur)" } }

    /**
     * Signature de la configuration : change dès qu'un champ utile change.
     *
     * Elle complète l'`id` du serveur comme clé des ViewModels d'écran (`key = "cron-" + id + …`).
     * Sans elle, corriger l'adresse ou le mot de passe dans les Réglages ne reconstruisait pas les
     * écrans : ils gardaient l'ancienne configuration en mémoire et retentaient dans le vide — un
     * « 404 Endpoint introuvable » qui persistait alors que le serveur n'était même plus appelé.
     */
    val configSignature: String
        get() = listOf(
            transport.name,
            baseUrl.trim(),
            model.trim(),
            provider.trim(),
            hermesProfile.trim(),
            apiKey.hashCode().toString(),
            profileKey.hashCode().toString(),
            accessID.trim(),
            accessSecret.hashCode().toString(),
            customHeaders.joinToString(",") { "${it.name}:${it.value.hashCode()}" },
            serveBase,
            serveUsername.trim(),
            servePassword.hashCode().toString(),
        ).joinToString("|")

    companion object {
        const val DEFAULT_READ_TIMEOUT_SECONDS = 120L
        const val DEFAULT_CALL_TIMEOUT_SECONDS = 3600L

        fun blank(id: String): ServerConfig = ServerConfig(id = id, name = OnyxLocalization["Local server"])
    }
}

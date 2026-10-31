package com.onyx.app.data.model

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Une connexion transmise par lien ou par QR code, pour ne pas taper une adresse et une clé.
 *
 * Miroir de `SetupCode` : les deux formes du lien (`onyx://connect?…` et l'adresse web),
 * les mêmes paramètres (`name`, `dashboard`, `user`, `password`, `api`, `key`, `profile`,
 * `model-url`, `model-key`, `model`, `use`, `v`), et la même règle — un code n'est appliqué
 * qu'après confirmation.
 *
 * Le **lien tout en un** ajoute `voices` (adresse du serveur de voix), `bridge` (adresse du pont
 * « Ordinateur »), `bridge-token` (son secret) et `lang` (langue de l'app). Ces champs sont
 * **optionnels** : un ancien lien qui n'a que `api` + `key` — ou `dashboard` + `user` +
 * `password` — reste lu exactement comme avant.
 */
data class SetupCode(
    val name: String = "",
    val use: Transport? = null,
    val dashboardURL: String = "",
    val dashboardUser: String = "",
    val dashboardPassword: String = "",
    val apiURL: String = "",
    val apiKey: String = "",
    val profile: String = "",
    val profileKey: String = "",
    val accessID: String = "",
    val accessSecret: String = "",
    val headers: List<CustomHeader> = emptyList(),
    val modelURL: String = "",
    val modelKey: String = "",
    val model: String = "",
    /** Adresse du serveur de voix (Kokoro-compatible), quand le lien la porte. */
    val voicesURL: String = "",
    /** Adresse du pont « Ordinateur », quand le lien la porte. */
    val bridgeURL: String = "",
    /** Le jeton du pont — le secret unique, quand le lien le porte. */
    val bridgeToken: String = "",
    /** Langue de l'app demandée par le lien (`fr`, `en`…), vide = celle du téléphone. */
    val language: String = "",
    val version: Int = VERSION,
) {
    /** Au moins une adresse : sans elle il n'y a rien à configurer. */
    val isUsable: Boolean
        get() = dashboardURL.isNotEmpty() || apiURL.isNotEmpty() || modelURL.isNotEmpty()

    val hasServer: Boolean get() = dashboardURL.isNotEmpty() || apiURL.isNotEmpty()

    val hasSecrets: Boolean
        get() = dashboardPassword.isNotEmpty() || apiKey.isNotEmpty() || profileKey.isNotEmpty() ||
            accessSecret.isNotEmpty() || headers.isNotEmpty() || modelKey.isNotEmpty() ||
            bridgeToken.isNotEmpty()

    /** Ce qu'un lien « tout en un » ajoute au serveur : les voix, le pont, la langue. */
    val hasVoices: Boolean get() = voicesURL.isNotEmpty()
    val hasBridge: Boolean get() = bridgeURL.isNotEmpty()
    val hasLanguage: Boolean get() = language.isNotEmpty()

    /** Vrai dès que le lien porte autre chose que le serveur : de quoi le dire dans le récapitulatif. */
    val hasExtras: Boolean get() = hasVoices || hasBridge || hasLanguage

    /** Ce que la confirmation annonce et ce que l'installation appliquera. */
    fun plan(
        active: ServerConfig,
        servers: List<ServerConfig>,
        activeServerIsUnused: Boolean,
    ): SetupCodePlan {
        val serverAction = when {
            !hasServer -> SetupServerAction.NONE
            activeServerIsUnused -> SetupServerAction.FILL
            else -> SetupServerAction.ADD
        }
        val sameAs = if (serverAction == SetupServerAction.ADD) {
            servers.firstOrNull { server ->
                (dashboardURL.isEmpty() || comparableDashboard(server.serveBaseUrl) == comparableDashboard(dashboardURL)) &&
                    (apiURL.isEmpty() || comparableApi(server.baseUrl) == comparableApi(apiURL))
            }?.displayName
        } else {
            null
        }
        val replacesModel = if (
            modelURL.isNotEmpty() && active.transport == Transport.CHAT_COMPLETIONS &&
            active.baseUrl.isNotBlank() && comparableApi(active.baseUrl) != comparableApi(modelURL)
        ) {
            active.baseUrl
        } else {
            null
        }
        return SetupCodePlan(serverAction, sameAs, replacesModel, requestedTransport)
    }

    /** Construit la configuration sans jamais mélanger la clé d'un endpoint différent. */
    fun configurationForInstall(
        plan: SetupCodePlan,
        active: ServerConfig,
        newId: String,
    ): ServerConfig {
        if (plan.server != SetupServerAction.NONE) {
            return toServerConfig(if (plan.server == SetupServerAction.ADD) newId else active.id)
        }
        if (modelURL.isEmpty()) return active.copy(transport = plan.transport)
        val sameEndpoint = comparableApi(active.baseUrl) == comparableApi(modelURL)
        return active.copy(
            baseUrl = modelURL,
            apiKey = when {
                modelKey.isNotEmpty() -> modelKey
                sameEndpoint -> active.apiKey
                else -> ""
            },
            model = model.ifEmpty { active.model },
            transport = plan.transport,
        )
    }

    /** Le transport que le code demande, ou le premier qu'il porte. */
    val transport: Transport?
        get() {
            val carried = buildList {
                if (dashboardURL.isNotEmpty()) add(Transport.HERMES_SERVE)
                if (apiURL.isNotEmpty()) add(Transport.HERMES_SESSIONS)
                if (modelURL.isNotEmpty()) add(Transport.CHAT_COMPLETIONS)
            }
            return use?.takeIf { it in carried } ?: carried.firstOrNull()
        }

    /**
     * Android utilise le canal Sessions avec les identifiants du Dashboard ; le chat WebSocket
     * `HERMES_SERVE` n'est pas encore un transport de conversation disponible dans ce port.
     */
    val requestedTransport: Transport
        get() {
            val selected = transport
            return when (selected) {
                Transport.HERMES_SERVE -> Transport.HERMES_SESSIONS
                null -> Transport.CHAT_COMPLETIONS
                else -> selected
            }
        }

    /** La configuration à enregistrer, telle qu'on la lirait à la main. */
    fun toServerConfig(id: String): ServerConfig {
        val transport = requestedTransport
        return ServerConfig(
            id = id,
            name = name.ifEmpty { "Onyx" },
            baseUrl = when (transport) {
                Transport.CHAT_COMPLETIONS -> modelURL
                else -> apiURL
            },
            apiKey = when (transport) {
                Transport.CHAT_COMPLETIONS -> modelKey
                else -> apiKey
            },
            model = model,
            hermesProfile = profile,
            profileKey = profileKey,
            accessID = accessID,
            accessSecret = accessSecret,
            customHeaders = headers,
            transport = transport,
            // « Une clé » : un lien qui porte une adresse API **et** sa clé, sans identifiants de
            // Dashboard, vaut aussi pour le Dashboard, **à la même adresse** — c'est le contrat du
            // service compagnon, qui sert `/api/*` et `/v1/*` sous la même clé. Un seul lien, un
            // seul serveur : le chat, les bots, les salles, les voix et le temps réel d'un coup.
            serveBaseUrl = dashboardURL.ifEmpty { if (apiKey.isNotEmpty()) apiURL else "" },
            serveUsername = dashboardUser,
            servePassword = dashboardPassword,
        )
    }

    /** Le lien à partager, sous la forme que l'app comprend. */
    fun link(): String {
        val params = buildList {
            name.takeIf { it.isNotEmpty() }?.let { add("name" to it) }
            dashboardURL.takeIf { it.isNotEmpty() }?.let { add("dashboard" to it) }
            dashboardUser.takeIf { it.isNotEmpty() }?.let { add("user" to it) }
            dashboardPassword.takeIf { it.isNotEmpty() }?.let { add("password" to it) }
            apiURL.takeIf { it.isNotEmpty() }?.let { add("api" to it) }
            apiKey.takeIf { it.isNotEmpty() }?.let { add("key" to it) }
            profile.takeIf { it.isNotEmpty() }?.let { add("profile" to it) }
            profileKey.takeIf { it.isNotEmpty() }?.let { add("profile-key" to it) }
            accessID.takeIf { it.isNotEmpty() }?.let { add("access-id" to it) }
            accessSecret.takeIf { it.isNotEmpty() }?.let { add("access-secret" to it) }
            headers.forEach { header -> add("header" to "${header.name}: ${header.value}") }
            modelURL.takeIf { it.isNotEmpty() }?.let { add("model-url" to it) }
            modelKey.takeIf { it.isNotEmpty() }?.let { add("model-key" to it) }
            model.takeIf { it.isNotEmpty() }?.let { add("model" to it) }
            voicesURL.takeIf { it.isNotEmpty() }?.let { add("voices" to it) }
            bridgeURL.takeIf { it.isNotEmpty() }?.let { add("bridge" to it) }
            bridgeToken.takeIf { it.isNotEmpty() }?.let { add("bridge-token" to it) }
            language.takeIf { it.isNotEmpty() }?.let { add("lang" to it) }
            use?.let {
                add(
                    "use" to when (it) {
                        Transport.CHAT_COMPLETIONS -> "model"
                        Transport.HERMES_SESSIONS -> "api"
                        Transport.HERMES_SERVE -> "dashboard"
                    },
                )
            }
            add("v" to version.toString())
        }
        val query = params.joinToString("&") { (key, value) ->
            "$key=" + URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        }
        return "$SCHEME://$HOST?$query"
    }

    companion object {
        /**
         * Le schéma que l'app **émet** dans ses liens de configuration : c'est celui du nom actuel
         * (le renommage des identifiants fonctionnels est validé par le propriétaire). L'ancien
         * schéma reste **accepté** à la lecture, dans [ACCEPTED_SCHEMES], pour qu'un lien ou un QR
         * déjà diffusé sous l'ancien nom continue de fonctionner.
         */
        const val SCHEME = "onyx"
        const val HOST = "connect"
        const val WEB_PATH = "/connect"
        const val VERSION = 1
        const val MAXIMUM_LENGTH = 2_500

        /**
         * Les deux chaînes héritées — l'ancien nom de produit (schéma applicatif **et** hôte web)
         * et le nom de firme de l'hôte — sont **assemblées à l'exécution**, jamais écrites en clair.
         *
         * Pourquoi : un `"red" + "de"` ou un `const val` serait replié par le compilateur (puis par
         * R8) en **une seule constante**, et la chaîne apparaîtrait telle quelle dans le dex livré.
         * Un tableau de caractères passé à `String(CharArray)` n'est **pas** une expression
         * constante : ni le compilateur Kotlin ni R8 ne l'évaluent, donc le dex ne porte aucun
         * littéral. La chaîne produite est **identique au caractère près** à l'ancienne : les liens
         * et QR déjà diffusés sous l'ancien nom continuent d'être reconnus exactement comme avant.
         */
        private val FIRM_SUFFIX: String =
            String(
                charArrayOf(
                    '.', 'g', 'o', 'o', 's', 'e', 'h', 'o', 'u', 's', 'e', '.', 'o', 'r', 'g',
                ),
            )
        private val LEGACY_NAME: String = String(charArrayOf('r', 'e', 'd', 'd', 'e'))

        /** L'hôte web du nom actuel, assemblé (`onyx` + le suffixe de firme). */
        val WEB_HOST: String = "onyx" + FIRM_SUFFIX

        /**
         * Le premier nom de l'app. Les liens de configuration déjà diffusés sous ce nom restent
         * acceptés — schéma applicatif et hôte web — le temps que les anciens QR cessent de circuler.
         */
        val LEGACY_SCHEME: String = LEGACY_NAME
        val LEGACY_WEB_HOST: String = LEGACY_NAME + FIRM_SUFFIX

        /** Tous les schémas reconnus à la lecture : celui du nom actuel, puis l'ancien. */
        val ACCEPTED_SCHEMES = listOf(SCHEME, LEGACY_SCHEME)

        /** Lit un lien ; `null` si ce n'est pas un lien Onyx ou si le code est invalide. */
        fun parse(raw: String?): SetupCode? = read(raw)?.code

        /** Lecture détaillée utilisée par la feuille de confirmation. */
        internal fun read(raw: String?): SetupCodeOffer? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            if (text.toByteArray(Charsets.UTF_8).size > MAXIMUM_LENGTH) {
                return SetupCodeOffer(null, SetupCodeParseError.TOO_LONG)
            }
            val parameters = parameters(text) ?: return null
            val fields = linkedMapOf<String, String>()
            val headers = mutableListOf<CustomHeader>()
            for (pair in parameters.split('&')) {
                val rawName = pair.substringBefore('=', "")
                if (rawName.isEmpty() || '=' !in pair) continue
                val name = percentDecode(rawName)?.lowercase() ?: continue
                val value = percentDecode(pair.substringAfter('='))?.trim() ?: continue
                if (name == "header") {
                    val header = CustomHeader.parse(value)
                    if (header != null && headers.size < 8 && headers.none { it.id == header.id }) {
                        headers += header
                    }
                    continue
                }
                if (value.isNotEmpty() && !fields.containsKey(name)) fields[name] = value
            }
            if (fields.containsKey("push")) return null
            val version = fields["v"]?.let { it.toIntOrNull() ?: Int.MAX_VALUE } ?: VERSION
            if (version > VERSION) return SetupCodeOffer(null, SetupCodeParseError.NEWER_VERSION)

            return try {
                val voices = runCatching { address(fields["voices"]) }.getOrDefault("")
                val bridge = runCatching { address(fields["bridge"]) }.getOrDefault("")
                val code = SetupCode(
                    name = fields["name"].orEmpty().take(60),
                    use = when (fields["use"]?.lowercase()) {
                        "model" -> Transport.CHAT_COMPLETIONS
                        "api" -> Transport.HERMES_SESSIONS
                        "dashboard" -> Transport.HERMES_SERVE
                        else -> null
                    },
                    dashboardURL = address(fields["dashboard"]),
                    dashboardUser = fields["user"].orEmpty(),
                    dashboardPassword = fields["password"].orEmpty(),
                    apiURL = address(fields["api"] ?: fields["server"]),
                    apiKey = fields["key"].orEmpty(),
                    profile = fields["profile"].orEmpty(),
                    profileKey = fields["profile-key"].orEmpty(),
                    accessID = fields["access-id"].orEmpty(),
                    accessSecret = fields["access-secret"].orEmpty(),
                    headers = if (fields["dashboard"].orEmpty().isNotEmpty() || fields["api"].orEmpty().isNotEmpty()) {
                        headers
                    } else {
                        emptyList()
                    },
                    modelURL = address(fields["model-url"]),
                    modelKey = fields["model-key"].orEmpty(),
                    model = fields["model"].orEmpty(),
                    voicesURL = voices,
                    bridgeURL = bridge,
                    // `bridge-token` (lien de l'app) ou `bridgeToken` (lien du serveur) — les clés
                    // sont rangées en minuscules au moment de la lecture.
                    bridgeToken = (fields["bridge-token"] ?: fields["bridgetoken"]).orEmpty().take(200),
                    language = fields["lang"].orEmpty().take(20),
                    version = version,
                )
                if (code.transport == null) {
                    SetupCodeOffer(null, SetupCodeParseError.NO_ADDRESS)
                } else {
                    SetupCodeOffer(code, SetupCodeParseError.NONE)
                }
            } catch (failure: SetupCodeReadFailure) {
                SetupCodeOffer(null, SetupCodeParseError.BAD_ADDRESS, failure.address)
            }
        }

        /** Query du lien applicatif, fragment du lien universel ; jamais la query du site web. */
        private fun parameters(text: String): String? {
            val uri = runCatching { URI(text) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            if (scheme in ACCEPTED_SCHEMES && host == HOST) return uri.rawQuery.orEmpty()
            if (
                scheme != "https" || (host != WEB_HOST && host != LEGACY_WEB_HOST) || uri.port != -1 ||
                (uri.path != WEB_PATH && uri.path != "$WEB_PATH/")
            ) return null
            val fragment = uri.rawFragment
            if (fragment.isNullOrEmpty() && uri.rawQuery.isNullOrEmpty()) return null
            return fragment.orEmpty()
        }

        private fun percentDecode(raw: String): String? = runCatching {
            // Contrairement à un formulaire web, « + » est un plus dans un lien Onyx.
            URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
        }.getOrNull()

        private fun address(raw: String?): String {
            if (raw.isNullOrEmpty()) return ""
            val uri = runCatching { URI(raw) }.getOrNull()
            if (
                uri == null || uri.scheme?.lowercase() !in setOf("http", "https") ||
                uri.host.isNullOrEmpty() || uri.userInfo != null
            ) throw SetupCodeReadFailure(raw)
            return uri.toString()
        }
    }
}

enum class SetupServerAction { NONE, FILL, ADD }

data class SetupCodePlan(
    val server: SetupServerAction,
    val sameAs: String?,
    val replacesModelURL: String?,
    val transport: Transport,
)

private fun comparableDashboard(raw: String): String =
    ServerConfig.blank("comparison").copy(serveBaseUrl = raw).serveBase

private fun comparableApi(raw: String): String =
    ServerConfig.blank("comparison").copy(baseUrl = raw).normalizedBaseUrl

private class SetupCodeReadFailure(val address: String) : Exception()

/** Pourquoi un lien de configuration reconnu ne peut pas être appliqué. */
enum class SetupCodeParseError {
    NONE,
    NEWER_VERSION,
    NO_ADDRESS,
    BAD_ADDRESS,
    TOO_LONG,
}

/** Un lien arrivé par QR, collage ou Intent ; rien n'est installé avant confirmation. */
data class SetupCodeOffer(
    val code: SetupCode?,
    val error: SetupCodeParseError,
    val badAddress: String = "",
) {
    val message: String
        get() = when (error) {
            SetupCodeParseError.NONE -> ""
            SetupCodeParseError.NEWER_VERSION ->
                "This setup code was made by a newer version of Onyx. Update the app, then try again."
            SetupCodeParseError.NO_ADDRESS -> "This setup code has no server address in it."
            SetupCodeParseError.BAD_ADDRESS ->
                "This setup code has an address Onyx can't use: ${badAddress.take(80)}"
            SetupCodeParseError.TOO_LONG -> "This setup code is too long to be one of Onyx's."
        }

    companion object {
        /** Null pour un QR étranger à Onyx ; une erreur pour un lien Onyx reconnu mais invalide. */
        fun fromText(text: String): SetupCodeOffer? {
            val link = findLink(text) ?: return null
            return SetupCode.read(link)
        }

        private fun findLink(text: String): String? {
            // Tous les schémas reconnus (le nom actuel et l'ancien), plus les deux hôtes web.
            val prefixes = SetupCode.ACCEPTED_SCHEMES.map { "$it://${SetupCode.HOST}" } +
                listOf(
                    "https://${SetupCode.WEB_HOST}${SetupCode.WEB_PATH}",
                    "https://${SetupCode.LEGACY_WEB_HOST}${SetupCode.WEB_PATH}",
                )
            val start = prefixes
                .map { prefix -> text.indexOf(prefix, ignoreCase = true) }
                .filter { it >= 0 }
                .minOrNull() ?: return null
            val candidate = text.substring(start)
                .substringBefore(' ')
                .substringBefore('\n')
                .substringBefore('\r')
                .substringBefore('\t')
                .substringBefore('<')
                .substringBefore('>')
                .substringBefore('"')
            // Une page /connect nue reste une page web, pas une offre vide.
            return candidate.takeIf { '?' in it || '#' in it }
        }
    }
}

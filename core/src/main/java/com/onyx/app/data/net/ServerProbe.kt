package com.onyx.app.data.net

import com.onyx.app.data.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest

/**
 * La réponse à « ce serveur sait-il faire ça ? » — trois valeurs, jamais une exception.
 *
 * [UNKNOWN] **se comporte toujours comme [NO]** : c'est la règle de la maison, portée par
 * [isUsable]. Un serveur muet, un statut ambigu ou une sonde annulée ne valent pas un oui ; l'écran
 * qui s'appuie dessus se dégrade alors exactement comme devant un refus franc.
 */
enum class Support {
    YES,
    NO,
    UNKNOWN,
    ;

    /** Seul [YES] ouvre une fonctionnalité. [UNKNOWN] est un non, par construction. */
    val isUsable: Boolean get() = this == YES

    companion object {
        /** Lecture tolérante d'un nom gravé sur disque ; ce qui est illisible vaut [UNKNOWN]. */
        fun fromName(raw: String?): Support =
            entries.firstOrNull { it.name.equals(raw.orEmpty(), ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * Les capacités que la sonde sait interroger, et **la route courte** qui répond pour chacune.
 *
 * Cette liste est la déclaration du lot : une capacité n'entre ici que le jour où une route `GET`
 * idempotente et bon marché existe pour la constater. Tant que le serveur ne publie pas la route,
 * la sonde rend [Support.NO] — c'est exactement ce qu'on veut d'un serveur plus ancien.
 *
 * Les chemins sont relatifs à l'adresse du Dashboard, comme les autres routes de la maison
 * (`ServeClient.request` les ajoute à la base).
 */
enum class ServerCapability(val route: String) {
    /**
     * Le coffre : la route de **santé** du plugin `onyx-vault` — installée, elle répond et dit si
     * la clé de chiffrement est là ; absente, le serveur rend 404.
     *
     * Le chemin est celui du plugin réel, pas un nom d'attente : une sonde qui interroge
     * `api/vault` sur un serveur où le coffre est installé répondrait « absent » et l'écran
     * mentirait. Vérifié sur l'installation : `/api/plugins/onyx-vault/health`.
     */
    VAULT("api/plugins/onyx-vault/health"),

    /**
     * **Le navigateur de l'agent** — le plugin `onyx-browser`, qui n'est pas un plugin de Dashboard :
     * il tient son propre serveur, relayé par `onyx-one` sous `/onyx-browser/`. Sa route de santé
     * répond sur la **même origine** que le reste, donc la sonde n'a rien de nouveau à savoir.
     */
    BROWSER("onyx-browser/health"),

    /**
     * L'enregistrement d'une démonstration — second plugin (`onyx-record`), décision des specs du
     * lot 3 : un journal relisible n'entre pas dans le coffre.
     */
    RECORD("api/plugins/onyx-record/health"),

    /**
     * La passation d'une room : notion **du core** Hermes (specs du lot 5), pas d'un plugin — la
     * route définitive viendra avec `groups.lead` / `groups.handoff`.
     */
    HANDOFF("api/handoff"),
}

/**
 * Une sonde par couple (**identifiant de serveur**, [ServerConfig.configSignature]) et par capacité.
 *
 * Le défaut qu'elle corrige : l'app découvrait l'absence d'une route **en échouant** — un serveur
 * ancien produisait un écran vide là où il fallait simplement se dégrader. Ici, on demande d'abord ;
 * la réponse est un [Support], jamais une exception.
 *
 * Trois règles tenues par ce fichier :
 *
 * 1. **Jamais sur le chemin critique.** [supports] est `suspend` et fait au plus une requête `GET`
 *    courte ([DEFAULT_TIMEOUT_SECONDS] secondes). Aucun écran ne l'attend au moment de s'ouvrir :
 *    l'appelant l'interroge depuis une portée de fond, et la réponse est déjà là au coup suivant.
 * 2. **Jamais d'exception qui remonte.** Toute panne — réseau, statut, JSON, annulation — devient
 *    un [Support]. Un écran ne peut pas échouer à cause d'une sonde.
 * 3. **Une seule requête par couple.** Le résultat est gardé en mémoire dans [ProbeCache] (et,
 *    quand un fichier lui est donné, sur disque avec expiration). Corriger l'adresse ou le mot de
 *    passe change la signature : la sonde repart, parce que ce n'est plus le même serveur.
 *
 * Rien ici ne touche à un écran : ce lot pose la brique, il ne branche aucune décision.
 */
class ServerProbe(
    private val config: ServerConfig,
    private val client: ServeClient = ServeClient(config),
    private val cache: ProbeCache = processCache,
    private val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
) {

    /** Les capacités que cette brique sait interroger, dans l'ordre de la déclaration. */
    fun declared(): List<ServerCapability> = ServerCapability.entries

    /**
     * La réponse pour une capacité : le cache d'abord, une requête `GET` courte ensuite.
     *
     * Sans identifiants, il n'y a rien à demander : [Support.UNKNOWN] (donc un non), sans réseau.
     * Une capacité inconnue du serveur (`404`, `405`, `501`) vaut [Support.NO]. Tout ce qui ne
     * prouve ni présence ni absence (401, 403, 5xx, JSON illisible après un 200, annulation) reste
     * [Support.UNKNOWN] et **n'est pas gravé** : la prochaine ouverture redemandera.
     */
    suspend fun supports(capability: ServerCapability): Support {
        if (!config.hasServeCredentials) return Support.UNKNOWN
        val key = keyFor(config, capability)
        cache.read(key)?.let { return it }

        val answer = withContext(Dispatchers.IO) { ask(capability) }
        if (answer == Support.YES || answer == Support.NO) cache.write(key, answer)
        return answer
    }

    /**
     * Vrai seulement si le serveur sait faire — la lecture que les écrans emploieront.
     *
     * C'est le point unique où [Support.UNKNOWN] se comporte comme [Support.NO] : `false`.
     */
    suspend fun allows(capability: ServerCapability): Boolean = supports(capability).isUsable

    /** Toutes les capacités déclarées, sondées une fois chacune. */
    suspend fun snapshot(): Map<ServerCapability, Support> =
        declared().associateWith { supports(it) }

    /** L'unique requête ; elle ne lève pas. */
    private suspend fun ask(capability: ServerCapability): Support = try {
        // Un GET nu : la route doit exister et répondre, on ne lit pas son corps.
        client.request(capability.route, method = "GET", timeoutSeconds = timeoutSeconds)
        Support.YES
    } catch (error: TransportError) {
        classify(error)
    } catch (error: Throwable) {
        // Une annulation ou une panne inattendue : on ne casse pas l'écran qui a posé la question.
        Support.UNKNOWN
    }

    /**
     * Ce que le statut (ou la panne) prouve.
     *
     * Un **délai dépassé** vaut [Support.NO] : une route qui ne répond pas dans le temps court
     * d'une sonde n'est pas une route sur laquelle un écran peut compter. Un `404` franc vaut
     * [Support.NO] aussi — c'est la réponse d'un serveur plus ancien, celle qu'on veut lire.
     */
    private fun classify(error: TransportError): Support = when (error) {
        is TransportError.Http -> when {
            // La route n'est pas là : c'est la dégradation attendue.
            error.status == 404 || error.status == 405 || error.status == 501 -> Support.NO
            // La route peut exister mais on ne peut pas le prouver : on ne conclut pas.
            error.status == 401 || error.status == 403 -> Support.UNKNOWN
            // Serveur en peine (ou trop sollicité) : son état ne dit rien de la route.
            error.status in 500..599 || error.status == 408 || error.status == 429 -> Support.UNKNOWN
            else -> Support.NO
        }
        // Le temps court est dépassé : rien de prouvé, donc pas un oui.
        is TransportError.Timeout -> Support.NO
        // Le serveur n'a jamais été joint : ce n'est pas une absence de route.
        is TransportError.Unreachable, is TransportError.AddressNotFound -> Support.NO
        // Un 2xx dont le corps n'est pas du JSON : la route a bien répondu.
        is TransportError.Malformed -> Support.YES
        // Sans identifiants utilisables, la question n'a pas été posée.
        is TransportError.BadUrl, is TransportError.MissingApiKey,
        is TransportError.SignInRequired -> Support.UNKNOWN
        is TransportError.Cancelled -> Support.UNKNOWN
        else -> Support.UNKNOWN
    }

    companion object {
        /** Le temps d'une sonde : court, parce qu'un écran ne l'attend pas. */
        const val DEFAULT_TIMEOUT_SECONDS: Long = 3L

        /** Le cache de tout le processus : deux écrans qui demandent la même capacité ne sondent qu'une fois. */
        private val processCache = ProbeCache()

        /**
         * La clé du cache : **identifiant** et **signature de configuration**, puis la capacité.
         *
         * La signature est réduite à une empreinte : le fichier du cache n'a pas à recopier une
         * adresse ni un compte. `internal` pour que le test tienne exactement la règle du couple.
         */
        internal fun keyFor(config: ServerConfig, capability: ServerCapability): String =
            "${config.id.take(64)}|${fingerprint(config.configSignature)}|${capability.name}"

        /** Les 16 premiers hexadécimaux du SHA-256 : stable, court, sans collision pratique ici. */
        private fun fingerprint(signature: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray(Charsets.UTF_8))
                .take(8)
                .joinToString("") { byte -> "%02x".format(byte) }
    }
}

/**
 * Le cache des réponses : mémoire d'abord, disque quand un fichier est donné, avec expiration.
 *
 * **Pourquoi une expiration.** Un serveur qu'on met à jour, une route qui apparaît : une réponse
 * gravée pour toujours laisserait l'app croire l'ancien serveur. Quinze minutes suffisent à éviter
 * les sondes en rafale sans figer une capacité absente.
 *
 * **Ce qui n'est jamais gravé.** [Support.UNKNOWN] ne l'est pas : une réponse indécise n'est pas
 * une réponse, et la prochaine ouverture doit pouvoir redemander.
 *
 * Aucune E/S ne lève : un disque plein, un fichier corrompu ou un dossier absent rendent le cache
 * mémoire, pas une panne. `@Synchronized` parce que plusieurs écrans peuvent demander en même temps.
 */
class ProbeCache(
    private val file: File? = null,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val support: Support, val atMillis: Long)

    private val entries = mutableMapOf<String, Entry>()
    private var loaded = false

    /** La réponse gardée pour cette clé, ou `null` si absente ou périmée. */
    @Synchronized
    fun read(key: String): Support? {
        load()
        val entry = entries[key] ?: return null
        if (clock() - entry.atMillis > maxAgeMillis) {
            entries.remove(key)
            return null
        }
        return entry.support
    }

    /** Garde une réponse concluante. [Support.UNKNOWN] est ignoré. */
    @Synchronized
    fun write(key: String, support: Support) {
        if (support == Support.UNKNOWN) return
        load()
        entries[key] = Entry(support, clock())
        save()
    }

    /** Oublie une réponse (le serveur a changé de version, l'utilisateur redemande). */
    @Synchronized
    fun forget(key: String) {
        load()
        entries.remove(key)
        save()
    }

    /** Lit le fichier une seule fois, et seulement s'il y en a un. */
    private fun load() {
        if (loaded) return
        loaded = true
        val target = file ?: return
        val text = runCatching { if (target.isFile) target.readText() else "" }.getOrNull().orEmpty()
        if (text.isBlank()) return
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val stored = root["entries"] as? JsonObject ?: return
        stored.forEach { (key, raw) ->
            val entry = raw as? JsonObject ?: return@forEach
            val support = Support.fromName((entry["support"] as? JsonPrimitive)?.contentOrNull)
            if (support == Support.UNKNOWN) return@forEach
            val at = (entry["at"] as? JsonPrimitive)?.longOrNull ?: return@forEach
            entries[key] = Entry(support, at)
        }
    }

    /** Écrit le fichier — remplacement par un temporaire, pour ne jamais laisser un JSON tronqué. */
    private fun save() {
        val target = file ?: return
        runCatching {
            val now = clock()
            entries.entries.removeAll { now - it.value.atMillis > maxAgeMillis }
            val payload = buildJsonObject {
                put("entries", buildJsonObject {
                    entries.forEach { (key, entry) ->
                        put(key, buildJsonObject {
                            put("support", entry.support.name)
                            put("at", entry.atMillis)
                        })
                    }
                })
            }
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, target.name + ".tmp")
            temporary.writeText(json.encodeToString(JsonObject.serializer(), payload))
            if (!temporary.renameTo(target)) {
                target.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }

    companion object {
        /** Quinze minutes : le temps qu'un serveur change de version, sans sondes en rafale. */
        const val DEFAULT_MAX_AGE_MILLIS: Long = 15 * 60 * 1000L

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

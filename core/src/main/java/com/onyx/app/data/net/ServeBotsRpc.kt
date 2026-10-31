package com.onyx.app.data.net

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.localization.OnyxLocalization

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Les écritures d'un bot, par le RPC du Dashboard.
 *
 * Miroir de `ServerBots.setFace` / `configure` de la référence : `profiles.configure` avec le `ui_meta`
 * du profil et la révision qu'on a lue — si quelqu'un l'a changé entre-temps, Hermes refuse et
 * l'app le dit.
 */
class ServeBotsRpc(private val rpc: RpcChannel) {

    constructor(rpc: ServeRpc) : this(rpc.asChannel())

    /** Le roster complet de `profiles.list`, avec `canonical_session`, aperçu et dernière activité. */
    suspend fun profiles(): List<BotProfile> {
        val payload = rpc.call("profiles.list", buildJsonObject { }, LIST_TIMEOUT).asObject()
        val rows = payload?.array("profiles") ?: payload?.array("data") ?: emptyList()
        return rows.mapNotNull { element -> element.asObject()?.let(::botProfileFromRow) }
    }

    /**
     * La photo qu'un bot a dans Hermes Desktop (`profiles.get_asset`, `avatar`), ou rien.
     *
     * Miroir de `ServerBots.botPicture` côté référence : le serveur rend le fichier en `data:` base64.
     */
    suspend fun botPicture(profile: String): ByteArray? {
        if (profile.isBlank()) return null
        val params = buildJsonObject {
            put("name", profile)
            put("asset", "avatar")
        }
        val result = rpc.call("profiles.get_asset", params, timeoutMillis = 30_000).asObject() ?: return null
        if (result["found"].asBool() != true) return null
        val url = result["data"].asString() ?: return null
        val marker = url.indexOf(";base64,")
        if (marker < 0) return null
        return runCatching {
            android.util.Base64.decode(url.substring(marker + ";base64,".length), android.util.Base64.DEFAULT)
        }.getOrNull()
    }

    /**
     * `profiles.describe` — les jeux d'outils du serveur, ceux que ce profil a, et s'il tient sa
     * propre liste.
     *
     * Miroir de `HermesServeClient.toolsetsOffered` / `botDetails` (`BotDetails` de
     * BotManagement) : la liste vient de `profiles.describe`, jamais des routes REST.
     */
    suspend fun describeToolsets(profile: String): BotToolsets {
        val payload = rpc.call(
            "profiles.describe",
            buildJsonObject { put("name", profile) },
            timeoutMillis = 30_000,
        ).asObject()
        return botToolsetsFrom(payload)
    }

    /**
     * `profiles.configure { enabled_toolsets }` — les jeux d'outils qu'un bot a désormais.
     *
     * Miroir de `BotChange.toolsets` de la référence : jamais une liste vide (chaque version du serveur
     * l'interprète autrement), et l'écriture est refusée quand le profil a disparu.
     */
    suspend fun setToolsets(profile: String, names: List<String>) {
        if (names.isEmpty()) return
        rpc.call("profiles.configure", toolsetsConfigureParams(profile, names), timeoutMillis = 30_000)
    }

    /**
     * « Let bots message each other » : le profil principal est installé comme un bot du serveur,
     * ce qui donne à chaque chat de bot l'outil pour écrire aux autres.
     *
     * Miroir de `ServerBots.letBotsMessageEachOther` (ServerBots:492) : c'est un
     * `profiles.configure` sur le profil principal, avec la révision qu'on vient de lire ; changé
     * entre-temps (Hermes Desktop), c'est refusé — et allumé de toute façon.
     */
    suspend fun letBotsMessageEachOther(mainProfile: String) {
        val payload = rpc.call(
            "profiles.list",
            buildJsonObject { put("include_sessions", false) },
            timeoutMillis = 30_000,
        ).asObject()
        val row = payload?.array("profiles")
            ?.mapNotNull { it.asObject() }
            ?.firstOrNull { it.firstString("name") == mainProfile }
            ?: throw TransportError.Malformed(OnyxLocalization.format("%1\$s isn't on your server any more.", mainProfile))
        val revision = row.obj("ui_meta_revisions")?.int("hermes-bots") ?: 0
        val result = rpc.call(
            "profiles.configure",
            letBotsMessageParams(mainProfile, revision),
            timeoutMillis = 30_000,
        ).asObject()
        val applied = result?.obj("applied")
        // Réglé entre-temps (Hermes Desktop) : c'est allumé de toute façon.
        if (applied?.get("ui_meta_conflicts") != null) return
        if (applied?.get("ui_meta").asBool() != true) {
            throw TransportError.Malformed(OnyxLocalization["Hermes did not keep the bot setting."])
        }
    }

    /** Donne son visage à un bot, ou celui que son nom lui donne (`null`). */
    suspend fun setFace(profile: String, faceCode: String?, revision: Int) {
        val value = faceCode?.let { buildJsonObject { put("face", it) } } ?: JsonNull
        configure(profile, "onyx", value, revision)
    }

    /**
     * Fait un bot neuf : `profiles.create`, puis son visage, son titre et sa visibilité.
     *
     * Miroir du « Create » de la référence : le profil naît sur le serveur en **copie de la
     * configuration de l'agent principal** (`clone_from`, comme le pied de section de l'écran le
     * promet — ses réglages, ses clés et ses connexions, ses chats et ses mémoires à lui), avec la
     * personnalité du modèle choisi écrite dans son `SOUL.md`. Le `ui_meta` — visage, titre,
     * visibilité — se pose dans le même geste, exactement comme pour un bot qui existe déjà.
     *
     * Rend le nom du profil créé.
     */
    suspend fun createBot(
        profile: String,
        title: String,
        about: String,
        soul: String,
        faceCode: String?,
        cloneFrom: String? = null,
        hidden: Boolean = false,
    ): String {
        val created = rpc.call(
            "profiles.create",
            buildJsonObject {
                put("name", profile)
                if (about.isNotBlank()) put("description", about)
                if (soul.isNotBlank()) put("soul", soul)
                if (!cloneFrom.isNullOrBlank()) put("clone_from", cloneFrom)
            },
            CALL_TIMEOUT,
        ).asObject()
        if (created?.get("ok").asBool() != true) {
            throw TransportError.Malformed(OnyxLocalization["Hermes did not make the bot."])
        }
        // Un profil tout neuf n'a rien à réviser : les deux clés du `ui_meta` partent ensemble,
        // celles de `setFace`, `setAbout` et `setHidden` réunies.
        val result = rpc.call(
            "profiles.configure",
            buildJsonObject {
                put("name", profile)
                put(
                    "ui_meta",
                    buildJsonObject {
                        put(
                            "hermes-bots",
                            buildJsonObject {
                                if (title.isNotBlank()) put("title", title)
                                put("hidden", hidden)
                            },
                        )
                        put("onyx", faceCode?.let { code -> buildJsonObject { put("face", code) } } ?: JsonNull)
                    },
                )
                put("ui_meta_expected_revisions", buildJsonObject { put("hermes-bots", 0); put("onyx", 0) })
            },
            CALL_TIMEOUT,
        ).asObject()
        val applied = result?.obj("applied")
        if (applied?.get("ui_meta_conflicts") != null) {
            throw TransportError.Malformed(OnyxLocalization["This bot was just changed elsewhere. Review it and choose again."])
        }
        if (applied?.get("ui_meta").asBool() != true) {
            throw TransportError.Malformed(OnyxLocalization["Hermes did not keep the bot setting."])
        }
        return profile
    }

    /**
     * Change le modèle d'un bot ; Hermes le lit sur le profil, pas dans son `ui_meta`.
     * Le fournisseur accompagne le modèle : sans lui, un endpoint non standard est ambigu.
     */
    suspend fun setModel(profile: String, model: String?, provider: String? = null) {
        val params = buildJsonObject {
            put("name", profile)
            if (model == null) put("model", JsonNull) else put("model", model)
            if (!provider.isNullOrBlank()) put("provider", provider)
        }
        rpc.call("profiles.configure", params, CALL_TIMEOUT)
    }

    /**
     * Change ce qu'un bot affiche : son titre dans son `ui_meta`, sa phrase sur son profil.
     *
     * Le `ui_meta` d'Hermes ne porte que le titre et la visibilité ; la description est un champ du
     * profil, et l'y mettre serait ignoré.
     */
    suspend fun setAbout(profile: String, title: String, about: String, revision: Int) {
        if (title.isNotBlank()) {
            configure(
                profile = profile,
                key = "hermes-bots",
                value = buildJsonObject { put("title", title) },
                revision = revision,
            )
        }
        if (about.isNotBlank()) {
            rpc.call(
                "profiles.configure",
                buildJsonObject {
                    put("name", profile)
                    put("description", about)
                },
                CALL_TIMEOUT,
            )
        }
    }

    /**
     * Cache un bot dans Hermes Desktop (`ui_meta["hermes-bots"].hidden`), ou le remontre.
     *
     * Miroir de `changeBot(_:BotChange(hidden:))` de la référence : c'est le réglage du Desktop, pas celui
     * de Onyx — un bot caché doit disparaître de la liste sur **tous** les appareils, comme la
     * tuile « Hidden » le promet. La révision attendue accompagne l'écriture : Hermes refuse un
     * `ui_meta` que quelqu'un d'autre a changé entre-temps.
     */
    suspend fun setHidden(profile: String, hidden: Boolean, revision: Int = 0) {
        configure(
            profile = profile,
            key = "hermes-bots",
            value = buildJsonObject { put("hidden", hidden) },
            revision = revision,
        )
    }

    /**
     * Épingle un bot en haut de la liste (`ui_meta["hermes-bots"].pinned`), ou le détache.
     *
     * Miroir du `setPinned` de la référence (`BotsViewModel.setPinned` → `changeMeta(bot, "pinned", …)`)
     * : c'est le même bloc `ui_meta` que la visibilité, donc le même geste, la même révision
     * attendue, et Hermes Desktop le voit comme il voit `hidden`.
     */
    suspend fun setPinned(profile: String, pinned: Boolean, revision: Int = 0) {
        configure(
            profile = profile,
            key = "hermes-bots",
            value = buildJsonObject { put("pinned", pinned) },
            revision = revision,
        )
    }

    private suspend fun configure(profile: String, key: String, value: kotlinx.serialization.json.JsonElement, revision: Int) {
        val params = buildJsonObject {
            put("name", profile)
            put("ui_meta", buildJsonObject { put(key, value) })
            put("ui_meta_expected_revisions", buildJsonObject { put(key, revision) })
        }
        val result = rpc.call("profiles.configure", params, CALL_TIMEOUT).asObject()
        val applied = result?.obj("applied")
        if (applied?.get("ui_meta_conflicts") != null) {
            throw TransportError.Malformed(OnyxLocalization["This bot was just changed elsewhere. Review it and choose again."])
        }
        if (applied?.get("ui_meta").asBool() != true) {
            throw TransportError.Malformed(OnyxLocalization["Hermes did not keep the bot setting."])
        }
    }

    companion object {
        const val LIST_TIMEOUT = 30_000L
        const val CALL_TIMEOUT = 60_000L

        fun from(base: ServeRpc): ServeBotsRpc = ServeBotsRpc(base)
    }
}

/**
 * Les jeux d'outils d'un profil, tels que `profiles.describe` les rend (`BotDetails` côté référence).
 */
data class BotToolsets(
    /** Tous les jeux que le serveur propose, chacun marqué s'il est dans la liste du profil. */
    val offered: List<GatewayToolset> = emptyList(),
    /** Ceux que le profil a. Un profil sans liste propre et sans case cochée les a tous. */
    val enabled: Set<String> = emptySet(),
    /** Le profil tient une liste à lui (sinon il suit ceux du principal). */
    val pinned: Boolean = false,
)

/**
 * `profiles.describe` lu comme `BotDetails.init` (BotManagement:53) : `toolsets` avec leur
 * `enabled`, et `toolsets_pinned`. `enabledToolsets` de la référence est repris tel quel — sans liste propre
 * et sans aucun jeu coché, les versions 0.21.5 du serveur répondent une liste vide alors que le
 * profil a tout : on lit donc « tous ».
 */
fun botToolsetsFrom(payload: JsonObject?): BotToolsets {
    val rows = payload?.array("toolsets").orEmpty().mapNotNull { it.asObject() }
    val offered = rows.mapNotNull { row ->
        val name = row.firstString("name") ?: return@mapNotNull null
        GatewayToolset(
            name = name,
            label = toolsetTitleFor(name),
            description = row.strOrNull("description"),
            enabled = row["enabled"].asBool() ?: true,
            tools = row.stringList("tools"),
        )
    }
    val pinned = payload?.get("toolsets_pinned").asBool() ?: false
    val on = rows.filter { it["enabled"].asBool() ?: true }
        .mapNotNull { it.firstString("name") }
        .toSet()
    return BotToolsets(
        offered = offered,
        enabled = if (!pinned && on.isEmpty()) offered.map { it.name }.toSet() else on,
        pinned = pinned,
    )
}

/** `profiles.configure { name, enabled_toolsets }` — ce qu'un bot a désormais. */
fun toolsetsConfigureParams(profile: String, names: List<String>): JsonObject =
    buildJsonObject {
        put("name", profile)
        put("enabled_toolsets", buildJsonArray { names.sorted().forEach { name -> add(JsonPrimitive(name)) } })
    }

/** `profiles.configure` qui installe le profil principal comme bot (voir `letBotsMessageEachOther`). */
fun letBotsMessageParams(profile: String, revision: Int): JsonObject =
    buildJsonObject {
        put("name", profile)
        put("ui_meta", buildJsonObject { put("hermes-bots", buildJsonObject { }) })
        put("ui_meta_expected_revisions", buildJsonObject { put("hermes-bots", revision) })
    }

/** Le titre qu'une personne lit pour un jeu d'outils, comme `BotToolset.title` de la référence. */
fun toolsetTitleFor(name: String): String = TOOLSET_TITLES[name]
    ?: name.replace('_', ' ').split(' ')
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

/** Les mots de la référence (`BotToolset.titles`) : ce que Onyx dit d'un jeu d'outils. */
private val TOOLSET_TITLES = mapOf(
    "web" to "Web search", "browser" to "Browser", "terminal" to "Terminal", "file" to "Files",
    "code_execution" to "Run code", "vision" to "Look at pictures", "image_gen" to "Make pictures",
    "tts" to "Speak aloud", "stt" to "Transcribe audio", "skills" to "Skills", "todo" to "To-do list",
    "memory" to "Memory", "context_engine" to "Context engine", "session_search" to "Search past chats",
    "connections" to "Connections", "clarify" to "Ask you questions", "delegation" to "Hand off tasks",
    "cronjob" to "Schedules", "computer_use" to "Use the computer", "homeassistant" to "Home Assistant",
    "kanban" to "Kanban", "video" to "Watch video", "video_gen" to "Make video", "x_search" to "Search X",
    "search" to "Search", "spotify" to "Spotify",
)

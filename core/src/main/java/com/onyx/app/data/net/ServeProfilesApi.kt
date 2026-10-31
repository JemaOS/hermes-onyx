package com.onyx.app.data.net

import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.localization.OnyxLocalization
import kotlinx.serialization.json.JsonObject

/**
 * Les bots du serveur : les profils Hermes, avec leur `ui_meta`.
 *
 * Miroir de `BotRoster` côté référence (Echo/Services/Transport/ServerBots:101) : la même route
 * (`GET api/profiles`), les mêmes champs facultatifs, et les profils cachés laissés de côté.
 */
class ServeProfilesApi(private val client: ServeClient) {

    /** `GET api/profiles` — tous les profils, bots compris. */
    suspend fun profiles(): List<BotProfile> {
        val payload = client.request("api/profiles").asObject()
        val rows = payload?.array("profiles") ?: payload?.array("data") ?: emptyList()
        return rows.mapNotNull { element -> element.asObject()?.let(::botProfileFromRow) }
    }

    /** Les bots montrables : les profils cachés ne le sont pas, comme sur la référence. */
    suspend fun visible(mainName: String = "Onyx"): List<BotProfile> = profiles().visibleBotProfiles(mainName)

    /**
     * `DELETE api/profiles/{name}` — supprime le profil, et avec lui ses chats, ses mémoires et
     * ses réglages.
     *
     * Miroir de `HermesServeClient.deleteProfile` de la référence (BotManagement:244) : c'est une
     * route REST, Hermes n'ayant **aucun** appel socket pour supprimer un profil. Le profil
     * principal est refusé ici : c'est l'agent de la personne, pas un bot.
     */
    suspend fun deleteProfile(name: String) {
        if (name.isBlank() || name.equals("default", ignoreCase = true)) {
            throw TransportError.Malformed(OnyxLocalization["The main profile can't be deleted."])
        }
        // Le nom est passé tel quel : OkHttp encode le segment de chemin lui-même (un espace
        // devient `%20`, comme le `addingPercentEncoding(.urlPathAllowed)` de la référence). L'encoder
        // avant le donnerait `%2520`.
        client.delete("api/profiles/$name", timeoutSeconds = 45)
    }
}

/** Une ligne de `profiles.list` ou de `GET api/profiles`, avec la date Unix en millisecondes. */
internal fun botProfileFromRow(row: JsonObject): BotProfile? {
    val name = row.firstString("name") ?: return null
    val meta = row.obj("ui_meta")?.obj("hermes-bots")
    val chat = row.obj("canonical_session")
    val active = chat?.get("last_active").asString()?.toDoubleOrNull()?.takeIf { it > 0 }
    return BotProfile(
        name = name,
        displayName = row.strOrNull("display_name"),
        title = meta?.strOrNull("title"),
        description = row.str("description"),
        isDefault = row["is_default"].asBool() ?: (name == "default"),
        model = row.strOrNull("model"),
        isHidden = meta?.get("hidden").asBool() ?: false,
        // « Pin to top » de la référence : le même `ui_meta` que la visibilité, la même lecture.
        pinned = meta?.get("pinned").asBool() ?: false,
        setUpRevision = row.obj("ui_meta_revisions")?.int("hermes-bots") ?: 0,
        setUp = meta != null,
        chatId = chat?.firstString("resolved_id", "id"),
        preview = chat?.strOrNull("preview"),
        lastActive = active?.let { if (it < 100_000_000_000.0) (it * 1000).toLong() else it.toLong() },
        hasPicture = row["has_avatar"].asBool() ?: false,
        faceCode = row.obj("ui_meta")?.obj("onyx")?.strOrNull("face"),
        previousNames = row.array("previous_names").mapNotNull { it.asString() },
        workerActiveMillis = row.obj("worker_session")?.get("last_active").asString()?.toDoubleOrNull()
            ?.takeIf { it > 0 }
            ?.let { if (it < 100_000_000_000.0) (it * 1000).toLong() else it.toLong() },
    )
}

/** Le tri et la visibilité de `BotRoster.listed`, partagés par REST et RPC. */
fun List<BotProfile>.visibleBotProfiles(mainName: String): List<BotProfile> =
    filterNot { it.isHidden }
        .sortedWith(
            compareByDescending<BotProfile> { it.isDefault }
                .thenByDescending { it.lastActive ?: Long.MIN_VALUE }
                .thenBy { it.name },
        )

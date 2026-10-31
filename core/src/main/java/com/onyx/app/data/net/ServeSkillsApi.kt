package com.onyx.app.data.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Une compétence du gateway (`DashboardSkill`). */
data class DashboardSkill(
    val name: String,
    val description: String = "",
    val category: String? = null,
    val enabled: Boolean = true,
    val alwaysLoaded: Boolean = false,
    /**
     * D'où vient la compétence, tel que `GET api/skills` le rend : `hub` (installée du catalogue
     * officiel), `agent` (écrite par l'agent lui-même), `external` (déposée hors de l'app).
     *
     * LOT organisation-fil-competences : le champ était rendu par le serveur mais **jeté** au
     * décodage ; il porte le badge de provenance de l'écran Compétences.
     */
    val provenance: String? = null,
)

/** Une compétence du **catalogue officiel** (`GET api/skills/hub/official`). */
data class CatalogSkill(
    val identifier: String,
    val name: String,
    val description: String = "",
    /** La famille, tirée de l'identifiant par le serveur (`official/<famille>/<compétence>`). */
    val category: String = "general",
    /** Vraie quand le profil l'a déjà installée. */
    val installed: Boolean = false,
    /** `builtin`, `trusted` ou `community` — ce que le serveur dit de la source. */
    val trustLevel: String = "",
)

/**
 * Les compétences (SKILL.md) lues et écrites par le Dashboard.
 *
 * Miroir des routes de `HermesServeClient` : `GET api/skills`, `GET api/skills/content`,
 * `POST api/skills`, `PUT api/skills/content`, et — pour l'éditeur d'un bot —
 * `PUT api/skills/toggle`, `GET api/config?include_defaults=false` et `PUT api/config`
 * (`skills.auto_load`). `profile` nomme le profil visé (celui d'un bot) : c'est le `?profile=` du
 * `HermesServeClient.naming` de la référence.
 */
class ServeSkillsApi(private val client: ServeClient) {

    /** `GET api/skills` — la liste, avec ce que le serveur sait de chacune. */
    suspend fun skills(profile: String? = null): List<DashboardSkill> {
        val payload = client.request("api/skills", query = profileQuery(profile))
        val rows = payload.asObject()?.array("skills") ?: payload.asArray()
        return rows.orEmpty().mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val name = obj.firstString("name") ?: return@mapNotNull null
            DashboardSkill(
                name = name,
                description = obj.str("description"),
                category = obj.strOrNull("category"),
                enabled = obj["enabled"].asBool() ?: true,
                alwaysLoaded = obj["always_loaded"].asBool()
                    ?: obj["alwaysLoaded"].asBool()
                    ?: false,
                provenance = obj.strOrNull("provenance"),
            )
        }
    }

    /** `GET api/skills/content?name=…` — le SKILL.md. */
    suspend fun content(name: String): String {
        val payload = client.request("api/skills/content", query = profileQuery(null) + mapOf("name" to name))
        return payload.asObject()?.str("content") ?: payload.asString().orEmpty()
    }

    /** `POST api/skills` — une nouvelle compétence. */
    suspend fun create(name: String, content: String, category: String?) {
        client.request(
            path = "api/skills",
            method = "POST",
            body = buildJsonObject {
                put("name", name)
                put("content", content)
                if (!category.isNullOrBlank()) put("category", category)
            },
        )
    }

    /** `PUT api/skills/content` — le contenu d'une compétence existante. */
    suspend fun saveContent(name: String, content: String) {
        client.request(
            path = "api/skills/content",
            method = "PUT",
            body = buildJsonObject {
                put("name", name)
                put("content", content)
            },
        )
    }

    /**
     * `PUT api/skills/toggle` — une compétence allumée ou éteinte pour ce profil seul.
     *
     * Miroir de `HermesServeClient.toggleSkill(name:enabled:profile:)` : éteinte, l'agent ne la
     * voit plus listée et ne peut plus la lire.
     */
    suspend fun toggle(name: String, enabled: Boolean, profile: String? = null): JsonElement =
        client.request(
            path = "api/skills/toggle",
            method = "PUT",
            query = profileQuery(profile),
            body = buildJsonObject {
                put("name", name)
                put("enabled", enabled)
            },
        )

    /**
     * `GET api/config?include_defaults=false` — les compétences que ce profil lit en entier au
     * début de chaque message (`skills.auto_load` de sa configuration).
     */
    suspend fun alwaysLoaded(profile: String): List<String> {
        val payload = client.request(
            "api/config",
            query = mapOf("include_defaults" to "false") + profileQuery(profile),
        )
        return autoLoadFrom(payload)
    }

    /**
     * `PUT api/config` — la liste des compétences toujours lues.
     *
     * Le Dashboard fusionne ce qu'on lui envoie dans la configuration du profil, et une liste
     * remplace celle qui était là : rien d'autre ne change (la route de la référence `setAlwaysLoadedSkills`).
     */
    suspend fun setAlwaysLoaded(names: List<String>, profile: String): JsonElement =
        client.request(
            path = "api/config",
            method = "PUT",
            query = profileQuery(profile),
            body = buildJsonObject {
                put(
                    "config",
                    buildJsonObject {
                        put(
                            "skills",
                            buildJsonObject {
                                put("auto_load", buildJsonArray { names.forEach { name -> add(JsonPrimitive(name)) } })
                            },
                        )
                    },
                )
            },
        )

    /**
     * `GET api/skills/hub/official` — **tout** le catalogue officiel, la marque « installée »
     * posée pour ce profil.
     *
     * C'est la source du bouton « Parcourir le catalogue » : la liste locale du serveur, pas un
     * index distant. Un serveur qui ne sert pas la route rend une liste vide (404) — l'écran dit
     * alors que le catalogue n'est pas disponible, il n'invente aucune entrée.
     */
    suspend fun catalog(profile: String? = null): List<CatalogSkill> {
        val payload = client.request(
            "api/skills/hub/official",
            query = profileQuery(profile),
        )
        val rows = payload.asObject()?.array("skills") ?: payload.asArray()
        return rows.orEmpty().mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val name = obj.firstString("name") ?: return@mapNotNull null
            CatalogSkill(
                identifier = obj.str("identifier").ifEmpty { name },
                name = name,
                description = obj.str("description"),
                category = obj.str("category").ifEmpty { "general" },
                installed = obj["installed"].asBool() ?: false,
                trustLevel = obj.str("trust_level"),
            )
        }
    }

    /** `POST api/skills/hub/install` — installe une entrée du catalogue pour ce profil. */
    suspend fun installFromCatalog(identifier: String, profile: String? = null): JsonElement =
        client.request(
            path = "api/skills/hub/install",
            method = "POST",
            query = profileQuery(profile),
            body = buildJsonObject { put("identifier", identifier) },
        )

    /** `POST api/skills/hub/uninstall` — retire une compétence installée depuis le hub. */
    suspend fun uninstallFromCatalog(name: String, profile: String? = null): JsonElement =
        client.request(
            path = "api/skills/hub/uninstall",
            method = "POST",
            query = profileQuery(profile),
            body = buildJsonObject { put("name", name) },
        )

    private fun profileQuery(profile: String?): Map<String, String> {
        // Un profil nommé pour cet appel (celui d'un bot), sinon celui du serveur choisi.
        val named = profile?.takeIf { it.isNotBlank() } ?: client.profile
        return named?.takeIf { it.isNotBlank() && !it.equals("default", true) }
            ?.let { mapOf("profile" to it) }
            ?: emptyMap()
    }
}

/**
 * `skills.auto_load` d'un `api/config` : les noms de compétences, et rien d'autre.
 *
 * Un nombre ou un `null` dans la liste n'est pas un nom (l'API d'Hermes ne devrait pas en mettre,
 * mais une entrée numérique deviendrait « 3 » avec la lecture ordinaire du JSON).
 */
fun autoLoadFrom(payload: JsonElement?): List<String> {
    val list: JsonElement? = payload.asObject()?.obj("skills")?.get("auto_load")
    return (list as? JsonArray)
        ?.mapNotNull { element -> (element as? JsonPrimitive)?.takeIf { it.isString }?.content }
        ?: emptyList()
}

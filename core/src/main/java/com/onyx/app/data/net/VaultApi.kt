package com.onyx.app.data.net

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Un secret du coffre, **tel que la lecture le rend** : son nom, son empreinte, sa date et ce qui
 * l'utilise.
 *
 * **Aucun champ ne porte la valeur**, et ce n'est pas un oubli : une valeur qui entrerait dans un
 * modèle de lecture finirait tôt ou tard dans un `toString()`, une sauvegarde d'état ou un journal.
 * Le serveur ne la renvoie d'ailleurs jamais (`vault.list` rend `name`, `fingerprint`,
 * `updated_at`, `used_by`) ; ce type est le miroir exact de cette forme, et le test de l'API pose
 * une valeur dans la réponse pour vérifier qu'elle ne ressort **par aucun chemin** — ni champ, ni
 * `toString()`, ni empreinte.
 *
 * [fingerprint] est l'empreinte que le serveur publie (les quatre derniers caractères) : elle sert
 * à reconnaître un secret sans jamais le révéler. [usedBy] nomme les tâches qui le lisent, et
 * [updatedAt] est l'instant ISO-8601 du serveur (`2026-11-03T12:34:56Z`) — l'écran le rend lisible,
 * il ne l'affiche pas brut.
 */
data class VaultSecret(
    val name: String,
    val fingerprint: String = "",
    val updatedAt: String = "",
    val usedBy: List<String> = emptyList(),
)

/**
 * Ce que rend une écriture (`POST vault.put`) : l'empreinte de la valeur **qui vient d'être
 * écrite**, sa date, et si le secret est nouveau.
 *
 * Même règle que [VaultSecret] : le serveur ne renvoie pas la valeur, ce type n'a donc aucun champ
 * pour l'accueillir.
 */
data class VaultWrite(
    val name: String,
    val fingerprint: String = "",
    val updatedAt: String = "",
    val usedBy: List<String> = emptyList(),
    val created: Boolean = false,
)

/** Ce que rend une révocation (`POST vault.revoke`) : le nom visé, et si une ligne a été retirée. */
data class VaultRevoke(
    val name: String,
    val revoked: Boolean = false,
)

/**
 * La santé du greffon `onyx-vault` (`GET …/health`).
 *
 * [ok] faux n'est pas une panne de l'app : c'est le coffre qui se déclare indisponible, et [code] /
 * [detail] disent pourquoi (clé absente, disque). L'écran le montre tel quel — il n'invente pas
 * d'explication, et il ne lit jamais de valeur ici.
 */
data class VaultHealth(
    val ok: Boolean = false,
    val code: String? = null,
    val detail: String? = null,
)

/**
 * Le coffre de secrets d'un profil, sur le greffon `onyx-vault` du Dashboard.
 *
 * Miroir exact des trois routes montées sous `/api/plugins/onyx-vault/` :
 *
 *  - `GET  vault.list?profile=<profil>`   → `{profile, count, secrets:[…]}` ;
 *  - `POST vault.put`   `{profile, name, value, used_by?}` → `{…, fingerprint, created}` ;
 *  - `POST vault.revoke` `{profile, name}` → `{profile, name, revoked}` ;
 *  - `GET  health` (sans `profile`) → `{ok, code?, detail?}`.
 *
 * Toutes passent par l'unique porte d'entrée du Dashboard, [ServeClient.request] : même session,
 * même reprise sur un cookie mort, même lecture tolérante du JSON que les autres `*Api.kt`.
 *
 * **Le profil est obligatoire côté serveur** : `vault.list` refuse une requête sans `profile` (le
 * greffon valide le nom avant de lire). [ServeClient.profile] vaut `null` pour le profil par
 * défaut, donc c'est `"default"` qui part dans ce cas — jamais une requête muette que le serveur
 * refuserait en 400.
 */
class VaultApi(private val client: ServeClient) {

    /** `GET vault.list?profile=…` — les métadonnées du profil, **jamais les valeurs**. */
    suspend fun list(profile: String? = null): List<VaultSecret> {
        val payload = client.request(LIST, query = mapOf("profile" to profileFor(profile)))
        val rows = payload.asObject()?.array("secrets") ?: payload.asArray().orEmpty()
        return rows.mapNotNull { element ->
            val obj = element.asObject() ?: return@mapNotNull null
            val name = obj.firstString("name") ?: return@mapNotNull null
            VaultSecret(
                name = name,
                fingerprint = obj.str("fingerprint"),
                updatedAt = obj.str("updated_at"),
                usedBy = obj.stringList("used_by"),
            )
        }
    }

    /**
     * `POST vault.put` — écrit ou remplace un secret pour ce profil.
     *
     * `value` part dans le corps et **n'est gardée nulle part** : le résultat ne la contient pas, et
     * aucun journal de ce fichier ne l'écrit. `usedBy` vide n'est pas envoyé (le serveur garde alors
     * la liste déjà déclarée pour ce nom, au lieu de l'effacer).
     */
    suspend fun put(
        name: String,
        value: String,
        profile: String? = null,
        usedBy: List<String> = emptyList(),
    ): VaultWrite {
        val payload = client.request(
            path = PUT,
            method = "POST",
            body = buildJsonObject {
                put("profile", profileFor(profile))
                put("name", name)
                put("value", value)
                if (usedBy.isNotEmpty()) {
                    put("used_by", buildJsonArray { usedBy.forEach { entry -> add(JsonPrimitive(entry)) } })
                }
            },
        )
        val obj = payload.asObject()
        return VaultWrite(
            name = obj?.firstString("name") ?: name,
            fingerprint = obj?.str("fingerprint").orEmpty(),
            updatedAt = obj?.str("updated_at").orEmpty(),
            usedBy = obj?.stringList("used_by").orEmpty(),
            created = obj?.bool("created") ?: false,
        )
    }

    /**
     * `POST vault.revoke` — retire un secret. La route est **idempotente** : `revoked` vaut `false`
     * quand il n'y avait rien à retirer, ce que l'écran dit sans le présenter comme un échec.
     */
    suspend fun revoke(name: String, profile: String? = null): VaultRevoke {
        val payload = client.request(
            path = REVOKE,
            method = "POST",
            body = buildJsonObject {
                put("profile", profileFor(profile))
                put("name", name)
            },
        )
        val obj = payload.asObject()
        return VaultRevoke(
            name = obj?.firstString("name") ?: name,
            revoked = obj?.bool("revoked") ?: false,
        )
    }

    /** `GET health` — la route que la sonde interroge aussi, lue ici pour dire *pourquoi* c'est muet. */
    suspend fun health(): VaultHealth {
        val payload = client.request(HEALTH)
        val obj = payload.asObject()
        return VaultHealth(
            ok = obj?.bool("ok") ?: false,
            code = obj?.strOrNull("code"),
            detail = obj?.strOrNull("detail"),
        )
    }

    /**
     * Le profil qui part réellement : celui qu'on nomme, sinon celui du serveur choisi, sinon
     * `default` — le greffon refuse une requête sans profil.
     */
    private fun profileFor(profile: String?): String =
        profile?.takeIf { it.isNotBlank() }
            ?: client.profile?.takeIf { it.isNotBlank() }
            ?: DEFAULT_PROFILE

    companion object {
        /** Le préfixe des routes du greffon, tel qu'il est monté sur le Dashboard. */
        const val PREFIX = "api/plugins/onyx-vault"

        const val LIST = "$PREFIX/vault.list"
        const val PUT = "$PREFIX/vault.put"
        const val REVOKE = "$PREFIX/vault.revoke"
        const val HEALTH = "$PREFIX/health"

        /** Le profil que le serveur nomme quand l'app n'en choisit pas d'autre. */
        const val DEFAULT_PROFILE = "default"
    }
}

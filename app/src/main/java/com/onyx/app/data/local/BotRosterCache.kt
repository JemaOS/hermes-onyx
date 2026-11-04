package com.onyx.app.data.local

import android.content.Context
import com.onyx.app.data.model.serve.BotProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * **Le dernier roster de bots connu, gardé sur ce téléphone — par connexion, et avec les visages
 * tels qu'ils sont affichés.**
 *
 * Le roster est ce qui dit à quelle conversation appartient quel bot (`botOfConversation`). Il vient
 * du serveur — donc **après** la première frame : au redémarrage, l'app dessinait Onyx (visage bleu,
 * « @default ») puis sautait sur le vrai bot, parce que la liste était vide le temps du réseau.
 *
 * Ce magasin est la copie du dernier roster **réussi**. Il ne remplace jamais le serveur : il le
 * précède d'une frame. Un bot supprimé côté serveur disparaît au premier rafraîchissement réussi —
 * le seul risque est de le voir une seconde de trop, jamais de le perdre.
 *
 * **Deux choses sont gardées, et toutes deux sont nécessaires.**
 *
 * - **Le roster, par connexion.** Un même téléphone peut parler à plusieurs serveurs : sans cette
 *   portée, le roster de l'un s'afficherait pour l'autre — le bot d'un serveur, avec le visage d'un
 *   autre, dans la conversation d'un troisième. La clé est celle de la connexion (la signature de la
 *   configuration), comme pour les salles gardées ([GroupRoomStore]).
 * - **Les visages *effectifs*, pas les codes bruts.** Ce que l'écran dessine n'est pas le
 *   `ui_meta.onyx.face` du serveur : c'est la répartition que [BotFace.faces] fait sur **tout** le
 *   roster, pour que deux bots n'aient jamais la même créature. Le nom seul ne la redonne pas (le
 *   nom `coder` tire la palette 4, alors que le vrai `coder` d'un roster de six bots porte la
 *   palette 6), et le code brut non plus. On garde donc le code **résolu**, celui qui a été
 *   dessiné — c'est le seul qui restitue la même créature au redémarrage.
 *
 * On ne garde que ce que l'en-tête et la liste lisent : nom, libellé, profil principal, identité de
 * chat, visage, et l'origine (`viaAPI`). Rien d'autre ne s'affiche sans le serveur.
 */
class BotRosterCache(private val text: TextStore) {

    constructor(context: Context) : this(PreferencesTextStore(context, PREFERENCES))

    /**
     * Le roster retenu pour cette connexion : les bots **tels qu'ils sont affichés**, et la table
     * de leurs visages.
     *
     * `null` quand ce téléphone n'a jamais enregistré de roster pour cette connexion — et non pas
     * une liste vide : le vide se distingue, parce qu'il ne faut pas effacer ce qui est su.
     */
    fun load(server: String): CachedRoster? {
        val raw = text.get(KEY)?.takeIf { it.isNotBlank() } ?: return null
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val entry = root[server] as? JsonObject ?: return null
        val bots = (entry["bots"] as? JsonArray)
            ?.mapNotNull { element -> (element as? JsonObject)?.let(::read) }
            .orEmpty()
        if (bots.isEmpty()) return null
        val faces = (entry["faces"] as? JsonObject)
            ?.mapNotNull { (name, code) ->
                val value = (code as? JsonPrimitive)?.contentOrNullSafe() ?: return@mapNotNull null
                name to value
            }
            ?.toMap()
            .orEmpty()
        // **La table fait foi.** Le bot porte son visage dans la table, pas à côté d'elle : les
        // deux disent la même chose à l'écran — la créature de l'en-tête et celle de la liste —
        // et c'est la table qui a été gardée. Un bot sans entrée dans la table garde le sien.
        val restored = bots.map { bot -> faces[bot.name]?.let { bot.copy(faceCode = it) } ?: bot }
        return CachedRoster(bots = restored, faces = faces)
    }

    /**
     * Enregistre le roster **affiché** de cette connexion. Un roster vide n'efface rien : sans
     * serveur, on garde.
     */
    fun save(server: String, roster: CachedRoster) {
        if (roster.bots.isEmpty()) return
        val kept = all().toMutableMap()
        kept[server] = JsonObject(
            mapOf(
                "bots" to JsonArray(roster.bots.map(::encode)),
                "faces" to JsonObject(roster.faces.mapValues { JsonPrimitive(it.value) }),
            ),
        )
        write(kept)
    }

    /** « Erase everything » : plus aucun roster gardé, pour aucune connexion. */
    fun clearAll() {
        text.put(KEY, EMPTY)
    }

    // — Ce qui est écrit sur le disque —

    private fun all(): Map<String, JsonObject> {
        val raw = text.get(KEY)?.takeIf { it.isNotBlank() } ?: return emptyMap()
        val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return emptyMap()
        return root.mapNotNull { (server, value) -> (value as? JsonObject)?.let { server to it } }.toMap()
    }

    private fun write(rosters: Map<String, JsonObject>) {
        text.put(KEY, if (rosters.isEmpty()) EMPTY else JsonObject(rosters).toString())
    }

    private fun encode(bot: BotProfile): JsonObject = buildJsonObject {
        put("name", JsonPrimitive(bot.name))
        bot.title?.let { put("title", JsonPrimitive(it)) }
        // Le libellé du profil (`display_name`) décide du nom affiché d'un bot ; sans lui, le lien
        // d'une conversation à son bot par le titre ne tient plus au redémarrage.
        bot.displayName?.let { put("displayName", JsonPrimitive(it)) }
        bot.chatId?.let { put("chatId", JsonPrimitive(it)) }
        bot.faceCode?.let { put("faceCode", JsonPrimitive(it)) }
        if (bot.isDefault) put("isDefault", JsonPrimitive(true))
        if (bot.viaAPI) put("viaAPI", JsonPrimitive(true))
    }

    private fun read(obj: JsonObject): BotProfile? {
        val name = obj["name"]?.jsonPrimitive?.contentOrNullSafe()?.takeIf { it.isNotBlank() } ?: return null
        return BotProfile(
            name = name,
            title = obj["title"]?.jsonPrimitive?.contentOrNullSafe(),
            displayName = obj["displayName"]?.jsonPrimitive?.contentOrNullSafe(),
            chatId = obj["chatId"]?.jsonPrimitive?.contentOrNullSafe(),
            faceCode = obj["faceCode"]?.jsonPrimitive?.contentOrNullSafe(),
            isDefault = obj["isDefault"]?.jsonPrimitive?.booleanOrNull == true,
            viaAPI = obj["viaAPI"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (isString) content.takeIf { it.isNotEmpty() } else null

    private companion object {
        const val PREFERENCES = "onyx_bot_roster"

        /** La clé unique sous laquelle tous les rosters sont écrits, un par connexion. */
        const val KEY = "rosters"
        const val EMPTY = "{}"
    }
}

/**
 * **Le roster d'une connexion, tel qu'il a été affiché.**
 *
 * [bots] porte le visage résolu de chaque bot (son code effectif), [faces] la table de ces visages
 * — celle que l'écran et les raccourcis lisent pour rendre la même créature partout.
 */
data class CachedRoster(
    val bots: List<BotProfile>,
    val faces: Map<String, String>,
)

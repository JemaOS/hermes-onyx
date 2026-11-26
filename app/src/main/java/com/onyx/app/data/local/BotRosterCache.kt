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
 * **The last known bot roster, kept on this phone — per connection, and with the faces
 * as they are displayed.**
 *
 * The roster is what says which conversation belongs to which bot (`botOfConversation`). It comes
 * from the server — therefore **after** the first frame: on restart, the app drew Onyx (blue face,
 * "@default") then jumped to the real bot, because the list was empty while the network was pending.
 *
 * This store is the copy of the last **successful** roster. It never replaces the server: it
 * precedes it by a frame. A bot deleted server-side disappears on the first successful refresh —
 * the only risk is seeing it one second too long, never losing it.
 *
 * **Two things are kept, and both are necessary.**
 *
 * - **The roster, per connection.** The same phone may talk to several servers: without this
 *   scope, one's roster would display for another — one server's bot, with another's face,
 *   in a third's conversation. The key is the connection's (the configuration's
 *   signature), as for the kept rooms ([GroupRoomStore]).
 * - **The *effective* faces, not the raw codes.** What the screen draws is not the server's
 *   `ui_meta.onyx.face`: it is the distribution [BotFace.faces] computes over **the whole**
 *   roster, so that two bots never get the same creature. The name alone does not reproduce it (the
 *   name `coder` draws palette 4, while the real `coder` of a six-bot roster carries
 *   palette 6), and neither does the raw code. We keep the **resolved** code, the one that was
 *   drawn — it is the only one that restores the same creature on restart.
 *
 * We only keep what the header and the list read: name, label, main profile, chat
 * identity, face, and the origin (`viaAPI`). Nothing else displays without the server.
 */
class BotRosterCache(private val text: TextStore) {

    constructor(context: Context) : this(PreferencesTextStore(context, PREFERENCES))

    /**
     * The roster kept for this connection: the bots **as they are displayed**, and the table
     * of their faces.
     *
     * `null` when this phone has never recorded a roster for this connection — and not
     * an empty list: emptiness is distinguished, because what is known must not be erased.
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
        // **The table prevails.** The bot carries its face in the table, not beside it: both
        // say the same thing on screen — the header's creature and the list's —
        // and it is the table that was kept. A bot without an entry in the table keeps its own.
        val restored = bots.map { bot -> faces[bot.name]?.let { bot.copy(faceCode = it) } ?: bot }
        return CachedRoster(bots = restored, faces = faces)
    }

    /**
     * Records the **displayed** roster of this connection. An empty roster erases nothing: without a
     * server, we keep.
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

    /** "Erase everything": no roster kept anymore, for any connection. */
    fun clearAll() {
        text.put(KEY, EMPTY)
    }

    // — What is written to disk —

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
        // The profile label (`display_name`) decides a bot's displayed name; without it, the link
        // from a conversation to its bot by title no longer holds on restart.
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

        /** The single key under which all rosters are written, one per connection. */
        const val KEY = "rosters"
        const val EMPTY = "{}"
    }
}

/**
 * **A connection's roster, as it was displayed.**
 *
 * [bots] carries each bot's resolved face (its effective code), [faces] the table of those faces
 * — the one the screen and the shortcuts read to render the same creature everywhere.
 */
data class CachedRoster(
    val bots: List<BotProfile>,
    val faces: Map<String, String>,
)

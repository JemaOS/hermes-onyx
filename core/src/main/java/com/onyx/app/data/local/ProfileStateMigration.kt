package com.onyx.app.data.local

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.ProfileRename
import com.onyx.app.data.model.serve.ProfileRenames

/** Ce qu'une passe de migration a fait : les renommages reportés, et les conversations retitrées. */
data class MigrationOutcome(
    val applied: List<ProfileRename> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
)

/**
 * Reporte sur le **nom courant** l'état local que l'app garde par *nom de profil*, après un
 * renommage côté serveur.
 *
 * Trois états suivent, chacun avec sa manière d'être idempotent :
 *
 * - **Visage et visibilité** (`onyx_bot_faces` : la face, le titre, la phrase, le modèle, « shown »,
 *   `force_hidden_<nom>`, et la liste des profils ajoutés à la main) : les entrées de l'ancien nom
 *   sont **déplacées** sur le nouveau. On n'écrit que si la clé du nouveau nom est *absente*
 *   (« l'état courant gagne »), puis on retire l'ancienne — un déplacement est idempotent.
 * - **Activité et ordre** (`onyx_bot_activity` : `seen|<serveur>|bot:<nom>` et la liste
 *   `order|<serveur>`) : de même, la marque est déplacée et `bot:<ancien>` devient `bot:<courant>`
 *   dans l'ordre.
 * - **Rattachement des conversations** (`conversations.json`) : une conversation ouverte pour un
 *   bot **sans `canonical_session`** est liée par son **titre** (le nom que [botChatToOpen] lui
 *   pose). On la retitre donc de l'ancien nom vers le courant. Un bot **avec** `canonical_session`
 *   garde son lien fort (`BotProfile.chatId` == `serverSessionId`), immuable : rien à faire.
 *
 * L'**une seule fois** tient au [ProfileRenameLedger] : chaque paire « ancien=>courant » n'est
 * reportée qu'une fois. Le retitre des conversations, lui, n'est pas derrière le journal : il est
 * idempotent par nature (après coup le titre ne dit plus l'ancien nom) et peut donc rattraper des
 * conversations lues *après* le roster.
 */
class ProfileStateMigration(
    private val faces: RenameStore,
    private val activity: RenameStore,
    private val ledger: ProfileRenameLedger,
) {

    fun migrate(
        roster: List<BotProfile>,
        serverKey: String,
        conversations: List<Conversation> = emptyList(),
        aliases: Map<String, String> = ProfileRenames.LocalAliases,
    ): MigrationOutcome {
        val renames = ProfileRenames.plan(roster, aliases)
        if (renames.isEmpty()) return MigrationOutcome(conversations = conversations)

        val recorded = ledger.applied().toMutableSet()
        val applied = mutableListOf<ProfileRename>()
        for (rename in renames) {
            if (rename.fact in recorded) continue
            moveFaceState(rename.from, rename.to)
            moveActivityState(serverKey, rename.from, rename.to)
            ledger.record(rename.fact)
            recorded += rename.fact
            applied += rename
        }
        val retitled = retitleConversations(conversations, renames)
        return MigrationOutcome(applied = applied, conversations = retitled)
    }

    // --- Visage et visibilité ------------------------------------------------------------------

    private fun moveFaceState(from: String, to: String) {
        moveString(faces, FaceKeys.title(from), FaceKeys.title(to))
        moveString(faces, FaceKeys.about(from), FaceKeys.about(to))
        moveString(faces, FaceKeys.model(from), FaceKeys.model(to))
        // Le visage : la clé EST le nom du profil.
        moveString(faces, FaceKeys.face(from), FaceKeys.face(to))
        moveBoolean(faces, FaceKeys.shown(from), FaceKeys.shown(to))
        moveBoolean(faces, FaceKeys.hideOverride(from), FaceKeys.hideOverride(to))
        moveBoolean(faces, FaceKeys.pinOverride(from), FaceKeys.pinOverride(to))
        moveApiBotEntry(from, to)
    }

    /** La liste des profils ajoutés à la main : l'entrée suit le nom. */
    private fun moveApiBotEntry(from: String, to: String) {
        val joined = faces.string(FaceKeys.API_BOTS) ?: return
        val bots = joined.split(FaceKeys.SEPARATOR).filter { it.isNotBlank() }
        if (from !in bots) return
        val next = bots.map { if (it == from) to else it }.distinct()
        faces.putString(FaceKeys.API_BOTS, next.joinToString(FaceKeys.SEPARATOR))
    }

    // --- Activité et ordre ---------------------------------------------------------------------

    private fun moveActivityState(server: String, from: String, to: String) {
        val oldItem = ActivityKeys.botItem(from)
        val newItem = ActivityKeys.botItem(to)
        moveString(activity, ActivityKeys.seen(server, oldItem), ActivityKeys.seen(server, newItem))

        val orderKey = ActivityKeys.order(server)
        val joined = activity.string(orderKey) ?: return
        val items = joined.split(ActivityKeys.SEPARATOR).filter { it.isNotBlank() }
        if (oldItem !in items) return
        val next = items.map { if (it == oldItem) newItem else it }.distinct()
        activity.putString(orderKey, next.joinToString(ActivityKeys.SEPARATOR))
    }

    // --- Aides de déplacement ------------------------------------------------------------------

    /** Déplace une valeur **chaîne** : on n'écrase jamais l'état courant, l'ancienne clé part. */
    private fun moveString(store: RenameStore, oldKey: String, newKey: String) {
        val value = store.string(oldKey)
        if (value != null && !store.contains(newKey)) store.putString(newKey, value)
        if (store.contains(oldKey)) store.remove(oldKey)
    }

    /** Déplace une valeur **booléenne** (visibilité), même règle. */
    private fun moveBoolean(store: RenameStore, oldKey: String, newKey: String) {
        if (store.contains(oldKey) && !store.contains(newKey)) store.putBoolean(newKey, store.boolean(oldKey, false))
        if (store.contains(oldKey)) store.remove(oldKey)
    }
}

/**
 * Retitre les conversations rattachées à un bot par son **nom** (le lien de repli de
 * `botChatToOpen`, quand le roster ne nomme aucun `canonical_session`).
 *
 * Ne touche qu'une conversation dont le titre **posé** ([Conversation.customName], pas un titre
 * « New Conversation » ni la première question reprise mot pour mot) vaut exactement l'ancien nom.
 */
fun retitleConversations(
    conversations: List<Conversation>,
    renames: List<ProfileRename>,
): List<Conversation> {
    if (conversations.isEmpty() || renames.isEmpty()) return conversations
    val labels = renames.associate { it.from to it.to }
    return conversations.map { conversation ->
        val name = conversation.customName() ?: return@map conversation
        val target = labels[name] ?: return@map conversation
        conversation.copy(title = target)
    }
}

package com.onyx.app.data.net

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.ServerConfig

/**
 * Les conversations du **serveur**, lues dans le registre des sessions, et traduites en
 * conversations qu'Onyx sait afficher.
 *
 * Le volet des conversations ne montre que ce que l'app a **écrit** sur ce téléphone : le
 * registre local. Les sessions du serveur — celles des autres appareils, celles de la CLI,
 * celles d'hier — n'y sont pas, faute d'être lues. La liste paraissait alors vide alors que
 * le serveur en comptait des dizaines.
 *
 * Chaque session devient une conversation **sans message** : le fil est lu à l'ouverture
 * (`ServeSessionMessages`), jamais d'avance. C'est ce qui permet d'ouvrir une session déjà
 * commencée sans en charger tout l'historique dans la liste.
 */
object ServerSessions {

    /** L'identifiant local d'une conversation distante : stable, et lié à la session. */
    fun localId(serverId: String, sessionId: String): String = "srv-$serverId-$sessionId"

    /** La session distant qu'une conversation locale représente. */
    fun serverIdOf(conversation: Conversation): String? =
        conversation.serverSessionId?.takeIf { it.isNotBlank() }

    /**
     * Les conversations distantes d'un serveur, triées comme le registre local : les plus
     * récentes d'abord.
     *
     * Une session déjà connue du registre local n'est pas dupliquée : c'est la même
     * conversation, déjà enregistrée avec ses messages.
     *
     * **Le chat d'un bot n'est pas une conversation de la liste.** [botChats] porte les
     * `canonical_session` des bots du roster (`BotProfile.chatId`) : le chat d'un bot s'ouvre par
     * sa tuile, et l'importer comme conversation faisait apparaître, **tout seul**, une ligne vide
     * par bot — titrée de son nom, sans aperçu — juste après qu'on eut créé une conversation avec
     * l'un d'eux. C'est le défaut relevé : « j'ai créé une conversation avec coder et j'ai 3 bots,
     * je devrais avoir que le bot que j'ai choisi ».
     */
    fun conversations(
        server: ServerConfig,
        summaries: List<SessionSummary>,
        known: List<Conversation>,
        botChats: Set<String> = emptySet(),
    ): List<Conversation> {
        val already = known.mapNotNull { serverIdOf(it) }.toSet()
        return summaries
            .filterNot { it.id in already }
            .filterNot { it.id in botChats }
            .map { summary -> conversation(server, summary) }
            .sortedByDescending { it.updatedAt }
    }

    /**
     * Les conversations **distantes** qui ne sont que le chat d'un bot — celles que l'ancien
     * import avait écrites dans le registre du téléphone.
     *
     * Purement comparatif : une conversation écrite ici (`remote == false`) n'est jamais listée,
     * même si elle porte la session d'un bot — c'est peut-être exactement le fil qu'on a ouvert
     * avec lui.
     */
    fun staleBotChats(known: List<Conversation>, botChats: Set<String>): List<String> =
        known.filter { it.remote && it.serverSessionId in botChats }.map { it.id }

    /** La conversation locale qui représente une session du serveur. */
    fun conversation(server: ServerConfig, summary: SessionSummary): Conversation {
        val started = summary.startedAt?.let { (it * 1000).toLong() }
        val touched = summary.lastActive?.let { (it * 1000).toLong() }
        val created = started ?: touched ?: 0L
        return Conversation(
            id = localId(server.id, summary.id),
            title = summary.title?.takeIf { it.isNotBlank() } ?: summary.preview?.takeIf { it.isNotBlank() }
                ?: Conversation.DefaultTitle,
            createdAt = created,
            updatedAt = touched ?: created,
            serverId = server.id,
            serverSessionId = summary.id,
            remote = true,
        )
    }
}

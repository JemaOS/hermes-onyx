package com.onyx.app.ui.bots

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.design.ProfileInForce

/**
 * Le chat d'un bot, comme `Conversation.openBot(_:mainName:)` de la référence
 * (Echo/Services/Conversation:1477) : la conversation dont la session serveur est le
 * `canonical_session` du bot — la session que le Dashboard lui nomme (`bot.chatId`). C'est la même
 * conversation que la tuile lit pour ses pastilles (`openStreaming`, `isOpen`).
 */
fun botChat(bot: BotProfile, conversations: List<Conversation>): Conversation? =
    bot.chatId?.let { chat ->
        conversations.firstOrNull { it.serverSessionId == chat || it.id == chat }
    }

/**
 * Le **profil Hermes** qui porte la session d'une conversation : celui de son bot, sinon celui du
 * serveur — la décision unique de [com.onyx.app.ui.design.ProfileInForce].
 *
 * Une session est écrite dans la base **de son profil** (`~/.hermes/profiles/<bot>/state.db`) : un
 * écran d'usage, de registre ou de relevé qui oublie le profil interroge la base `default`, n'y
 * trouve pas la ligne du bot, et conclut « le registre n'a pas répondu » sur une session
 * parfaitement enregistrée. C'est le défaut signalé — ici, la règle est écrite une fois.
 *
 * Rend `"default"` (le profil de la passerelle) quand ni le bot ni le serveur n'en nomment un.
 */
fun conversationProfile(
    conversation: Conversation,
    bots: List<BotProfile>,
    server: ServerConfig,
    mainName: String = "Onyx",
): String = ProfileInForce.inForce(
    botOfConversation(conversation, bots, server.displayName.ifBlank { mainName })?.name,
    server.effectiveProfile,
)

/** Le profil d'un **bot** nommé, pour la portée « par bot » du relevé. */
fun botProfileName(bot: BotProfile): String = ProfileInForce.inForce(bot.name, null)

/**
 * Les profils Hermes qu'un relevé doit interroger : les bots nommés, plus le profil du serveur.
 *
 * Même liste pour le registre des sessions **et** pour la table de prix — une seule façon de nommer
 * un profil, comme `ModelCatalog.fetchModelOptions`.
 */
fun ledgerProfiles(bots: List<BotProfile>, server: ServerConfig): List<String> =
    (bots.map { it.name } + (server.effectiveProfile ?: ProfileInForce.Default))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/**
 * Le bot dont c'est la conversation ouverte, ou `null` pour une conversation simple.
 *
 * Deux liens, dans cet ordre :
 *
 * 1. **Le chat serveur du bot** (`BotProfile.chatId`, le `canonical_session` que le Dashboard lui
 *    nomme) : le lien fort, celui que [botChat] et la tuile lisent. Un bot dont le chat nommé est
 *    la session serveur de la conversation — ou la conversation elle-même — la revendique.
 * 2. **Le titre posé par [botChatToOpen]** : quand le roster ne nomme aucun `canonical_session`,
 *    l'ouverture du chat d'un bot **titre la conversation de son nom**. Ce titre est le seul lien
 *    durable qui reste. Il vaut **même après le premier tour** — la session serveur s'écrit alors
 *    dans la conversation, et l'ancien test (`serverSessionId == null`) perdait le bot dès que la
 *    conversation avait parlé, ce qui faisait retomber l'en-tête sur Onyx (le défaut « scout »).
 *
 * Le titre ne compte que s'il a vraiment été **posé** : [Conversation.customName] écarte un titre
 * qui n'est que la première question, qu'un message de l'utilisateur peut reprendre mot pour mot
 * (« scout » en premier message ne fait pas de la conversation celle du bot `scout`). Et il ne
 * compte que s'il désigne **un seul** bot : deux profils du même libellé rendraient le lien
 * ambigu, et un faux rattachement (le visage d'un bot qui n'a rien dit) vaut moins que pas de
 * rattachement — Onyx montre alors son propre visage.
 */
fun botOfConversation(
    conversation: Conversation,
    bots: List<BotProfile>,
    mainName: String = "Onyx",
): BotProfile? = bots.firstOrNull { bot ->
    bot.chatId != null &&
        (bot.chatId == conversation.serverSessionId || bot.chatId == conversation.id)
} ?: conversation.customName()
    ?.let { name -> bots.filter { it.label(mainName) == name }.singleOrNull() }

/**
 * **La dernière session d'un bot** — « Ouvrir la dernière session » du menu.
 *
 * La plus récente de ses conversations **autre que son chat** : une routine qu'il a exécutée, un
 * fil qu'on a ouvert avec lui, une conversation reprise plus tard. La référence lit cette ligne sur
 * le roster (`last_session` de `profiles.list`, `BotsRoster.kt:447`) et ouvre l'identifiant qu'elle
 * porte ; ce port n'a **pas de transport de sessions** — le fil d'un bot s'écrit dans une
 * conversation locale —, la même question se pose donc ici sur l'historique du téléphone : les
 * conversations rattachées à ce bot ([botOfConversation]), la plus récemment modifiée d'abord.
 *
 * Rend `null` quand il n'y en a aucune : l'entrée ne s'affiche alors pas (une entrée morte serait
 * pire que pas d'entrée).
 */
fun botRecentConversation(
    bot: BotProfile,
    conversations: List<Conversation>,
    bots: List<BotProfile> = listOf(bot),
    mainName: String = "Onyx",
): Conversation? {
    val own = bot.chatId?.takeIf { it.isNotBlank() }
    return conversations
        .filter { it.serverSessionId != own && it.id != own }
        .filter { botOfConversation(it, bots, mainName) == bot }
        .maxByOrNull { it.updatedAt }
}

/**
 * Le chat d'un bot à ouvrir : celui qu'on garde déjà, sinon une conversation **liée à son chat
 * serveur**, titrée de son nom — le nom que la conversation d'un bot porte chez référence
 * (`BotRef.name`, celui de l'en-tête et de la liste), pas le titre « Bot Chat » de la session
 * elle-même.
 *
 * référence fait le chemin entier : il retrouve la session par son titre sur le profil du bot
 * (`session.list`), la fait quand elle n'existe pas encore (`session.create`) puis l'ouvre. Ce port
 * n'a pas de transport de sessions (le chat passe par `/v1/chat/completions`, sans état) : il ouvre
 * donc la conversation liée au `canonical_session` que le roster a nommé, et la garde pour la
 * retrouver ensuite. Un bot que le roster ne nomme pas encore ([BotProfile.chatId] absent) ouvre une
 * conversation neuve, sans session serveur : c'est tout ce que le port peut tenir, et la session
 * s'y écrit au premier tour.
 */
fun botChatToOpen(
    bot: BotProfile,
    conversations: List<Conversation>,
    serverId: String = "",
    mainName: String = "Onyx",
    now: Long = System.currentTimeMillis(),
): Conversation = botChat(bot, conversations)
    ?: Conversation.start(now = now).copy(
        serverId = serverId,
        serverSessionId = bot.chatId,
        title = bot.label(mainName),
    )

/**
 * La conversation qu'ouvre le **crayon** de la barre du fil, selon l'endroit où l'on est.
 *
 * - **Dans la conversation d'un bot** ([bot] non nul) : une conversation du **même** bot — mêmes
 *   profil, visage et couleur, puisque c'est [botOfConversation] qui la rattache. La **même**
 *   conversation quand elle est vide et jamais servie (aucun message, rien en attente : il n'y a
 *   rien à perdre, et en garder deux vides serait un doublon) — où qu'elle soit dans la liste, pour
 *   qu'un fil neuf laissé de côté se retrouve au lieu d'en faire un second. Sinon une **neuve**,
 *   titrée du libellé du bot : c'est ce titre qui la rattache au bot, et jamais la session serveur
 *   d'un autre.
 * - **Dans une conversation simple** ([bot] nul, c'est-à-dire la racine) : la courante quand elle
 *   est vide et jamais servie, sinon une neuve — le comportement d'avant, sans doublon vide.
 *
 * La conversation rendue n'est jamais l'ancienne quand elle a parlé : celle-ci reste dans
 * l'historique, entière. C'est l'appelant qui enregistre le résultat quand il est neuf.
 */
fun freshConversationForPencil(
    current: Conversation?,
    bot: BotProfile?,
    conversations: List<Conversation> = emptyList(),
    serverId: String = "",
    mainName: String = "Onyx",
    now: Long = System.currentTimeMillis(),
): Conversation {
    if (bot != null) {
        // La conversation courante d'abord — c'est celle qu'on regarde —, puis toute autre
        // conversation vide du même bot : la reprendre vaut mieux qu'une deuxième vide.
        val mine = buildList {
            current?.let(::add)
            addAll(conversations)
        }
        return mine.firstOrNull { it.isEmpty && botOfConversation(it, listOf(bot), mainName) != null }
            ?: Conversation.start(now = now).copy(serverId = serverId, title = bot.label(mainName))
    }
    return current?.takeIf { it.isEmpty }
        ?: Conversation.start(now = now).copy(serverId = serverId)
}

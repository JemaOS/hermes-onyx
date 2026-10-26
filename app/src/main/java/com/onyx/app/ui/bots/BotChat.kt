package com.onyx.app.ui.bots

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.serve.BotProfile

/**
 * Le chat d'un bot, comme `Conversation.openBot(_:mainName:)` 
 *: la conversation dont la session serveur est le
 * `canonical_session` du bot — la session que le Dashboard lui nomme (`bot.chatId`). C'est la même
 * conversation que la tuile lit pour ses pastilles (`openStreaming`, `isOpen`).
 */
fun botChat(bot: BotProfile, conversations: List<Conversation>): Conversation? =
    bot.chatId?.let { chat ->
        conversations.firstOrNull { it.serverSessionId == chat || it.id == chat }
    }

/**
 * Le bot dont c'est la conversation ouverte, ou `null` pour une conversation simple.
 *
 * Deux liens, dans cet ordre:
 *
 * 1. **Le chat serveur du bot** (`BotProfile.chatId`, le `canonical_session` que le Dashboard lui
 * nomme): le lien fort, celui que [botChat] et la tuile lisent. Un bot dont le chat nommé est
 * la session serveur de la conversation — ou la conversation elle-même — la revendique.
 * 2. **Le titre posé par [botChatToOpen]**: quand le roster ne nomme aucun `canonical_session`,
 * l'ouverture du chat d'un bot **titre la conversation de son nom**. Ce titre est le seul lien
 * durable qui reste. Il vaut **même après le premier tour** — la session serveur s'écrit alors
 * dans la conversation, et l'ancien test (`serverSessionId == null`) perdait le bot dès que la
 * conversation avait parlé, ce qui faisait retomber l'en-tête sur Onyx (le défaut « scout »).
 *
 * Le titre ne compte que s'il a vraiment été **posé**: [Conversation.customName] écarte un titre
 * qui n'est que la première question, qu'un message de l'utilisateur peut reprendre mot pour mot
 * (« scout » en premier message ne fait pas de la conversation celle du bot `scout`). Et il ne
 * compte que s'il désigne **un seul** bot: deux profils du même libellé rendraient le lien
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
 * Le chat d'un bot à ouvrir: celui qu'on garde déjà, sinon une conversation **liée à son chat
 * serveur**, titrée de son nom — le nom que la conversation d'un bot porte chez 
 * (`BotRef.name`, celui de l'en-tête et de la liste), pas le titre « Bot Chat » de la session
 * elle-même.
 *
 * fait le chemin entier: il retrouve la session par son titre sur le profil du bot
 * (`session.list`), la fait quand elle n'existe pas encore (`session.create`) puis l'ouvre. Ce port
 * n'a pas de transport de sessions (le chat passe par `/v1/chat/completions`, sans état): il ouvre
 * donc la conversation liée au `canonical_session` que le roster a nommé, et la garde pour la
 * retrouver ensuite. Un bot que le roster ne nomme pas encore ([BotProfile.chatId] absent) ouvre une
 * conversation neuve, sans session serveur: c'est tout ce que le port peut tenir, et la session
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

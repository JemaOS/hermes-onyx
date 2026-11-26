package com.onyx.app.ui.bots

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.ui.design.ProfileInForce

/**
 * A bot's chat, like `Conversation.openBot(_:mainName:)` in the reference
 * (Echo/Services/Conversation:1477): the conversation whose server session is the
 * bot's `canonical_session` — the session the Dashboard names for it (`bot.chatId`). It is the same
 * conversation the tile reads for its badges (`openStreaming`, `isOpen`).
 */
fun botChat(bot: BotProfile, conversations: List<Conversation>): Conversation? =
    bot.chatId?.let { chat ->
        conversations.firstOrNull { it.serverSessionId == chat || it.id == chat }
    }

/**
 * The **Hermes profile** that carries a conversation's session: that of its bot, otherwise that of the
 * server — the single decision of [com.onyx.app.ui.design.ProfileInForce].
 *
 * A session is written into **its profile's** database (`~/.hermes/profiles/<bot>/state.db`): a usage,
 * roster or ledger screen that forgets the profile queries the `default` database, does not
 * find the bot's row there, and concludes "the roster did not respond" for a session that is
 * perfectly recorded. That is the reported defect — here, the rule is written once.
 *
 * Returns `"default"` (the gateway's profile) when neither the bot nor the server names one.
 */
fun conversationProfile(
    conversation: Conversation,
    bots: List<BotProfile>,
    server: ServerConfig,
    mainName: String = "Onyx",
): String = ProfileInForce.inForce(
    // **The profile retained by the conversation counts.** A conversation imported from the server
    // carries neither a bot title nor a `canonical_session`: its only link is `botProfile`, written at
    // import. Without this read, all conversations reimported after a reinstall
    // fell back on Onyx's face (the blue one) instead of their bot's.
    botOfConversationOrRemembered(conversation, bots, server.displayName.ifBlank { mainName })?.name,
    server.effectiveProfile,
)

/** The profile of a named **bot**, for the "per bot" scope of the ledger. */
fun botProfileName(bot: BotProfile): String = ProfileInForce.inForce(bot.name, null)

/**
 * The Hermes profiles a ledger must query: the named bots, plus the server's profile.
 *
 * Same list for the session roster **and** for the price table — a single way to name
 * a profile, like `ModelCatalog.fetchModelOptions`.
 */
fun ledgerProfiles(bots: List<BotProfile>, server: ServerConfig): List<String> =
    (bots.map { it.name } + (server.effectiveProfile ?: ProfileInForce.Default))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/**
 * The bot whose open conversation this is, or `null` for a plain conversation.
 *
 * Two links, in this order:
 *
 * 1. **The bot's server chat** (`BotProfile.chatId`, the `canonical_session` the Dashboard names
 *    for it): the strong link, the one [botChat] and the tile read. A bot whose named chat is
 *    the conversation's server session — or the conversation itself — claims it.
 * 2. **The title set by [botChatToOpen]**: when the roster names no `canonical_session`,
 *    opening a bot's chat **titles the conversation with its name**. This title is the only durable
 *    link that remains. It holds **even after the first turn** — the server session is then written
 *    into the conversation, and the former test (`serverSessionId == null`) lost the bot as soon as the
 *    conversation had spoken, which made the header fall back on Onyx (the "scout" defect).
 *
 *    The compared name is [Conversation.botName]: the title **without** the branch suffix. A
 *    branch ("coder (fork)") is a copy of a bot conversation — it keeps it its bot,
 *    hence its face and its profile. That is what "Create a thread" and "branch from here"
 *    produce; without this rule, the branch fell back on Onyx's face while its
 *    original kept its own.
 *
 * The title counts only if it was really **set**: [Conversation.customName] dismisses a title
 * that is only the first question, which a user message may repeat word for word
 * ("scout" as the first message does not make the conversation the `scout` bot's). And it only
 * counts if it designates **a single** bot: two profiles with the same label would make the link
 * ambiguous, and a false attachment (the face of a bot that said nothing) is worth less than no
 * attachment — Onyx then shows its own face.
 */
fun botOfConversation(
    conversation: Conversation,
    bots: List<BotProfile>,
    mainName: String = "Onyx",
): BotProfile? {
    val named = conversation.botName()
    val byChat = bots.filter { bot ->
        bot.chatId != null &&
            (bot.chatId == conversation.serverSessionId || bot.chatId == conversation.id)
    }
    // **A canonical session identifies a bot only if it designates only one.** A bot created
    // "from" another inherits its original's `canonical_session` (`clone_from`): two
    // bots then claimed the same session, and `firstOrNull` gave the **first of the roster** to
    // all its conversations — two bots, a single face, and changing one's face changed
    // nothing for it (reported by the owner: "all the coder have syncro the same
    // face"). When the session is shared, it therefore no longer decides: the **name of the
    // conversation** settles it between the bots that claim it, and if it does not settle it, the
    // conversation falls back on the profile it retained ([Conversation.botProfile]) — never on
    // a bot chosen at random from a list.
    if (byChat.size == 1) return byChat.first()
    if (byChat.size > 1) {
        return named?.let { name -> byChat.filter { it.label(mainName) == name }.singleOrNull() }
    }
    return named?.let { name -> bots.filter { it.label(mainName) == name }.singleOrNull() }
}

/**
 * **A conversation's bot, even when the roster has not arrived yet.**
 *
 * The roster is the truth, but it comes from the network: on restart, the screen is drawn before it,
 * and the conversation then displayed as Onyx — blue face, "@default" — for the time of two
 * frames, before jumping to its real bot. What the conversation retained
 * ([Conversation.botProfile]) fills exactly this hole: `""` for the main profile, otherwise the
 * profile name. It never replaces a roster bot, it **precedes** it.
 *
 * **The profile name is the bot's identity, and it suffices.** It is what the conversation
 * retained, and it is what the roster carries: the bot found by this name is the real one — with its
 * face, the one the server distributed over the roster. Without this, the retained bot was a
 * faceless shell: the screen drew the creature that the **name** pulls, instead of the one
 * of yesterday — the same bot, but not the same face, which is not the same thing.
 */
fun botOfConversationOrRemembered(
    conversation: Conversation,
    bots: List<BotProfile>,
    mainName: String = "Onyx",
): BotProfile? = botOfConversation(conversation, bots, mainName)
    ?: conversation.botProfile?.let { profile ->
        if (profile.isEmpty()) BotProfile(name = "default", isDefault = true)
        // The roster may already know this profile: then it is the one speaking, face included.
        else bots.firstOrNull { it.name == profile } ?: BotProfile(name = profile)
    }

/**
 * **A bot's last session** — "Open the last session" in the menu.
 *
 * The most recent of its conversations **other than its chat**: a routine it ran, a
 * thread opened with it, a conversation resumed later. The reference reads this line on
 * the roster (`last_session` of `profiles.list`, `BotsRoster.kt:447`) and opens the identifier it
 * carries; this port has **no session transport** — a bot's thread is written into a
 * local conversation —, so the same question is asked here on the phone's history: the
 * conversations attached to this bot ([botOfConversation]), the most recently modified first.
 *
 * Returns `null` when there is none: the entry then does not appear (a dead entry would be
 * worse than no entry).
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
 * A bot's chat to open: the one already kept, otherwise a conversation **linked to its server
 * chat**, titled with its name — the name a bot's conversation carries in the reference
 * (`BotRef.name`, the one of the header and of the list), not the "Bot Chat" title of the session
 * itself.
 *
 * The reference does the whole path: it finds the session by its title on the bot's profile
 * (`session.list`), creates it when it does not exist yet (`session.create`) then opens it. This port
 * has no session transport (the chat goes through `/v1/chat/completions`, stateless): it therefore
 * opens the conversation linked to the `canonical_session` that the roster named, and keeps it to
 * find it again afterwards. A bot the roster does not name yet ([BotProfile.chatId] absent) opens a
 * new conversation, with no server session: it is all the port can hold, and the session
 * is written into it on the first turn.
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
 * The conversation opened by the thread bar's **pencil**, depending on where one is.
 *
 * - **In a bot's conversation** ([bot] non-null): a conversation of the **same** bot — same
 *   profile, face and color, since it is [botOfConversation] that attaches it. The **same**
 *   conversation when it is empty and never served (no message, nothing pending: there is
 *   nothing to lose, and keeping two empty ones would be a duplicate) — wherever it is in the list, so
 *   that a new thread left aside is found instead of making a second one. Otherwise a **new** one,
 *   titled with the bot's label: it is this title that attaches it to the bot, and never the server
 *   session of another.
 * - **In a plain conversation** ([bot] null, that is the root): the current one when it
 *   is empty and never served, otherwise a new one — the former behavior, without an empty duplicate.
 *
 * The returned conversation is never the old one when it has spoken: that one stays in
 * the history, whole. It is the caller that saves the result when it is new.
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
        // The current conversation first — it is the one being looked at —, then any other
        // empty conversation of the same bot: reusing it is better than a second empty one.
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

package com.onyx.app.ui.bots

import com.onyx.app.data.local.BotPresence
import com.onyx.app.data.local.BotPresenceIndex
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.ServerConfig
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.repo.branchedConversation
import com.onyx.app.data.repo.forkedConversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La branche garde le bot de son originale.**
 *
 * Le défaut que le propriétaire a montré : « quand je fork une nouvelle conversation, ça
 * synchronise pas le nouveau visage ». Sur la capture, la ligne « coder (fork) » portait le visage
 * **bleu d'Onyx** — celui du repli — pendant que les lignes « coder » portaient le leur.
 *
 * Deux liens rattachent une conversation à son bot : le **chat serveur** du bot
 * (`BotProfile.chatId`) et le **titre posé** par l'ouverture de son chat. C'est le second qui
 * manque aux bots que le roster ne nomme pas de `canonical_session` — et c'est lui que le
 * suffixe « (fork) » cassait : le titre de la branche, « coder (fork) », ne correspondait à
 * aucun libellé de bot.
 *
 * La règle est donc : une branche est la **copie** d'une conversation, et elle garde son
 * identité — le nom comparé au libellé du bot est le titre **sans** le suffixe
 * ([Conversation.botName]). Une branche de conversation simple, elle, ne se rattache à personne.
 */
class ForkBotIdentityTest {

    private fun user(text: String) = ChatMessage(id = "u", role = Role.USER, text = text, createdAt = 1L)

    /** Le bot `coder` du roster : pas de `canonical_session` — c'est le titre qui le lie. */
    private val coder = BotProfile(name = "coder", displayName = "coder", faceCode = "1:round:round:smile:none:0:0:0")

    private val roster = listOf(coder)

    /** Une conversation de bot : ouverte depuis la tuile, donc **titrée** de son nom. */
    private val chat = Conversation(
        id = "c1",
        title = "coder",
        createdAt = 1L,
        updatedAt = 2L,
        messages = listOf(user("Oui — les 4 audits tournent en ce moment")),
    )

    /** Une conversation simple : son titre n'est que sa première question. */
    private val simple = Conversation(
        id = "c2",
        title = "Où est la clé ?",
        createdAt = 1L,
        updatedAt = 2L,
        messages = listOf(user("Où est la clé ?")),
    )

    /** Une conversation simple **renommée** : son titre est un nom posé, distinct de la question. */
    private val renamed = Conversation(
        id = "c2b",
        title = "Projet Onyx",
        createdAt = 1L,
        updatedAt = 2L,
        messages = listOf(user("Où est la clé ?")),
    )

    private val server = ServerConfig(id = "default", name = "Onyx", hermesProfile = "default")

    // ---- 1. la branche garde le bot ----------------------------------------------------------

    @Test
    fun `la branche d une conversation de bot garde le bot`() {
        val fork = forkedConversation(chat, id = "c3", now = 9_999L)

        assertEquals("coder", botOfConversation(fork, roster)?.name)
        assertEquals("la branche porte le bot de son originale", coder, botOfConversation(fork, roster))
    }

    @Test
    fun `la branche et son originale partagent le meme profil`() {
        val fork = forkedConversation(chat, id = "c3", now = 9_999L)

        // C'est ce profil que la liste résout pour la **carte** : sans lui, la carte tombe sur le
        // repli du serveur — le visage bleu d'Onyx de la capture.
        assertEquals(
            "la branche et l'originale ont le même profil",
            conversationProfile(chat, roster, server, "Onyx"),
            conversationProfile(fork, roster, server, "Onyx"),
        )
        assertEquals("coder", conversationProfile(fork, roster, server, "Onyx"))
    }

    @Test
    fun `une branche branchee a partir d ici garde aussi le bot`() {
        // « Brancher à partir d'ici » **efface** la session serveur : il ne reste que le titre.
        // C'est le cas le plus nu : sans le nom de base, la branche n'appartenait à personne.
        val branch = branchedConversation(chat, id = "c4", messageId = chat.messages.first().id, now = 9_999L)!!

        assertNull("la branche n'a pas de session serveur", branch.serverSessionId)
        assertEquals("la branche garde quand même le bot", "coder", botOfConversation(branch, roster)?.name)
    }

    @Test
    fun `le widget nomme aussi le bot de la branche`() {
        val fork = forkedConversation(chat, id = "c3", now = 9_999L)
        val table = listOf(BotPresence(name = "coder", label = "coder", chatId = null))

        assertEquals("coder", BotPresenceIndex.labelOf(fork, table))
    }

    // ---- 2. ce qui ne doit pas être rattaché ------------------------------------------------

    @Test
    fun `une branche de conversation simple ne se rattache a aucun bot`() {
        val fork = forkedConversation(simple, id = "c5", now = 9_999L)

        assertNull(botOfConversation(fork, roster))
        // Le nom de base, une fois le suffixe retiré, redevient la première question : ce n'est
        // pas un nom posé, donc pas un lien.
        assertNull(fork.botName())
    }

    @Test
    fun `un titre ambigu ne rattache toujours personne`() {
        val first = BotProfile(name = "coder", displayName = "coder")
        val second = BotProfile(name = "coder-2", displayName = "coder")
        val fork = forkedConversation(chat, id = "c6", now = 9_999L)

        assertNull(botOfConversation(fork, listOf(first, second)))
    }

    @Test
    fun `une conversation dont le titre est la premiere question ne lie toujours personne`() {
        // Un bot s'appelle « Où est la clé ? » et la conversation porte ce titre : c'est sa
        // première question, pas un nom posé — elle ne lui appartient donc pas. Sa branche non
        // plus : le nom de base, une fois le suffixe retiré, redevient la question.
        val namesake = BotProfile(name = "ou", displayName = "Où est la clé ?")
        val branch = forkedConversation(simple, id = "c7", now = 9_999L)

        assertNull(botOfConversation(branch, listOf(namesake)))
        assertNull("le nom de base n'est que la question : ce n'est pas un nom", branch.botName())
    }

    @Test
    fun `une conversation renommee qui ne ressemble a aucun bot ne lie personne`() {
        val branch = forkedConversation(renamed, id = "c7b", now = 9_999L)

        assertEquals("le nom posé est le nom de base de la branche", "Projet Onyx", branch.botName())
        assertNull(botOfConversation(branch, roster))
    }

    // ---- 3. l'affichage ne change pas ------------------------------------------------------

    @Test
    fun `le titre de la branche garde le suffixe fork`() {
        val fork = forkedConversation(chat, id = "c8", now = 9_999L)

        assertEquals("coder (fork)", fork.title)
        // Le **nom** de l'en-tête reste celui de la branche : le suffixe est de l'affichage, il ne
        // disparaît que de la comparaison avec le libellé du bot.
        assertEquals("coder (fork)", fork.customName())
        assertNotEquals(fork.customName(), fork.botName())
    }

    @Test
    fun `le nom de base est le nom pose quand la conversation n est pas une branche`() {
        assertEquals("coder", chat.botName())
        // Une conversation dont le titre n'est que la première question n'a **ni** nom posé
        // **ni** nom de base : les deux règles écartent le même titre.
        assertNull(simple.customName())
        assertNull(simple.botName())
        // Une conversation **renommée** a les deux.
        assertEquals("Projet Onyx", renamed.customName())
        assertEquals("Projet Onyx", renamed.botName())
        // Une conversation neuve n'a pas de nom.
        assertNull(Conversation.start(id = "c9").botName())
    }

    @Test
    fun `le suffixe se retire meme pose plusieurs fois`() {
        val twice = forkedConversation(forkedConversation(chat, id = "c10", now = 1L), id = "c11", now = 2L)

        assertEquals("coder (fork) (fork)", twice.title)
        assertEquals("coder", twice.botName())
        assertTrue(botOfConversation(twice, roster) != null)
    }
}

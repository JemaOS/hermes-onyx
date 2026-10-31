package com.onyx.app.ui.bots

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.model.serve.GroupEvent
import com.onyx.app.ui.chat.components.cardSubtitleLines
import kotlinx.serialization.json.JsonObject
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le crayon de la barre du fil fait une conversation **de l'endroit où l'on est** : celle du bot
 * qu'on regarde (même profil, donc même visage et même couleur), celle de la salle où l'on est, ou
 * le comportement d'avant depuis la racine — sans jamais empiler deux conversations vides, et sans
 * jamais perdre celle qu'on quitte.
 *
 * Les décisions sont des fonctions pures, éprouvées ici sans appareil : c'est elles que l'écran
 * appelle, il n'y a donc pas deux règles à tenir.
 */
class PencilNewConversationTest {

    private val qa = BotProfile(name = "qa-tester", title = "QA-tester")
    private val writer = BotProfile(name = "writer", title = "writer")

    private fun said(id: String, text: String = "Un tour", at: Long = 10L) = Conversation(
        id = id,
        title = Conversation.titleFrom(text),
        createdAt = 1L,
        updatedAt = at,
        messages = listOf(ChatMessage(id = id + "-m", role = Role.USER, text = text, createdAt = at)),
    )

    private fun empty(id: String, title: String? = null) = Conversation(
        id = id,
        title = title ?: Conversation.DefaultTitle,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun botChat(id: String, bot: BotProfile) = Conversation(
        id = id,
        title = bot.label(),
        createdAt = 1L,
        updatedAt = 1L,
        serverSessionId = bot.chatId,
    )

    @Test
    fun `depuis la conversation d un bot le crayon ouvre une conversation du MEME bot`() {
        val current = said("c1").copy(title = qa.label(), serverSessionId = "chat-qa")

        val next = freshConversationForPencil(
            current = current,
            bot = qa,
            conversations = listOf(current),
            serverId = "server",
            now = 99L,
        )

        assertNotEquals("la conversation qu'on quitte n'est jamais reprise", current.id, next.id)
        assertEquals("elle est titrée du bot : c'est ce qui la rattache", qa.label(), next.title)
        assertEquals("le serveur de la conversation", "server", next.serverId)
        assertNull("une conversation neuve n'a pas encore de session serveur", next.serverSessionId)
        assertEquals("elle appartient bien au bot regardé", "qa-tester", botOfConversation(next, listOf(qa, writer))?.name)
        assertNotEquals("et jamais à un autre bot", "writer", botOfConversation(next, listOf(qa, writer))?.name)
    }

    @Test
    fun `le crayon ne fait jamais la conversation d un autre bot`() {
        val current = said("c1").copy(title = writer.label())

        // Le bot de la conversation ouverte est celui que le crayon prend pour cible.
        val target = botOfConversation(current, listOf(qa, writer))
        assertEquals("writer", target?.name)

        val next = freshConversationForPencil(current = current, bot = target, conversations = listOf(current))
        assertEquals("writer", botOfConversation(next, listOf(qa, writer))?.name)
    }

    @Test
    fun `une conversation vide et jamais servie est reprise au lieu d etre doublee`() {
        val current = botChat("c1", qa)

        val next = freshConversationForPencil(current = current, bot = qa, conversations = listOf(current))

        assertEquals("la même, pas une deuxième vide", "c1", next.id)
    }

    @Test
    fun `une conversation vide laissee de cote est reprise plutot qu une seconde vide`() {
        val current = said("c1").copy(title = qa.label())
        val leftAside = empty("c2", qa.label())

        val next = freshConversationForPencil(
            current = current,
            bot = qa,
            conversations = listOf(current, leftAside),
        )

        assertEquals("c2", next.id)
    }

    @Test
    fun `deux appuis de suite ne font jamais deux conversations vides`() {
        val current = said("c1").copy(title = qa.label())
        val first = freshConversationForPencil(current = current, bot = qa, conversations = listOf(current))
        val second = freshConversationForPencil(current = first, bot = qa, conversations = listOf(current, first))

        assertEquals(first.id, second.id)
    }

    @Test
    fun `depuis la racine le crayon garde le comportement d avant`() {
        val fresh = freshConversationForPencil(current = null, bot = null, conversations = emptyList(), serverId = "server")

        assertEquals("New Conversation", fresh.title)
        assertNull("aucun bot rattaché : l'en-tête reste celui de l'agent", botOfConversation(fresh, listOf(qa, writer)))

        // Une conversation simple déjà vide se reprend, sinon elle s'empilerait sans rien dire.
        assertEquals("c9", freshConversationForPencil(current = empty("c9"), bot = null).id)
    }

    @Test
    fun `un fil de groupe vierge ne s empile pas`() {
        assertFalse(groupStartsAnotherConversation(messages = emptyList(), asking = false, error = null))
        assertTrue(groupStartsAnotherConversation(messages = emptyList(), asking = true, error = null))
        assertTrue(groupStartsAnotherConversation(messages = emptyList(), asking = false, error = "refus"))
        assertTrue(
            groupStartsAnotherConversation(
                messages = listOf(GroupMessage(id = "m", bot = null, text = "Une question")),
                asking = false,
                error = null,
            ),
        )
    }

    @Test
    fun `une salle vierge ne s empile pas non plus`() {
        assertFalse(roomStartsAnotherConversation(emptyList()))
        assertTrue(
            roomStartsAnotherConversation(
                listOf(
                    GroupEvent(
                        seq = 1,
                        kind = "message.user",
                        actorKind = "user",
                        actorID = null,
                        payload = JsonObject(emptyMap()),
                        createdAtMillis = 1L,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `le sous-titre d un cartouche de reprise a droit a deux lignes`() {
        assertEquals("la phrase entière se lit, jamais coupée en plein mot", 2, cardSubtitleLines(isContinue = true))
        assertEquals("les autres cartes restent compactes", 1, cardSubtitleLines(isContinue = false))

        val source = File("src/main/java/com/onyx/app/ui/chat/components/StartScreen.kt").readText()
        assertTrue(source.contains("maxLines = cardSubtitleLines(isContinue)"))
    }

    @Test
    fun `la barre du fil passe par la decision contextuelle`() {
        val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
        val bar = File("src/main/java/com/onyx/app/ui/chat/components/OnyxTopBar.kt").readText()

        assertTrue("le crayon est celui du fil", bar.contains("onClick = onNewConversation"))
        // Le corps de « repartir de zéro » vit dans `startFreshConversation()` (écrit une fois,
        // pour le crayon, la commande et le menu) : l'écran y décide toujours selon le bot regardé.
        assertTrue("l'écran décide selon le bot regardé", app.contains("bot = openConversationBot"))
        assertTrue("et selon la liste des conversations", app.contains("conversations = history"))
        assertTrue("la conversation neuve est gardée", app.contains("conversationRepo.save(next)"))
        assertFalse(
            "le crayon ne crée plus une conversation anonyme",
            app.contains("onNewConversation = chatViewModel::newConversation"),
        )
    }

    @Test
    fun `une salle ouvre une conversation de la meme salle`() {
        val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
        val room = File("src/main/java/com/onyx/app/ui/bots/GroupRoomScreen.kt").readText()
        val group = File("src/main/java/com/onyx/app/ui/bots/GroupChatScreen.kt").readText()

        assertTrue("les membres de la salle deviennent ceux de la conversation", app.contains("groupPicked = room.members.map { it.name }.toSet()"))
        assertTrue("le nom de la salle aussi", app.contains("groupTitle = room.name"))
        assertTrue("la salle vierge ne s'empile pas", app.contains("roomStartsAnotherConversation(roomViewModel.state.value.rows)"))
        assertTrue("et le groupe repart à vide", app.contains("onNewConversation = groupViewModel::startNewConversation"))
        assertTrue("le crayon de la salle est bien câblé", room.contains("onClick = onNewConversation"))
        assertTrue("celui du groupe aussi", group.contains("onClick = onNewConversation"))
    }
}

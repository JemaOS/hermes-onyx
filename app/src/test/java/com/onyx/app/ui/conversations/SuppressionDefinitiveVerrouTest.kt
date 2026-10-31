package com.onyx.app.ui.conversations

import androidx.datastore.core.DataStore
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.model.serve.BotProfile
import com.onyx.app.data.repo.ConversationRepository
import com.onyx.app.ui.bots.botChat
import com.onyx.app.ui.bots.botChatToOpen
import com.onyx.app.ui.sidebar.sidebarRows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Le verrou de la suppression** — la plainte du propriétaire : « quand je reste appuyé sur une
 * conversation et que je clique sur supprimer, ça doit bien supprimer la conversation du bot, et je
 * ne dois plus du tout la retrouver ».
 *
 * Ce fichier n'emploie **que** l'API que la source d'avant portait déjà (`save`, `delete`,
 * `deleteAll`, `eraseAll`, `load`, `find`, `conversations`, `sidebarRows`, `botChat`) : il peut donc
 * tourner **sur la source d'avant** et y **mordre**.
 *
 * La morsure, telle qu'elle arrive : `ConversationRepository.delete` ôte « Onyx » du fichier, puis
 * l'enregistrement qui suit dans l'app — la conversation supprimée était celle qu'on regardait, donc
 * la fin du tour ou le passage à une conversation neuve la **réécrit** — la ramène. L'échec nomme la
 * survivante (« Onyx ») et l'endroit (la liste du volet, son compte, la recherche, l'écran du bot).
 *
 * Les cinq endroits que la plainte nomme sont vérifiés : la liste **et son compte**, la recherche,
 * l'écran du bot, le magasin local, et un **redémarrage simulé** (un dépôt neuf sur le même fichier).
 */
class SuppressionDefinitiveVerrouTest {

    /** Le magasin des conversations, en mémoire : le même objet tient lieu de fichier. */
    private class FakeConversationStore(
        initial: List<Conversation> = emptyList(),
    ) : DataStore<List<Conversation>> {

        private val flow = MutableStateFlow(initial)

        override val data: Flow<List<Conversation>> = flow

        override suspend fun updateData(
            transform: suspend (t: List<Conversation>) -> List<Conversation>,
        ): List<Conversation> {
            val next = transform(flow.value)
            flow.value = next
            return next
        }
    }

    /** La conversation du bot : c'est celle que la plainte nomme (« Onyx », deux fois dans la liste). */
    private val botChatId = "api-onyx-1"

    private fun botConversation() = Conversation(
        id = botChatId,
        title = "Onyx",
        createdAt = 1_000L,
        updatedAt = 10_000L,
        serverId = "srv",
        serverSessionId = botChatId,
        messages = listOf(
            ChatMessage(id = "m1", role = Role.USER, text = "Onyx", createdAt = 1_000L),
            ChatMessage(id = "m2", role = Role.ASSISTANT, text = "Je suis là.", createdAt = 9_000L),
        ),
    )

    private fun otherConversation() = Conversation(
        id = "conv-autre",
        title = "Où est la clé ?",
        createdAt = 2_000L,
        updatedAt = 20_000L,
        serverId = "srv",
        serverSessionId = "api-autre",
        messages = listOf(ChatMessage(id = "m3", role = Role.USER, text = "Où est la clé ?", createdAt = 2_000L)),
    )

    private val onyx = BotProfile(name = "default", title = "Onyx", isDefault = true, chatId = botChatId)

    /** Ce que l'app réécrit dans la foulée d'une suppression d'une conversation **ouverte**. */
    private suspend fun rewriteAsTheAppWould(repo: ConversationRepository, conversation: Conversation) {
        repo.save(
            Conversation.start(id = conversation.id, now = 30_000L).copy(
                serverId = conversation.serverId,
                serverSessionId = conversation.serverSessionId,
                messages = conversation.messages,
            ),
        )
    }

    // ——— Le verrou : la conversation supprimée n'est plus trouvable nulle part ———

    @Test
    fun `la conversation supprimee pendant qu elle est ouverte ne se retrouve nulle part`() = runBlocking {
        val store = FakeConversationStore()
        val repo = ConversationRepository(store)
        repo.save(otherConversation())
        repo.save(botConversation())
        val before = repo.conversations.value.size
        assertEquals("l'écran part de deux conversations", 2, before)

        // Le geste : appui long → « Supprimer » → la confirmation.
        repo.delete(botChatId)
        // …et ce que l'app fait juste après, parce que la conversation supprimée était celle qu'on
        // regardait : elle la réenregistre (fin de tour, passage à une conversation neuve).
        rewriteAsTheAppWould(repo, botConversation())

        assertEquals(
            "« Onyx » survit dans la liste du volet : le compte ne décrémente pas",
            before - 1,
            repo.conversations.value.size,
        )
        assertNull(
            "« Onyx » survit dans la liste du volet",
            repo.conversations.value.firstOrNull { it.id == botChatId },
        )
        assertNull("« Onyx » survit au `find` du magasin local", repo.find(botChatId))
        assertTrue(
            "« Onyx » se retrouve par la recherche",
            sidebarRows(repo.conversations.value, emptyMap(), emptyMap(), 100.0, query = "Onyx").none { it.id == botChatId },
        )
        assertNull("« Onyx » survit à l'écran du bot", botChat(onyx, repo.conversations.value))
        assertTrue("l'autre conversation, elle, reste", repo.find("conv-autre") != null)
    }

    @Test
    fun `le redemarrage ne ramene pas la conversation supprimee`() = runBlocking {
        val store = FakeConversationStore()
        val repo = ConversationRepository(store)
        repo.save(botConversation())
        repo.save(otherConversation())
        repo.delete(botChatId)

        // « Redémarrage » : un dépôt neuf sur le même fichier, comme au lancement suivant.
        val relu = ConversationRepository(store)
        relu.load()

        assertNull("« Onyx » revient au lancement suivant", relu.find(botChatId))
        assertEquals("le redémarrage laisse le reste", 1, relu.conversations.value.size)
        assertTrue("et l'autre est intacte", relu.find("conv-autre") != null)
    }

    @Test
    fun `un tour qui se termine apres la suppression ne la ramene pas non plus`() = runBlocking {
        val store = FakeConversationStore()
        val repo = ConversationRepository(store)
        val conversation = botConversation()
        repo.save(conversation)
        repo.delete(botChatId)

        // Le journal du tour écrit sa réponse dans la conversation supprimée (magasin de tours).
        repo.upsertMessage(botChatId, ChatMessage(id = "m9", role = Role.ASSISTANT, text = "Tard.", createdAt = 40_000L))
        // Et le fil entier est réenregistré comme s'il était encore ouvert.
        rewriteAsTheAppWould(repo, conversation)

        assertNull("la réponse écrite après coup ressuscite « Onyx »", repo.find(botChatId))
    }

    @Test
    fun `tout effacer ne laisse rien revenir`() = runBlocking {
        val store = FakeConversationStore()
        val repo = ConversationRepository(store)
        repo.save(botConversation())
        repo.save(otherConversation())
        repo.eraseAll()
        assertTrue("tout effacer vide la liste", repo.conversations.value.isEmpty())

        // Un chemin qui réécrit (une fin de tour, un rattachement de bot) ne ramène personne.
        rewriteAsTheAppWould(repo, botConversation())
        repo.save(otherConversation())

        assertTrue("« Tout effacer » ne laisse rien se réécrire", repo.conversations.value.isEmpty())
    }

    @Test
    fun `supprimer un lot marque tout le lot`() = runBlocking {
        val store = FakeConversationStore()
        val repo = ConversationRepository(store)
        repo.save(botConversation())
        repo.save(otherConversation())
        repo.deleteAll(setOf(botChatId, "conv-autre"))

        assertTrue("le lot a quitté la liste", repo.conversations.value.isEmpty())
        rewriteAsTheAppWould(repo, botConversation())
        repo.save(otherConversation())
        assertTrue("aucune du lot ne se réécrit", repo.conversations.value.isEmpty())
    }

    @Test
    fun `ouvrir le bot apres la suppression ouvre une conversation neuve, pas la supprimee`() = runBlocking {
        val store = FakeConversationStore()
        val repo = ConversationRepository(store)
        repo.save(botConversation())
        repo.delete(botChatId)

        // Ouvrir le bot : il n'a plus de conversation, donc l'app en ouvre une **neuve** — jamais
        // celle qu'on vient de supprimer.
        val opened = botChatToOpen(onyx, repo.conversations.value, serverId = "srv", mainName = "Onyx", now = 50_000L)
        assertNotEquals("la conversation rouverte est celle qu'on a supprimée", botChatId, opened.id)
        assertNull("« Onyx » ne se retrouve pas dans la liste", repo.conversations.value.firstOrNull { it.id == botChatId })
    }
}

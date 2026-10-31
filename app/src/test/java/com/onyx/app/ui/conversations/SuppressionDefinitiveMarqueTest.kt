package com.onyx.app.ui.conversations

import androidx.datastore.core.DataStore
import com.onyx.app.data.local.InMemoryDeletedConversations
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.repo.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La marque des conversations supprimées**, et le seul geste qui la défait.
 *
 * Une suppression est définitive : la marque est ce qui la rend durable, et elle vit **hors du
 * fichier des conversations** — donc elle survit à un redémarrage et au « Tout effacer ». Le seul
 * retour en arrière est le bandeau d'annulation, qui passe par `restore`.
 */
class SuppressionDefinitiveMarqueTest {

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

    private fun conversation(id: String, title: String) = Conversation(
        id = id,
        title = title,
        createdAt = 1_000L,
        updatedAt = 10_000L,
        serverId = "srv",
        messages = listOf(ChatMessage(id = "m-$id", role = Role.USER, text = title, createdAt = 1_000L)),
    )

    @Test
    fun `la marque survit a un redemarrage et a une reecriture`() = runBlocking {
        val store = FakeConversationStore()
        // Le même carnet d'un lancement à l'autre : c'est ce que les préférences du téléphone font.
        val marques = InMemoryDeletedConversations()
        val repo = ConversationRepository(store, marques)
        repo.save(conversation("api-onyx-1", "Onyx"))
        repo.delete("api-onyx-1")

        assertTrue("la suppression est marquée", "api-onyx-1" in marques.marked())

        // Un dépôt neuf sur le même fichier et le même carnet : le lancement suivant.
        val relu = ConversationRepository(store, marques)
        relu.load()
        assertNull("la marquée ne revient pas non plus par le fichier", relu.find("api-onyx-1"))

        // Et un `save` d'un autre chemin est refusé sans bruit.
        relu.save(conversation("api-onyx-1", "Onyx"))
        assertNull("un `save` tardif ne la réécrit pas", relu.find("api-onyx-1"))
    }

    @Test
    fun `l annulation du bandeau defait la marque, et elle seule`() = runBlocking {
        val store = FakeConversationStore()
        val marques = InMemoryDeletedConversations()
        val repo = ConversationRepository(store, marques)
        val onyx = conversation("api-onyx-1", "Onyx")
        repo.save(onyx)
        repo.delete("api-onyx-1")
        assertNull(repo.find("api-onyx-1"))

        // « Annuler » : la seule chose qui remet vraiment une conversation supprimée.
        repo.restore(onyx)

        assertEquals("le rétablissement ramène la conversation", "api-onyx-1", repo.find("api-onyx-1")?.id)
        assertTrue("et la marque est bien retirée", "api-onyx-1" !in marques.marked())
        assertEquals("il n'y a pas de doublon", 1, repo.conversations.value.size)
    }

    @Test
    fun `tout effacer marque tout ce qu il efface`() = runBlocking {
        val store = FakeConversationStore()
        val marques = InMemoryDeletedConversations()
        val repo = ConversationRepository(store, marques)
        repo.save(conversation("a", "Onyx"))
        repo.save(conversation("b", "Autre"))

        repo.eraseAll()

        assertEquals("les deux sont marquées", setOf("a", "b"), marques.marked())
    }
}

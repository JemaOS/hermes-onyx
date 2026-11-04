package com.onyx.app.ui.bots

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.repo.ConversationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Renommer un bot renomme ses conversations.**
 *
 * Le propriétaire a renommé « coder » en « coder_mk86 » : l'en-tête du fil a suivi, la liste des
 * conversations non — « c'est pas synchro de partout ». Le titre d'une conversation de bot **est**
 * le nom du bot : c'est lui qui la rattache à lui quand le `canonical_session` ne dit rien. Le
 * laisser en arrière ne salissait pas seulement la liste, il **détachait** la conversation de son
 * bot — plus de visage, plus de sous-titre.
 */
class BotRenamePropagationTest {

    /** Le magasin des conversations, en mémoire : le même objet tient lieu de fichier. */
    private class Store(initial: List<Conversation> = emptyList()) :
        androidx.datastore.core.DataStore<List<Conversation>> {

        private val flux = kotlinx.coroutines.flow.MutableStateFlow(initial)
        var ecritures = 0
            private set

        override val data: kotlinx.coroutines.flow.Flow<List<Conversation>> = flux

        override suspend fun updateData(
            transform: suspend (t: List<Conversation>) -> List<Conversation>,
        ): List<Conversation> {
            ecritures++
            val suivant = transform(flux.value)
            flux.value = suivant
            return suivant
        }
    }

    private fun conversation(
        id: String,
        title: String,
        profile: String? = null,
        session: String? = null,
    ) = Conversation.start(now = 1_000L).copy(
        id = id,
        title = title,
        botProfile = profile,
        serverSessionId = session,
    )

    @Test
    fun `les conversations du bot prennent son nouveau nom`() = runBlocking {
        val repo = ConversationRepository(Store())
        repo.save(conversation("a", "coder", profile = "coder"))
        repo.save(conversation("b", "coder", profile = "coder"))
        repo.save(conversation("c", "Autre sujet", profile = null))

        repo.retitleBot(profile = "coder", fromLabel = "coder", toLabel = "coder_mk86")

        val titres = repo.conversations.value.associate { it.id to it.title }
        assertEquals("coder_mk86", titres["a"])
        assertEquals("coder_mk86", titres["b"])
        assertEquals("une conversation d'un autre sujet ne bouge pas", "Autre sujet", titres["c"])
    }

    @Test
    fun `une conversation ecrite avant le rattachement suit par son titre`() = runBlocking {
        // Les versions d'avant `botProfile` ne gardaient que le titre : c'est lui qui rattache.
        val repo = ConversationRepository(Store())
        repo.save(conversation("vieux", "coder", profile = null))

        repo.retitleBot(profile = "coder", fromLabel = "coder", toLabel = "coder_mk86")

        assertEquals("coder_mk86", repo.conversations.value.single().title)
    }

    @Test
    fun `un second passage ne change plus rien`() = runBlocking {
        val store = Store()
        val repo = ConversationRepository(store)
        repo.save(conversation("a", "coder", profile = "coder"))

        repo.retitleBot(profile = "coder", fromLabel = "coder", toLabel = "coder_mk86")
        val apresPremier = store.ecritures
        repo.retitleBot(profile = "coder", fromLabel = "coder", toLabel = "coder_mk86")

        assertTrue("aucune seconde écriture", apresPremier == store.ecritures)
    }

    @Test
    fun `un nom vide ne renomme rien`() = runBlocking {
        val repo = ConversationRepository(Store())
        repo.save(conversation("a", "coder", profile = "coder"))

        repo.retitleBot(profile = "coder", fromLabel = "coder", toLabel = "   ")

        assertEquals("coder", repo.conversations.value.single().title)
    }
}

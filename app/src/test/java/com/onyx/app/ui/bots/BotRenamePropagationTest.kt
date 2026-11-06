package com.onyx.app.ui.bots

import com.onyx.app.data.model.Conversation
import com.onyx.app.data.repo.ConversationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Renommer un bot renomme ses conversations — et seulement les siennes.**
 *
 * Le propriétaire a renommé « coder » en « coder_mk86 » : l'en-tête du fil a suivi, la liste des
 * conversations non — « c'est pas synchro de partout ». Le titre d'une conversation de bot **est**
 * le nom du bot : c'est lui qui la rattache à lui quand le `canonical_session` ne dit rien. Le
 * laisser en arrière ne salissait pas seulement la liste, il **détachait** la conversation de son
 * bot — plus de visage, plus de sous-titre.
 *
 * **Et le défaut suivant était l'inverse** : renommer *un* bot renommait les autres. La règle de
 * rattachement retombait sur le **titre** pour les conversations écrites avant `botProfile` : deux
 * bots portant le même libellé — ou les fils d'un bot créés sous le nom d'un autre — partaient
 * ensemble. On ne reconnaît plus un bot par un titre : par son profil, ou par la session que le
 * serveur lui donne.
 *
 * **Et un fil que le propriétaire a nommé lui-même garde son nom.** Le renommage ne réécrit que les
 * titres qui n'étaient rien d'autre que le nom du bot.
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

        repo.retitleBot(
            profile = "coder",
            fromLabel = "coder",
            toLabel = "coder_mk86",
            canonicalSessionId = "sess-coder",
        )

        val titres = repo.conversations.value.associate { it.id to it.title }
        assertEquals("coder_mk86", titres["a"])
        assertEquals("coder_mk86", titres["b"])
        assertEquals("une conversation d'un autre sujet ne bouge pas", "Autre sujet", titres["c"])
    }

    @Test
    fun `une conversation ecrite avant le rattachement suit par sa session`() = runBlocking {
        // Les versions d'avant `botProfile` ne gardaient que le titre — mais c'est la session que
        // le serveur donne au bot qui dit à qui la conversation appartient, pas le libellé.
        val repo = ConversationRepository(Store())
        repo.save(conversation("vieux", "coder", profile = null, session = "sess-coder"))

        repo.retitleBot(
            profile = "coder",
            fromLabel = "coder",
            toLabel = "coder_mk86",
            canonicalSessionId = "sess-coder",
        )

        assertEquals("coder_mk86", repo.conversations.value.single().title)
    }

    @Test
    fun `une conversation d'un autre bot qui portait le meme titre ne bouge pas`() = runBlocking {
        // Le défaut signalé : renommer un bot changeait le nom des « autres bots coder ». Deux
        // profils différents, le même libellé — le titre ne dit pas à qui la conversation
        // appartient, la session si.
        val repo = ConversationRepository(Store())
        repo.save(conversation("autre", "coder", profile = null, session = "sess-autre"))
        repo.save(conversation("autre-nu", "coder", profile = null, session = null))
        repo.save(conversation("autre-profil", "coder", profile = "qa-tester"))

        repo.retitleBot(
            profile = "coder",
            fromLabel = "coder",
            toLabel = "coderr",
            canonicalSessionId = "sess-coder",
        )

        val titres = repo.conversations.value.associate { it.id to it.title }
        assertEquals("un autre bot garde son nom", "coder", titres["autre"])
        assertEquals("une conversation sans session n'est pas devinée", "coder", titres["autre-nu"])
        assertEquals("un profil nommé n'est jamais renommé", "coder", titres["autre-profil"])
    }

    @Test
    fun `un fil nomme par le proprietaire garde son nom`() = runBlocking {
        // Le bot s'appelait « coder » ; ce fil s'appelle « Refonte du parser » parce que son
        // propriétaire l'a décidé. Renommer le bot ne réécrit pas ce qu'il a écrit.
        val repo = ConversationRepository(Store())
        repo.save(conversation("perso", "Refonte du parser", profile = "coder"))

        repo.retitleBot(
            profile = "coder",
            fromLabel = "coder",
            toLabel = "coderr",
            canonicalSessionId = "sess-coder",
        )

        assertEquals("Refonte du parser", repo.conversations.value.single().title)
    }

    @Test
    fun `un second passage ne change plus rien`() = runBlocking {
        val store = Store()
        val repo = ConversationRepository(store)
        repo.save(conversation("a", "coder", profile = "coder"))

        repo.retitleBot(
            profile = "coder",
            fromLabel = "coder",
            toLabel = "coder_mk86",
            canonicalSessionId = "sess-coder",
        )
        val apresPremier = store.ecritures
        repo.retitleBot(
            profile = "coder",
            fromLabel = "coder",
            toLabel = "coder_mk86",
            canonicalSessionId = "sess-coder",
        )

        assertTrue("aucune seconde écriture", apresPremier == store.ecritures)
    }

    @Test
    fun `un nom vide ne renomme rien`() = runBlocking {
        val repo = ConversationRepository(Store())
        repo.save(conversation("a", "coder", profile = "coder"))

        repo.retitleBot(
            profile = "coder",
            fromLabel = "coder",
            toLabel = "   ",
            canonicalSessionId = "sess-coder",
        )

        assertEquals("coder", repo.conversations.value.single().title)
    }
}

package com.onyx.app

import androidx.datastore.core.DataStore
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.data.repo.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * **Le fichier des conversations n'est plus réécrit à chaque sauvegarde.**
 *
 * `store.updateData` réécrit **tout** le fichier : chaque conversation locale avec son historique
 * complet. Et `persistCurrent()` est appelé à seize endroits, plusieurs fois par tour. Sur un fil de
 * vingt pages, un tour qui publie vingt fois recopiait donc vingt fois tout le texte — sur un thread
 * de fond, mais en concurrence avec l'écriture du flux, d'où les saccades qui grandissent avec la
 * taille de l'app.
 *
 * Ce que ce test verrouille, et qui n'est **pas** un temps (il dépend de la machine) :
 *
 *  1. une rafale de sauvegardes concurrentes ne produit **pas** une écriture par sauvegarde ;
 *  2. le fichier finit **toujours** sur l'état le plus récent — la coalescence ne perd rien ;
 *  3. `flush()` rend la main une fois l'écriture posée.
 */
class ConversationWriteCoalescingTest {

    private class CountingStore : DataStore<List<Conversation>> {
        private val flow = MutableStateFlow<List<Conversation>>(emptyList())
        var writes = 0
            private set
        /** Le contenu réellement écrit en dernier (ce que le disque porterait). */
        var lastWritten: List<Conversation> = emptyList()
            private set

        override val data: Flow<List<Conversation>> = flow

        override suspend fun updateData(
            transform: suspend (t: List<Conversation>) -> List<Conversation>,
        ): List<Conversation> {
            // Le disque n'est pas instantané : cette latence est ce qui rend la coalescence utile.
            Thread.sleep(4)
            val next = transform(flow.value)
            flow.value = next
            lastWritten = next
            writes++
            return next
        }
    }

    private fun conversation(id: String, text: String) = Conversation(
        id = id,
        title = id,
        createdAt = 1_000L,
        updatedAt = 10_000L,
        messages = listOf(ChatMessage(id = "$id-1", role = Role.USER, text = text, createdAt = 1_000L)),
    )

    @Test
    fun `une rafale de sauvegardes ne fait pas une ecriture par sauvegarde`() = runBlocking {
        val store = CountingStore()
        val repo = ConversationRepository(store)

        // Ce qu'un tour fait vraiment : la même conversation réécrite à chaque étape.
        repeat(20) { step ->
            repo.save(conversation("c1", "étape $step " + "texte ".repeat(200)))
        }
        repo.flush()

        assertTrue(
            "vingt sauvegardes ne doivent pas coûter vingt réécritures du fichier (mesuré : ${store.writes})",
            store.writes <= 4,
        )
        assertEquals(
            "et le disque porte le dernier état, pas un état intermédiaire",
            true,
            store.lastWritten.single().messages.single().text.contains("étape 19"),
        )
    }

    @Test
    fun `le dernier etat est toujours ecrit, meme quand les sauvegardes se chevauchent`() = runBlocking {
        val store = CountingStore()
        val repo = ConversationRepository(store)

        // Des sauvegardes **concurrentes** (le viewModel et les rappels de tour publient en parallèle).
        val jobs = (0 until 12).map { step ->
            launch(kotlinx.coroutines.Dispatchers.Default) {
                repo.save(conversation("c$step", "contenu $step"))
            }
        }
        jobs.forEach { it.join() }
        repo.flush()

        assertEquals(
            "toutes les conversations enregistrées sont sur le disque",
            12,
            store.lastWritten.size,
        )
        assertTrue("et l'écriture a bien eu lieu", store.writes >= 1)
    }


    @Test
    fun `la meme instance de messages ne coute pas une comparaison de tout le texte`() = runBlocking {
        // Ce que le viewModel envoie maintenant quand rien ne s'écrit : la **même** liste. Le dépôt
        // reconnaît « rien n'a changé » sans comparer un seul caractère — avant, `previous ==
        // conversation` comparait tous les messages, tout leur texte et toutes leurs sorties d'outil.
        val store = CountingStore()
        val repo = ConversationRepository(store)
        val heavy = conversation("c1", "texte ".repeat(2_000))
        repo.save(heavy)
        repo.flush()
        val writes = store.writes

        repeat(10) { repo.save(heavy.copy()) }   // copies de surface, liste partagée
        repo.flush()

        assertEquals("aucune réécriture pour un contenu identique", writes, store.writes)
    }

    @Test
    fun `un message modifie est bien ecrit`() = runBlocking {
        val store = CountingStore()
        val repo = ConversationRepository(store)
        val initial = conversation("c1", "avant")
        repo.save(initial)
        repo.flush()

        val edited = initial.copy(
            messages = listOf(initial.messages.single().copy(text = "après")),
        )
        repo.save(edited)
        repo.flush()

        assertEquals(
            "une édition change le fichier",
            "après",
            store.lastWritten.single().messages.single().text,
        )
    }

    @Test
    fun `une conversation inchangee n ecrit pas du tout`() = runBlocking {
        val store = CountingStore()
        val repo = ConversationRepository(store)
        val same = conversation("c1", "identique")

        repo.save(same)
        repo.flush()
        val afterFirst = store.writes
        repeat(5) { repo.save(same) }
        repo.flush()

        assertEquals("la même valeur ne réécrit pas le fichier", afterFirst, store.writes)
    }

    @Test
    fun `la coalescence ne coute pas un delai visible sur une sauvegarde isolee`() = runBlocking {
        val store = CountingStore()
        val repo = ConversationRepository(store)

        val elapsed = measureTimeMillis {
            repo.save(conversation("c1", "une seule sauvegarde"))
            repo.flush()
        }
        assertEquals("une écriture, sans attente ajoutée", 1, store.writes)
        assertTrue("et elle est immédiate (mesuré : ${elapsed} ms)", elapsed < 1_000)
    }
}
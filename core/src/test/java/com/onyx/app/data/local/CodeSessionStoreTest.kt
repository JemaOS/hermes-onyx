package com.onyx.app.data.local

import com.onyx.app.data.model.CodeSession
import com.onyx.app.data.model.CodeStatus
import com.onyx.app.data.model.edited
import com.onyx.app.data.model.renamed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La persistance locale des sessions de code : ce qui est écrit là **survit à un redémarrage**, et
 * le statut gardé est le dernier fait observé — jamais une valeur posée d'avance.
 */
class CodeSessionStoreTest {

    private fun record(id: String, status: CodeStatus = CodeStatus.RUNNING) = CodeSession(
        id = id,
        title = "Décris un changement $id",
        branch = "code/decris-un-changement-$id",
        repoPath = "/home/john/projet",
        worktreePath = "/home/john/projet/.worktrees/$id",
        createdAtMillis = 1_700_000_000_000L,
        status = status,
        statusAtMillis = 1_700_000_050_000L,
        sessionKey = "20261027_$id",
    )

    @Test
    fun `une session ecrite se relit entiere, dans une autre instance`() {
        val storage = InMemoryCodeSessionStorage()
        CodeSessionStore(storage).save("serveur-1", record("a", CodeStatus.READY))

        // Un magasin neuf sur la même mémoire : c'est ce qui se passe au redémarrage.
        val relu = CodeSessionStore(storage).sessions("serveur-1")
        assertEquals(1, relu.size)
        val one = relu.single()
        assertEquals("code/decris-un-changement-a", one.branch)
        assertEquals("/home/john/projet/.worktrees/a", one.worktreePath)
        assertEquals(CodeStatus.READY, one.status)
        assertEquals(1_700_000_050_000L, one.statusAtMillis)
        assertEquals("20261027_a", one.sessionKey)
        assertTrue(one.local)
    }

    @Test
    fun `les sessions sont rangees par connexion`() {
        val storage = InMemoryCodeSessionStorage()
        val store = CodeSessionStore(storage)
        store.save("serveur-1", record("a"))
        store.save("serveur-2", record("b"))

        assertEquals(listOf("a"), store.sessions("serveur-1").map { it.id })
        assertEquals(listOf("b"), store.sessions("serveur-2").map { it.id })
    }

    @Test
    fun `enregistrer deux fois le meme identifiant remplace la fiche`() {
        val storage = InMemoryCodeSessionStorage()
        val store = CodeSessionStore(storage)
        store.save("s", record("a", CodeStatus.RUNNING))
        store.save("s", record("a", CodeStatus.READY))

        assertEquals(1, store.sessions("s").size)
        assertEquals(CodeStatus.READY, store.sessions("s").single().status)
    }

    @Test
    fun `une lecture vide ou illisible ne rend rien plutot que d echouer`() {
        assertEquals(emptyList<CodeSession>(), CodeSessionStore(InMemoryCodeSessionStorage()).sessions("s"))
        assertEquals(emptyList<CodeSession>(), CodeSessionStore(InMemoryCodeSessionStorage("{")).sessions("s"))
    }

    // MARK: - Renommer, modifier, relancer, supprimer : ce qui survit à une relance de l'app

    @Test
    fun `un renommage est ecrit, et se relit dans une autre instance`() {
        val storage = InMemoryCodeSessionStorage()
        val store = CodeSessionStore(storage)
        store.save("s", record("a"))
        // L'écran renomme : la fiche réécrite, tout de suite.
        store.save("s", store.sessions("s").single().renamed("Autre tâche"))

        // Un magasin neuf sur la même mémoire : c'est le lancement suivant.
        val relu = CodeSessionStore(storage).sessions("s").single()
        assertEquals("Autre tâche", relu.title)
        assertEquals("a", relu.id)
    }

    @Test
    fun `une modification complete survit a une relance`() {
        val storage = InMemoryCodeSessionStorage()
        val store = CodeSessionStore(storage)
        store.save("s", record("a"))
        store.save(
            "s",
            store.sessions("s").single().edited(
                title = "Autre chose",
                branch = "code/autre-chose",
                prompt = "Corrige la page de connexion",
                repoPath = "/home/john/autre",
                worktreePath = null,
            ),
        )

        val relu = CodeSessionStore(storage).sessions("s").single()
        assertEquals("Autre chose", relu.title)
        assertEquals("code/autre-chose", relu.branch)
        assertEquals("Corrige la page de connexion", relu.prompt)
        assertEquals("/home/john/autre", relu.repoPath)
        assertNull(relu.worktreePath)
    }

    @Test
    fun `la demande gardee se relit, et son absence reste une absence`() {
        val storage = InMemoryCodeSessionStorage()
        val store = CodeSessionStore(storage)
        store.save("s", record("a").copy(prompt = "Bonjour"))
        store.save("s", record("b").copy(prompt = null))

        val relu = CodeSessionStore(storage).sessions("s").associateBy { it.id }
        assertEquals("Bonjour", relu.getValue("a").prompt)
        assertNull("une fiche sans demande n'en invente pas", relu.getValue("b").prompt)
    }

    @Test
    fun `une fiche supprimee ne reapparait pas apres une relance`() {
        val storage = InMemoryCodeSessionStorage()
        val store = CodeSessionStore(storage)
        store.save("s", record("a", CodeStatus.READY))
        store.save("s", record("b", CodeStatus.RUNNING))
        store.remove("s", "b")

        // Le magasin neuf du lancement suivant : la fiche supprimée est bien partie, et l'autre est
        // intacte — la suppression ne touche que ce qu'on a visé.
        val relu = CodeSessionStore(storage).sessions("s")
        assertEquals(listOf("a"), relu.map { it.id })
        assertEquals(CodeStatus.READY, relu.single().status)
    }

    @Test
    fun `supprimer la derniere fiche laisse la connexion vide`() {
        val storage = InMemoryCodeSessionStorage()
        val store = CodeSessionStore(storage)
        store.save("s", record("a"))
        store.remove("s", "a")

        assertEquals(emptyList<CodeSession>(), CodeSessionStore(storage).sessions("s"))
        // Et les autres connexions ne sont pas emportées avec elle.
        store.save("autre", record("b"))
        assertEquals(listOf("b"), CodeSessionStore(storage).sessions("autre").map { it.id })
    }
}

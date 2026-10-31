package com.onyx.app.ui.sidebar

import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Conversation
import com.onyx.app.data.model.Role
import com.onyx.app.localization.OnyxLocalization
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Ce que la recherche interroge**, et ce que le volet en dit.
 *
 * C'est le point du lot où il était le plus facile de mentir : la référence annonce « les titres,
 * les identifiants de session et le texte des messages », alors que notre recherche ne lit que deux
 * de ces trois choses. Recopier sa phrase aurait promis ce que le code ne fait pas. Ces tests
 * tiennent donc **les deux bouts** — la phrase affichée, et la fonction qui filtre — pour qu'ils ne
 * puissent plus diverger sans qu'un test le dise.
 *
 * Le second point est l'**anti-rebond** : une requête par frappe ferait clignoter la liste et
 * reconstruirait trente fois le même filtre ; seule la frappe posée compte.
 */
class SidebarSearchTest {

    private fun conversation(
        id: String,
        title: String,
        sessionId: String? = null,
        messages: List<ChatMessage> = emptyList(),
    ) = Conversation(
        id = id,
        title = title,
        createdAt = 0L,
        updatedAt = 0L,
        serverSessionId = sessionId,
        messages = messages,
    )

    private fun message(text: String) = ChatMessage(
        id = "m-$text",
        role = Role.ASSISTANT,
        text = text,
        createdAt = 0L,
    )

    private fun matches(conversation: Conversation, needle: String): Boolean =
        sidebarMatches(conversation, needle, includeMessages = true)

    @Test
    fun `la recherche regarde le titre et le texte des messages, jamais l identifiant de session`() {
        val quartz = conversation(
            id = "c1",
            title = "Rapport de quartz",
            sessionId = "session-7f3a91",
            messages = listOf(message("Le fournisseur a répondu"), message("Rien d'autre")),
        )
        val other = conversation(
            id = "c2",
            title = "Balade du week-end",
            sessionId = "session-2b8c44",
            messages = listOf(message("Le col était fermé")),
        )
        val all = listOf(quartz, other)
        val now = 1_000.0

        // Un mot du **titre** retient la ligne.
        assertEquals(listOf("c1"), sidebarRows(all, emptyMap(), emptyMap(), now, query = "quartz").map { it.id })
        // Un mot du **texte d'un message** aussi — c'est la seconde moitié de la phrase affichée.
        assertEquals(listOf("c2"), sidebarRows(all, emptyMap(), emptyMap(), now, query = "col").map { it.id })
        // L'**identifiant de session**, lui, n'est pas interrogé : la phrase ne doit donc pas le
        // promettre. Si quelqu'un l'ajoute un jour, ce test tombe et la phrase devra être revue.
        assertTrue("l'identifiant de session ne doit pas être interrogé", sidebarRows(all, emptyMap(), emptyMap(), now, query = "7f3a91").isEmpty())
        assertTrue("ni le nom du serveur", sidebarRows(all, emptyMap(), emptyMap(), now, query = "session-").isEmpty())
        // Et la casse ne compte pas : la frappe n'est pas exacte.
        assertTrue(matches(quartz, "QUARTZ"))
    }

    @Test
    fun `la phrase de l etat vide dit la meme chose que la fonction qui filtre`() {
        val shown = OnyxLocalization.translate(SearchScopeSentence, Locale.FRANCE)
        // Elle est **traduite** (l'app parle français), et elle nomme les deux champs lus.
        assertNotEquals("la phrase de l'état vide n'est pas traduite", SearchScopeSentence, shown)
        assertTrue("elle doit nommer les titres : $shown", shown.contains("titres", ignoreCase = true))
        assertTrue("elle doit nommer le texte des messages : $shown", shown.contains("messages", ignoreCase = true))
        // Et **pas** les identifiants de session, que `sidebarMatches` ne lit pas.
        assertFalse("elle ne peut pas promettre les identifiants : $shown", shown.contains("identifiant", ignoreCase = true))

        // La même vérité, mesurée sur le code cette fois : titre et messages retiennent, une chaîne
        // qui n'est ni l'un ni l'autre ne retient rien.
        val line = conversation(
            id = "c1",
            title = "Rapport de quartz",
            sessionId = "session-7f3a91",
            messages = listOf(message("Le fournisseur a répondu")),
        )
        assertTrue(sidebarMatches(line, "quartz", includeMessages = true))
        assertTrue(sidebarMatches(line, "fournisseur", includeMessages = true))
        assertFalse(sidebarMatches(line, "7f3a91", includeMessages = true))
    }

    @Test
    fun `le titre de l etat vide est distinct de celui d une liste vide`() {
        // « Aucun résultat » n'est **pas** « aucune conversation » : deux situations, deux textes.
        val fr = OnyxLocalization.translate(SearchEmptyTitle, Locale.FRANCE)
        assertNotEquals("le titre de l'état vide n'est pas traduit", SearchEmptyTitle, fr)
        assertNotEquals(fr, OnyxLocalization.translate("No conversations yet.", Locale.FRANCE))
    }

    @Test
    fun `une rafale de frappe ne rend que la derniere requete`() = runBlocking {
        // Ce que l'écran fait : chaque caractère **relance** l'effet, donc annule l'attente en cours.
        val settled = mutableListOf<String>()
        var job: Job? = null
        for (typed in listOf("t", "te", "tes", "test")) {
            job?.cancel()
            job = launch { debouncedSearchQuery(typed) { settled += it } }
            delay(40) // une frappe toutes les 40 ms, bien en dessous de l'attente
        }
        job?.join()
        assertEquals("une seule requête doit être appliquée", listOf("test"), settled)
    }

    @Test
    fun `une requete posee est appliquee apres l attente`() = runBlocking {
        val settled = mutableListOf<String>()
        val began = System.currentTimeMillis()
        debouncedSearchQuery("quartz") { settled += it }
        val elapsed = System.currentTimeMillis() - began
        assertEquals(listOf("quartz"), settled)
        assertTrue("l'attente doit être réelle : ${elapsed}ms", elapsed >= SearchDebounceMillis - 40)
    }

    @Test
    fun `effacer le texte ne fait pas attendre la liste`() = runBlocking {
        // La requête vide est rendue **tout de suite** : sinon la liste resterait filtrée un quart de
        // seconde après que la personne a vidé le champ.
        val settled = mutableListOf<String>()
        val began = System.currentTimeMillis()
        debouncedSearchQuery("") { settled += it }
        assertEquals(listOf(""), settled)
        assertTrue("vider le champ ne doit pas attendre", System.currentTimeMillis() - began < SearchDebounceMillis)
    }
}

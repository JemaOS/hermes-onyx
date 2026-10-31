package com.onyx.app.ui.sidebar

import com.onyx.app.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **« Archives » lit un état qui existe déjà.** La règle de l'écran est pure, donc mesurée ici
 * sans appareil : les rangées sont les conversations **archivées** de cet appareil, de la plus
 * récente à la plus ancienne — et rien d'autre. C'est ce qui distingue une destination réelle
 * d'une coquille : elle a du contenu à montrer, et un état vide **vrai** quand il n'y en a pas.
 */
class ArchivedConversationsTest {

    private fun conversation(
        id: String,
        title: String,
        updatedAt: Long,
        archived: Boolean,
    ) = Conversation(
        id = id,
        title = title,
        createdAt = updatedAt - 1_000,
        updatedAt = updatedAt,
        archived = archived,
    )

    @Test
    fun `la vue ne garde que les conversations archivees`() {
        val liste = listOf(
            conversation("a", "En cours", 300, archived = false),
            conversation("b", "Rangée", 200, archived = true),
            conversation("c", "Épinglée", 400, archived = false),
            conversation("d", "Rangée aussi", 100, archived = true),
        )
        val archivees = archivedConversations(liste)
        assertEquals(listOf("b", "d"), archivees.map { it.id })
        assertTrue("aucune conversation active ne s'y glisse", archivees.all { it.archived })
    }

    @Test
    fun `la plus recente vient en tete`() {
        val liste = listOf(
            conversation("vieille", "Vieille", 100, archived = true),
            conversation("neuve", "Neuve", 900, archived = true),
            conversation("milieu", "Milieu", 500, archived = true),
        )
        assertEquals(listOf("neuve", "milieu", "vieille"), archivedConversations(liste).map { it.id })
    }

    @Test
    fun `sans archive la vue est vide, et c est un fait`() {
        // L'état vide de l'écran (« Rien d'archivé ») : rien de rangé est un fait, pas un défaut.
        val liste = listOf(
            conversation("a", "Une", 300, archived = false),
            conversation("b", "Deux", 200, archived = false),
        )
        assertTrue(archivedConversations(liste).isEmpty())
        assertTrue(archivedConversations(emptyList()).isEmpty())
    }

    @Test
    fun `l encre de l action des archives est mesuree, jamais l accent brut`() {
        // Le défaut relevé sur l'écran neuf : « Désarchiver » (le seul geste de la page) peignait
        // l'accent **brut** (`theme.accent`) sur la page quasi blanche — soit ~3,4:1, sous le seuil
        // du texte (4,5:1). Comme partout dans le volet, l'accent doit être **écarté de son fond du
        // plus petit pas qui suffit** (`sidebarInk(theme.accent, ground, OnyxContrast.TEXT_MIN)`).
        val screen = java.io.File("src/main/java/com/onyx/app/ui/sidebar/ArchivedScreen.kt").readText()
        assertTrue(
            "l'action des archives doit peindre l'accent mesuré, pas l'accent brut",
            screen.contains("sidebarInk(theme.accent, theme.pageColor, OnyxContrast.TEXT_MIN)"),
        )
        assertTrue(
            "le texte de l'action ne doit pas retomber sur l'accent brut",
            !screen.contains("color = theme.accent,"),
        )
    }
}

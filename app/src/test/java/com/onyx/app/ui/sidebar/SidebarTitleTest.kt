package com.onyx.app.ui.sidebar

import com.onyx.app.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **Le nom affiché d'un fil de bot, remis d'aplomb.**
 *
 * Renommer « coder » en « coderr » a réécrit le titre des fils d'**autres** bots — « ça change tous
 * les bots coders le nom aussi ». La règle d'écriture est corrigée
 * (`ConversationRepository.retitleBot`), mais ce qui est déjà écrit sur le téléphone y reste : la
 * liste doit donc savoir reconnaître un titre qui n'était qu'un nom de bot.
 *
 * Trois cas, et le troisième compte autant que les autres : le titre que le propriétaire a écrit
 * lui-même ne doit **jamais** être réécrit.
 */
class SidebarTitleTest {

    private fun conversation(title: String, profile: String? = "coder") =
        Conversation.start(now = 1_000L).copy(id = "c", title = title, botProfile = profile)

    /** Le roster : le bot visé vient d'être renommé, un autre bot existe à côté. */
    private val labels = mapOf(
        "coder" to "coderr",
        "qa-tester" to "QA Tester",
    )
    private val known = setOf("coderr", "coder", "QA Tester", "qa-tester")

    private fun title(conversation: Conversation, identity: String = conversation.botProfile.orEmpty()) =
        sidebarTitle(conversation, identity, { labels[it].orEmpty() }, known)

    @Test
    fun `le nom de son propre bot est celui du roster`() {
        // Le fil porte encore l'ancien nom : on montre le vivant, sans écrire sur le disque.
        assertEquals("coderr", title(conversation("coder")))
    }

    @Test
    fun `un fil d'un autre bot qui portait le nom du premier est remis`() {
        // Le dégât collatéral : ce fil est celui de qa-tester, mais son titre a été réécrit en
        // « coderr » par le renommage. Il doit retrouver le nom de SON bot.
        assertEquals(
            "QA Tester",
            title(conversation("coderr", profile = "qa-tester")),
        )
    }

    @Test
    fun `un titre ecrit par le proprietaire n'est pas touche`() {
        // « Refonte du parser » n'est le nom d'aucun bot : c'est un choix, et il reste.
        assertEquals(
            "Refonte du parser",
            title(conversation("Refonte du parser")),
        )
    }

    @Test
    fun `un profil inconnu du roster garde son titre`() {
        assertEquals("coder", sidebarTitle(conversation("coder"), "inconnu", { labels[it].orEmpty() }, known))
    }

    @Test
    fun `un titre vide prend le nom du bot`() {
        assertEquals("coderr", title(conversation("   ")))
    }

    @Test
    fun `sans roster, rien n'est devine`() {
        assertEquals("coder", sidebarTitle(conversation("coder"), "coder", null, emptySet()))
    }
}
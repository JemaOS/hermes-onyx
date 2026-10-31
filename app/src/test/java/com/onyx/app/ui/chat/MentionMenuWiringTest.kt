package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le branchement de la liste des mentions `@` : le fil la lit sur le roster des bots, la pose
 * au-dessus du champ, n'écrit que le mot en cours, et laisse aux salles de groupe la liste qu'elles
 * ont déjà (`GroupComposer.tags`).
 *
 * Vérifications de source : elles portent sur le câblage, pas sur le rendu — c'est la capture
 * d'émulateur qui juge le rendu.
 */
class MentionMenuWiringTest {
    private val screen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val app = File("src/main/java/com/onyx/app/ui/OnyxApp.kt").readText()
    private val groupChat = File("src/main/java/com/onyx/app/ui/bots/GroupChatScreen.kt").readText()
    private val groupComposer = File("src/main/java/com/onyx/app/ui/bots/GroupComposer.kt").readText()

    @Test
    fun `le fil pose la liste des mentions au-dessus du champ`() {
        assertTrue(screen.contains("MentionSuggestions("))
        assertTrue(screen.contains("BotMentions.menu("))
        // Un tap finit le mot en cours dans le brouillon, et rien d'autre n'est réécrit.
        assertTrue(screen.contains("onInputChange(BotMentions.complete(state.input, bot))"))
        // Les bots proposés sont ceux du roster du serveur, comme la section « Bots » les montre.
        assertTrue(app.contains("mentionBots = mentionRoster,"))
        assertTrue(app.contains("BotMentions.rosterForMentions(bots,"))
        // La liste occupe la place du bouton « Jump to latest », qui s'efface devant elle.
        assertTrue(screen.contains("state.queued.isEmpty() && mentionSuggestions.isEmpty()"))
    }

    @Test
    fun `Echap ferme la liste sans toucher au brouillon`() {
        assertTrue(screen.contains("chatKeyOf(event.key) == ChatKey.ESCAPE"))
        assertTrue(screen.contains("mentionDismissed = mentionToken"))
        assertTrue(screen.contains("if (mentionToken == null) mentionDismissed = null"))
    }

    @Test
    fun `la salle de groupe garde sa propre liste de mentions`() {
        // La référence ne pose la liste des bots que dans le composeur d'un fil ; une salle a la
        // sienne, avec ses membres et leurs poignées.
        assertFalse(groupChat.contains("MentionSuggestions("))
        assertFalse(groupComposer.contains("MentionSuggestions("))
        assertTrue(groupComposer.contains("replaceTagToken(draft, bot.handle)"))
    }

    @Test
    fun `la doublure de roster ne prend la place du serveur que sur la demande de lancement`() {
        assertTrue(app.contains("getBooleanExtra(EXTRA_DEMO_BOTS, false)"))
        assertTrue(app.contains("if (demoBotsRun) BotRosterStandIn.bots() else botsState.bots"))
    }
}

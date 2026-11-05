package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La carte de prise en main a UN seul endroit : une ligne du fil.**
 *
 * Trois placements ont été essayés, et deux étaient faux :
 *
 * 1. dans la rangée d'outils de `MessageRow` — elle recouvrait les dernières bulles ;
 * 2. dans le bloc du compositeur — « Guider la réponse… » passait dessous (capture du
 *    2026-11-05) ;
 * 3. **une ligne du fil** (`item(key = "browser-invite")`) — c'est le bon : une invitation à
 *    prendre la main est un contenu de la conversation, elle défile avec lui.
 *
 * Le défaut que ce garde-fou empêche : après être passée en (3) dans un commit, la carte a été
 * remise en (2) par un autre — elle s'affichait alors **deux fois**, une ligne du fil *et* une
 * bande au-dessus du champ. L'unicité du placement est donc une propriété testée, pas une
 * intention.
 */
class BrowserInviteSinglePlacementTest {

    private val ecran = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val rangee = File("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt").readText()

    @Test
    fun `la carte est montee exactement une fois`() {
        val occurrences = Regex("""BrowserInvite\s*\(""").findAll(ecran).count()
        assertEquals(
            "une seule invocation de BrowserInvite dans l'écran (sinon elle s'affiche deux fois)",
            1,
            occurrences,
        )
    }

    @Test
    fun `elle est une ligne du fil`() {
        assertTrue(
            "émise comme élément de la liste",
            ecran.contains("item(key = \"browser-invite\")"),
        )
        val item = ecran.indexOf("item(key = \"browser-invite\")")
        val invite = ecran.indexOf("BrowserInvite(")
        assertTrue("elle est portée par cet élément", invite > item)
    }

    @Test
    fun `elle n est ni dans le fil d outils ni dans la pile du prompt`() {
        assertFalse(
            "aucune carte de prise en main dans une rangée d'outils",
            rangee.contains("BrowserTakeoverCard"),
        )
        assertFalse(
            "ni la détection d'outil qui la déclenchait",
            rangee.contains("estUneDemandeDeMain"),
        )
        // Le bloc du compositeur : de sa mesure de hauteur jusqu'à la LazyColumn du fil qui suit.
        val debutPile = ecran.indexOf("modifier = Modifier\n                    .align(Alignment.BottomCenter)")
        assertTrue("la pile du bas est trouvée", debutPile > 0)
        val pile = ecran.substring(debutPile, (debutPile + 4000).coerceAtMost(ecran.length))
        assertFalse(
            "la pile du prompt ne porte pas la carte",
            pile.contains("BrowserInvite("),
        )
    }
}
package com.onyx.app.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **La carte du navigateur vit dans le fil, jamais dans le champ.**
 *
 * Posée dans le bloc du compositeur, elle se superposait au champ : « Guider la réponse… »
 * passait dessous et se lisait à moitié. Une invitation à prendre la main est un contenu de la
 * conversation — elle se place après le dernier message et défile avec lui.
 */
class BrowserInvitePlacementTest {

    private val ecran = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()

    @Test
    fun `la carte est une ligne du fil`() {
        assertTrue(
            "elle est émise comme élément de la liste du fil",
            ecran.contains("item(key = \"browser-invite\")"),
        )
        assertTrue(
            "et elle porte l'invitation",
            ecran.contains("BrowserInvite("),
        )
    }

    @Test
    fun `elle n est plus dans le bloc du champ`() {
        val debut = ecran.indexOf("modifier = Modifier.onSizeChanged { composerHeightPx = it.height }")
        assertTrue("le bloc du compositeur est trouvé", debut > 0)
        val bloc = ecran.substring(debut, (debut + 900).coerceAtMost(ecran.length))
        assertFalse(
            "la carte n'est pas mesurée avec le champ",
            bloc.contains("BrowserInvite("),
        )
    }

    @Test
    fun `la carte n est pas dessinee dans une rangee d outils`() {
        // Une première version la posait dans la rangée d'outils de `MessageRow` : elle recouvrait
        // les dernières bulles. Une deuxième l'a mise dans le puits du champ : elle recouvrait la
        // première ligne du prompt. Elle est maintenant une ligne du fil — ni l'un ni l'autre.
        val rangee = File("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt").readText()
        assertFalse("aucune carte de prise en main dans une rangée", rangee.contains("BrowserTakeoverCard"))
        assertFalse("ni la détection d'outil qui la déclenchait", rangee.contains("estUneDemandeDeMain"))
    }
}

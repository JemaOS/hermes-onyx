package com.onyx.app.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** L'écran « Ordinateur » parle français, jusqu'aux verbes de la dernière action. */
class ComputerLocalizationTest {

    @Test
    fun `l'ecran ordinateur parle francais`() {
        val cases = linkedMapOf(
            "Computer" to "Ordinateur",
            "The computer screen" to "L’écran de l’ordinateur",
            "You have the hand." to "Vous avez la main.",
            "The agent has the hand." to "L’agent a la main.",
            "Take over" to "Prendre la main",
            "Hand back" to "Rendre la main",
            "Hand back to the agent" to "Rendre la main à l’agent",
            "Type on the computer…" to "Écrire sur l’ordinateur…",
            "Connecting to the computer…" to "Connexion à l’ordinateur…",
            "The computer's screen isn't coming through." to "L’écran de l’ordinateur ne passe pas.",
            "Try again" to "Réessayer",
            "Bridge address" to "Adresse du pont",
            "Computer bridge" to "Pont ordinateur",
            "Copy the saved token" to "Copier le jeton enregistré",
            "Close the session" to "Fermer la session",
            "Type it on the computer" to "L’écrire sur l’ordinateur",
            "This phone's keystore wouldn't keep the token: it lasts for this session only." to
                "Le trousseau de ce téléphone n’a pas gardé le jeton : il ne vaut que pour cette session.",
        )
        cases.forEach { (english, french) ->
            assertEquals(french, OnyxLocalization.translate(english, Locale.FRANCE))
            assertEquals(english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }

    @Test
    fun `l'approbation d'une action sensible parle francais`() {
        val cases = linkedMapOf(
            "Run a command" to "Exécuter une commande",
            "It leaves for the PC only after you approve it." to "Elle ne part vers le PC qu’après votre approbation.",
            "Run this command?" to "Exécuter cette commande ?",
            "Keyboard shortcut" to "Raccourci clavier",
            "For example: ctrl+c, alt+F4, Return, Escape." to "Par exemple : ctrl+c, alt+F4, Retour, Échap.",
            "Press this shortcut?" to "Enclencher ce raccourci ?",
            "Approve" to "Approuver",
            "Refuse" to "Refuser",
            "The bridge refuses this command." to "Le pont refuse cette commande.",
            "This needs your approval." to "Cette action demande votre approbation.",
            "Someone else has the hand." to "Quelqu’un d’autre a la main.",
        )
        cases.forEach { (english, french) ->
            assertEquals(french, OnyxLocalization.translate(english, Locale.FRANCE))
            assertEquals(english, OnyxLocalization.translate(english, Locale.ENGLISH))
        }
    }

    @Test
    fun `les verbes de la derniere action et les refus se traduisent`() {
        assertEquals("clic", OnyxLocalization.translate("click", Locale.FRANCE))
        assertEquals("frappe", OnyxLocalization.translate("typing", Locale.FRANCE))
        assertEquals("glisser", OnyxLocalization.translate("drag", Locale.FRANCE))
        assertEquals("touche", OnyxLocalization.translate("key", Locale.FRANCE))
        assertEquals("commande", OnyxLocalization.translate("command", Locale.FRANCE))
        assertEquals("En cours…", OnyxLocalization.translate("Working…", Locale.FRANCE))
        assertEquals(
            "Refusé : rien n’est parti. (uname -a)",
            OnyxLocalization.format("Refused: nothing was sent. (%1\$s)", "uname -a", locale = Locale.FRANCE),
        )
    }
}

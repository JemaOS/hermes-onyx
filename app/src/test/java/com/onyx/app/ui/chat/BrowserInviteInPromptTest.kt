package com.onyx.app.ui.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La demande de prise en main vit dans le puits du prompt, pas dans le fil.**
 *
 * Capture du 2026-11-05 : la carte « Le navigateur de l'agent vous demande la main » s'affichait
 * *sur* le bas de la conversation — elle recouvrait les dernières bulles. Deux causes, et deux
 * corrections que ce garde-fou empêche d'annuler :
 *
 * 1. la carte était dessinée **dans la rangée d'outils du fil** (`BrowserTakeoverCard` dans
 *    `MessageRow`) — elle est retirée, `BrowserInvite` la porte désormais dans le compositeur ;
 * 2. la bande grandissait la plaque **sans que la réserve du fil suive** (`composerHeightPx` ne
 *    mesurait que le champ) — la plaque recouvrait donc la fin du fil. La mesure porte maintenant
 *    sur le puits entier (bande + champ).
 */
class BrowserInviteInPromptTest {

    private val messageRow = File("src/main/java/com/onyx/app/ui/chat/components/MessageRow.kt").readText()
    private val chatScreen = File("src/main/java/com/onyx/app/ui/chat/ChatScreen.kt").readText()
    private val invite = File("src/main/java/com/onyx/app/ui/chat/components/BrowserInvite.kt").readText()

    @Test
    fun `le fil ne dessine plus la carte de prise en main`() {
        assertFalse(
            "aucune carte de prise en main dans la rangée d'outils",
            messageRow.contains("BrowserTakeoverCard"),
        )
        assertFalse(
            "ni la détection d'outil qui la déclenchait",
            messageRow.contains("estUneDemandeDeMain"),
        )
    }

    @Test
    fun `la bande est dans le puits du prompt`() {
        assertTrue(
            "BrowserInvite est montée par l'écran",
            chatScreen.contains("BrowserInvite(server = state.server, onOpen = onOpenBrowser)"),
        )
        assertTrue(
            "et elle lit l'état du plugin, pas le fil",
            invite.contains("BrowserScreenApi.health(server)"),
        )
    }

    @Test
    fun `la reserve du fil couvre la bande autant que le champ`() {
        val mesure = chatScreen.indexOf("Modifier.onSizeChanged { composerHeightPx = it.height }")
        assertTrue("la réserve se mesure quelque part", mesure >= 0)
        val invite = chatScreen.indexOf("BrowserInvite(server = state.server")
        assertTrue("la bande est montée par l'écran", invite >= 0)
        assertTrue(
            "la bande vient APRÈS l'ouverture du bloc mesuré (elle en fait partie)",
            invite > mesure,
        )
        assertFalse(
            "le champ ne se mesure plus seul (la bande grandirait la plaque sans la réserve)",
            chatScreen.contains(".onSizeChanged { composerHeightPx = it.height }\n                    .onGloballyPositioned"),
        )
    }
}
package com.onyx.app.ui.chat

import com.onyx.app.data.net.BrowserScreenApi
import com.onyx.app.ui.chat.components.TakeoverClaim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * **Une demande de prise en main appartient à une conversation, pas au serveur.**
 *
 * Défaut relevé sur l'appareil : la demande était un état **global** (`onyx-browser/health` la
 * publie tant qu'elle attend), donc chaque conversation ouverte affichait la même carte. On
 * quittait le fil où l'agent demandait de l'aide — sans avoir rien fait — et l'invitation suivait
 * dans les autres fils. Ces tests verrouillent le contraire : une demande, une conversation.
 */
class TakeoverClaimTest {

    @Before
    fun vide() {
        listOf("tk-1", "tk-2", "tk-3").forEach { TakeoverClaim.forget(it) }
    }

    @Test
    fun `la demande ne suit pas dans une autre conversation`() {
        assertEquals("conv-a", TakeoverClaim.claim("tk-1", "conv-a", live = false))
        // Même demande vue depuis un autre fil : elle n'y est pas à elle.
        assertEquals("conv-a", TakeoverClaim.claim("tk-1", "conv-b", live = false))
        assertEquals("conv-a", TakeoverClaim.ownerOf("tk-1"))
    }

    @Test
    fun `la conversation dont le tour tourne prend la demande`() {
        TakeoverClaim.claim("tk-1", "conv-a", live = false)
        // Celle qui a demandé la main travaille : son tour tourne, elle passe devant.
        assertEquals("conv-b", TakeoverClaim.claim("tk-1", "conv-b", live = true))
        assertEquals("conv-b", TakeoverClaim.ownerOf("tk-1"))
        // Une fois rattachée, elle ne se déplace plus, même si une autre se dit vivante.
        assertEquals("conv-b", TakeoverClaim.claim("tk-1", "conv-c", live = true))
    }

    @Test
    fun `refuser oublie la demande`() {
        TakeoverClaim.claim("tk-2", "conv-a", live = false)
        TakeoverClaim.forget("tk-2")
        assertNull(TakeoverClaim.ownerOf("tk-2"))
        // Une nouvelle demande du même identifiant repart de zéro.
        assertEquals("conv-b", TakeoverClaim.claim("tk-2", "conv-b", live = false))
    }

    @Test
    fun `les demandes disparues ne restent pas en mémoire`() {
        TakeoverClaim.claim("tk-1", "conv-a", live = false)
        TakeoverClaim.claim("tk-2", "conv-a", live = false)
        TakeoverClaim.retainOnly(setOf("tk-2"))
        assertNull(TakeoverClaim.ownerOf("tk-1"))
        assertEquals("conv-a", TakeoverClaim.ownerOf("tk-2"))
    }

    @Test
    fun `le refus part au serveur avec l'action decline`() {
        val corps = BrowserScreenApi.leaseBody("decline", note = "pas ici")
        assertEquals("decline", corps["action"]?.toString()?.trim('"'))
        assertEquals("pas ici", corps["note"]?.toString()?.trim('"'))
        // Un refus ne porte pas de taille d'écran : il ne prend rien, il rend la main.
        assertNull(corps["size"])
    }

    @Test
    fun `la carte branche son refus et connaît sa conversation`() {
        val racine = File(System.getProperty("user.dir")).let { base ->
            if (File(base, "app/src/main/java").isDirectory) base else base.parentFile ?: base
        }
        val carte = File(
            racine,
            "app/src/main/java/com/onyx/app/ui/chat/components/BrowserInvite.kt",
        ).readText()
        assertTrue("la carte doit dire non", carte.contains("BrowserScreenApi.decline("))
        assertTrue("le refus doit être traduit", carte.contains("\"Decline\""))
        assertTrue("la carte doit savoir à qui elle appartient", carte.contains("TakeoverClaim.claim("))

        val ecran = File(
            racine,
            "app/src/main/java/com/onyx/app/ui/chat/ChatScreen.kt",
        ).readText()
        assertTrue(
            "l'écran doit passer la conversation et son état de flux",
            ecran.contains("conversationId = state.conversationId")
                && ecran.contains("live = state.isStreaming"),
        )
    }
}

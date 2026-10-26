package com.onyx.app.ui.chat.components

import com.onyx.app.data.link.LinkPreview
import com.onyx.app.data.link.LinkPreviews
import com.onyx.app.data.model.ChatMessage
import com.onyx.app.data.model.Role
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Les conditions de la fiche sous un message ( + `LinkPreviewSlot`):
 * **réglage éteint ⇒ aucune requête réseau**, et aucune fiche sur un tour en cours, une note de
 * pilotage ou une erreur.
 */
class LinkPreviewSlotTest {

    private fun message(text: String, error: String? = null, steer: Boolean = false) =
        ChatMessage(id = "m", role = Role.ASSISTANT, text = text, createdAt = 0L, error = error, isSteer = steer)

    @Test
    fun `reglage eteint, aucune requete reseau`() = runBlocking {
        val reads = AtomicInteger(0)
        val store = LinkPreviews(folder = null, fetch = { reads.incrementAndGet(); null })

        requestLinkPreview(store, message("See https://example.com/a"), isLive = false, linkPreviews = false)

        assertEquals(0, reads.get())
    }

    @Test
    fun `reglage allume, la page est lue une fois`() = runBlocking {
        val reads = AtomicInteger(0)
        val store = LinkPreviews(folder = null, fetch = { url ->
            reads.incrementAndGet()
            LinkPreview(url = url, title = "The trail", site = "example.com", image = null)
        })
        val text = message("See https://example.com/a")

        requestLinkPreview(store, text, isLive = false, linkPreviews = true)
        requestLinkPreview(store, text, isLive = false, linkPreviews = true)

        assertEquals(1, reads.get())
    }

    @Test
    fun `pas de fiche pendant la reponse, sur une note de pilotage, ou une erreur`() {
        val normal = message("See https://example.com/a")
        assertEquals("https://example.com/a", linkPreviewUrl(normal, isLive = false, linkPreviews = true))

        assertNull(linkPreviewUrl(normal, isLive = true, linkPreviews = true))
        assertNull(linkPreviewUrl(message("See https://example.com/a", steer = true), isLive = false, linkPreviews = true))
        assertNull(linkPreviewUrl(message("https://example.com/a", error = "boom"), isLive = false, linkPreviews = true))
        assertNull(linkPreviewUrl(message("just words"), isLive = false, linkPreviews = true))
    }
}

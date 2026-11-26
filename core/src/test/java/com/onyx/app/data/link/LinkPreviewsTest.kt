package com.onyx.app.data.link

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le cache des fiches, port de `LinkPreviewTests` (EchoTests/LinkPreviewTests) : une page lue
 * une fois et gardée, une page illisible laissée tranquille un moment, et les requêtes en cours
 * dédoublonnées.
 */
class LinkPreviewsTest {

    private fun tempFolder() = Files.createTempDirectory("link-previews").toFile()

    @Test
    fun `une page est lue une fois et gardee sur disque`() = runBlocking {
        val folder = tempFolder()
        try {
            val url = "https://example.com/trail"
            val reads = AtomicInteger(0)
            val store = LinkPreviews(folder = folder, fetch = { u ->
                reads.incrementAndGet()
                LinkPreview(url = u, title = "The trail", site = "example.com", image = null)
            })
            store.request(url)
            store.request(url)
            assertEquals("The trail", store.preview(url)?.title)
            assertEquals("lue une fois", 1, reads.get())

            // Un autre démarrage : depuis le disque, sans relire la page.
            val again = LinkPreviews(folder = folder, fetch = { reads.incrementAndGet(); null })
            again.request(url)
            assertEquals("The trail", again.preview(url)?.title)
            assertEquals(1, reads.get())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `une page illisible est laissee tranquille un moment`() = runBlocking {
        val folder = tempFolder()
        try {
            val url = "https://example.com/gone"
            val reads = AtomicInteger(0)
            val store = LinkPreviews(folder = folder, fetch = { reads.incrementAndGet(); null })
            val now = 1_700_000_000_000L
            store.request(url, now)
            store.request(url, now + 60_000)
            assertEquals("pas redemandée dans le quart d'heure", 1, reads.get())
            store.request(url, now + LinkPreviews.RETRY_AFTER_MS + 1)
            assertEquals(2, reads.get())
            assertNull(store.preview(url))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `deux requetes simultanees ne lisent la page qu une fois`() = runBlocking {
        val url = "https://example.com/slow"
        val reads = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val store = LinkPreviews(folder = null, fetch = { u ->
            reads.incrementAndGet()
            gate.await()
            LinkPreview(url = u, title = "Slow", site = "example.com", image = null)
        })

        coroutineScope {
            val first = launch { store.request(url) }
            // Laisse la première requête entrer dans `fetching`.
            while (reads.get() == 0) yield()
            val second = launch { store.request(url) }
            yield()
            gate.complete(Unit)
            first.join()
            second.join()
        }

        assertEquals(1, reads.get())
    }
}

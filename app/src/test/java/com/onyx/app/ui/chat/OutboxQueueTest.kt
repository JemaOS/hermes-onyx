package com.onyx.app.ui.chat

import com.onyx.app.data.model.OutboxItem
import com.onyx.app.data.model.OutboxState
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxQueueTest {
    private val item = OutboxItem(id = "a", text = "A", queuedAt = 10_000L)

    @Test
    fun `une coupure remet le tour courant devant et fait attendre toute la file`() {
        val queue = OutboxQueue()
        queue.enqueue(item.copy(id = "b", text = "B"), waiting = false)

        queue.hold(item)

        assertEquals(listOf("a", "b"), queue.items.map { it.id })
        assertTrue(queue.items.all { it.state == OutboxState.WAITING_FOR_CONNECTION })
    }

    @Test
    fun `seul le premier message peut etre confirme et il libere les suivants`() {
        val queue = OutboxQueue(
            listOf(
                item.copy(state = OutboxState.PAUSED),
                item.copy(id = "b", text = "B", state = OutboxState.PAUSED),
            ),
        )

        assertFalse(queue.confirmFirst("b", now = 20_000L))
        assertTrue(queue.confirmFirst("a", now = 20_000L))

        assertEquals(OutboxState.QUEUED, queue.items[0].state)
        assertEquals(20_000L, queue.items[0].queuedAt)
        assertEquals(OutboxState.QUEUED, queue.items[1].state)
        assertEquals("a", queue.takeNext(now = 20_000L)?.id)
    }

    @Test
    fun `un message perime attend une confirmation au lieu de partir seul`() {
        val queue = OutboxQueue(listOf(item.copy(state = OutboxState.WAITING_FOR_CONNECTION)))
        val now = item.queuedAt + OutboxItem.STALE_AFTER_MS + 1

        assertNull(queue.takeNext(now))
        assertEquals(OutboxState.PAUSED, queue.items.single().state)
    }

    @Test
    fun `edition et suppression retirent exactement le message choisi`() {
        val queue = OutboxQueue(listOf(item, item.copy(id = "b", text = "B")))

        assertEquals("B", queue.remove("b")?.text)
        assertEquals(listOf("a"), queue.items.map { it.id })
    }

    @Test
    fun `la file hors ligne part dans l ordre ou elle a ete ecrite`() {
        val queue = OutboxQueue()
        queue.enqueue(item.copy(id = "a"), waiting = false)
        queue.enqueue(item.copy(id = "b"), waiting = false)
        queue.enqueue(item.copy(id = "c"), waiting = false)

        assertEquals(listOf("a", "b", "c"), queue.items.map { it.id })
        assertEquals(
            listOf("a", "b", "c"),
            listOf(queue.takeNext(1L)?.id, queue.takeNext(1L)?.id, queue.takeNext(1L)?.id),
        )
    }

    @Test
    fun `une coupure remet le tour courant en tete sans melanger les suivants`() {
        val queue = OutboxQueue()
        queue.enqueue(item.copy(id = "b"), waiting = false)
        queue.enqueue(item.copy(id = "c"), waiting = false)

        queue.hold(item.copy(id = "a"))

        assertEquals(listOf("a", "b", "c"), queue.items.map { it.id })
    }

    @Test
    fun `le premier message retenu gate toute la file`() {
        val queue = OutboxQueue()
        queue.enqueue(item.copy(id = "a"), waiting = true)

        // Le suivant arrive alors que la tête attend déjà la connexion: il attend aussi.
        queue.enqueue(item.copy(id = "b"), waiting = false)

        assertTrue(queue.items.all { it.state == OutboxState.WAITING_FOR_CONNECTION })
    }

    @Test
    fun `au retour de la connexion la file repart seule dans l ordre`() {
        val queue = OutboxQueue()
        queue.hold(item.copy(id = "a"))
        queue.enqueue(item.copy(id = "b"), waiting = false)
        assertTrue(queue.items.all { it.state == OutboxState.WAITING_FOR_CONNECTION })

        // La connexion revient: `takeNext` reprend la tête sans qu'on ait rien confirmé.
        assertEquals("a", queue.takeNext(now = 1L)?.id)
        assertEquals("b", queue.takeNext(now = 1L)?.id)
        assertTrue(queue.items.isEmpty())
    }

    @Test
    fun `la file survit a un aller-retour de serialisation`() {
        val items = listOf(
            item.copy(id = "a", text = "A", state = OutboxState.WAITING_FOR_CONNECTION, queuedAt = 11L),
            item.copy(
                id = "b",
                text = "B",
                state = OutboxState.QUEUED,
                queuedAt = 12L,
                attachments = listOf(
                    com.onyx.app.data.model.Attachment(id = "att", mimeType = "image/jpeg", uri = "file:///a.jpg", name = "a.jpg"),
                ),
            ),
        )

        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(OutboxItem.serializer()), items)
        val decoded = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(OutboxItem.serializer()),
            encoded,
        )

        assertEquals(items, decoded)
        // Ce que la conversation enregistre, la file le relit tel quel.
        assertEquals(items.map { it.id }, OutboxQueue(decoded).items.map { it.id })
        assertEquals(OutboxState.WAITING_FOR_CONNECTION, decoded.first().state)
    }

    @Test
    fun `les etats de la ligne suivent la position et l anciennete`() {
        val waiting = item.copy(state = OutboxState.WAITING_FOR_CONNECTION, queuedAt = 0L)
        assertEquals(
            "Waiting for connection · 00:00",
            outboxStatusText(waiting, isFirst = true, now = 10_000L, locale = Locale.FRANCE, timeZone = java.util.TimeZone.getTimeZone("UTC")),
        )
        assertEquals(
            "Not sent · from 00:00",
            outboxStatusText(
                waiting.copy(state = OutboxState.PAUSED),
                isFirst = true,
                now = OutboxItem.STALE_AFTER_MS + 1,
                locale = Locale.FRANCE,
                timeZone = java.util.TimeZone.getTimeZone("UTC"),
            ),
        )
        assertEquals("Queued", outboxStatusText(item, isFirst = false, locale = Locale.FRANCE))
    }
}

package com.onyx.app.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Vérifie le décodage SSE : c'est la pièce la plus fragile de la couche réseau et elle est
 * difficile à observer sans serveur, d'où ce test hors appareil.
 */
class SseParserTest {

    @Test
    fun `un evenement est dispatche sur une ligne vide`() {
        val parser = SseParser()

        assertNull(parser.feed("data: {\"a\":1}"))
        val event = parser.feed("")

        assertEquals("{\"a\":1}", event?.data)
        assertNull(event?.event)
    }

    @Test
    fun `plusieurs lignes data sont concatenees`() {
        val parser = SseParser()

        parser.feed("data: ligne1")
        parser.feed("data: ligne2")
        val event = parser.feed("")

        assertEquals("ligne1\nligne2", event?.data)
    }

    @Test
    fun `le nom d evenement et l id sont conserves`() {
        val parser = SseParser()

        parser.feed("event: assistant.delta")
        parser.feed("id: 42")
        parser.feed("data: bonjour")
        val event = parser.feed("")

        assertEquals("assistant.delta", event?.event)
        assertEquals("42", event?.id)
        assertEquals("bonjour", event?.data)
    }

    @Test
    fun `les commentaires et les champs inconnus sont ignores`() {
        val parser = SseParser()

        assertNull(parser.feed(": heartbeat"))
        assertNull(parser.feed("retry: 3000"))
        assertNull(parser.feed("inconnu: valeur"))
        assertNull(parser.feed(""))

        parser.feed("data: utile")
        assertEquals("utile", parser.feed("")?.data)
    }

    @Test
    fun `un seul espace apres les deux points est retire`() {
        val parser = SseParser()

        parser.feed("data:  deux espaces")
        assertEquals(" deux espaces", parser.feed("")?.data)
    }

    @Test
    fun `fin dispatch l evenement en attente sans ligne vide`() {
        val parser = SseParser()

        parser.feed("data: [DONE]")
        val event = parser.finish()

        assertEquals("[DONE]", event?.data)
        assertNull(parser.finish())
    }

    @Test
    fun `un flux complet de chat completions se decode evenement par evenement`() {
        val raw = buildString {
            append("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"Bon\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"jour\"}}]}\n\n")
            append("data: [DONE]\n\n")
        }

        val parser = SseParser()
        val payloads = raw.split("\n").mapNotNull { parser.feed(it)?.data }

        assertEquals(4, payloads.size)
        assertEquals("[DONE]", payloads.last())
    }
}

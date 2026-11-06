package com.onyx.app.data.net

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Une lecture de compteurs ne cherche pas dans le vide.**
 *
 * `session.usage` et `session.context_breakdown` ne répondent que pour une session **vivante** —
 * or presque toutes les conversations d'un bot sont terminées quand on ouvre la feuille. Les deux
 * appels étaient faits **l'un après l'autre**, chacun avec quinze secondes de patience : trente
 * secondes de sablier pour deux réponses qu'on savait déjà absentes, avant même de lire le registre,
 * qui lui répond en quelques dizaines de millisecondes.
 */
class CounterReadLatencyTest {

    private val source = File("src/main/java/com/onyx/app/data/net/ServeChatActions.kt").readText()

    @Test
    fun `les deux lectures partent ensemble`() {
        assertTrue(
            "un seul coroutineScope porte les deux appels",
            source.contains("val lecture = coroutineScope {"),
        )
        assertTrue("la consommation est lancée sans attendre", source.contains("async { runCatching { channel().call(\"session.usage\""))
        assertTrue("le contexte aussi", source.contains("async {\n                runCatching { channel().call(\"session.context_breakdown\""))
        assertTrue(
            "le registre part en même temps, pas après l'attente",
            source.contains("val consigne = async { runCatching { recordedUsage(sessionId, profile) }.getOrNull() }"),
        )
        assertTrue(
            "et on attend les trois à la fin",
            source.contains("Triple(usage.await(), detail.await(), consigne.await())"),
        )
    }

    @Test
    fun `une lecture de compteurs n attend pas quinze secondes`() {
        assertTrue("un délai court existe", source.contains("const val READ_TIMEOUT = 5_000L"))
        assertTrue(
            "la consommation l'utilise",
            source.contains("channel().call(\"session.usage\", params, READ_TIMEOUT)"),
        )
        assertTrue(
            "les processus aussi",
            source.contains("channel().call(\"process.list\", sessionParams(liveSessionId(sessionId)), READ_TIMEOUT)"),
        )
        // Le délai long reste pour les gestes qui touchent le dossier, pas pour une lecture.
        assertTrue("le délai long existe toujours pour les autres gestes", source.contains("const val TIMEOUT = 15_000L"))
    }
}

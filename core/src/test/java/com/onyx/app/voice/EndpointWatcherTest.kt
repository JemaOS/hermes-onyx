package com.onyx.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La fin d'écoute : « ça se bloque toujours en plein milieu d'une phrase ».
 *
 * Deux causes, deux éprouvettes :
 *  - le minuteur était **plus court** que celui de la référence (1,1 s contre 1,2 s), donc une
 *    hésitation terminait l'énoncé ;
 *  - il ne regardait que la transcription : dès qu'elle s'arrêtait de bouger une seconde, la phrase
 *    partait — même si le micro entendait encore une voix. La référence exige aussi que la pièce
 *    soit calme (0,6 × le silence), et la phrase qui n'en finit pas est terminée par un plafond.
 */
class EndpointWatcherTest {

    /** Une mesure de 100 ms, comme la boucle de surveillance (le pas de la référence). */
    private fun EndpointWatcher.tick(level: Float, now: Long) = level(level, 0.1, now)

    @Test
    fun `le seuil est celui de la reference`() {
        assertEquals("SpeechRecognizer.silenceTimeout de la référence", 1200L, DEFAULT_ENDPOINT_SILENCE_MS)
        assertEquals(720L, (DEFAULT_ENDPOINT_SILENCE_MS * ENDPOINT_QUIET_FRACTION).toLong())
        assertEquals(45_000L, MAX_UTTERANCE_MS)
    }

    @Test
    fun `une phrase stable et une piece calme terminent l ecoute`() {
        val watcher = EndpointWatcher()
        watcher.start(now = 0)
        // La pièce se déclare, puis rien : le plancher de bruit se pose dessus.
        watcher.tick(level = 0.02f, now = 100)
        watcher.tick(level = 0.02f, now = 200)
        assertTrue(
            "1,2 s de transcription stable et 0,72 s de calme",
            watcher.shouldEnd("bonjour", sinceLastChangeMs = 1300, sinceFirstSpeechMs = 2000, now = 3000),
        )
    }

    @Test
    fun `une voix qui continue ne termine pas l enonce, meme sans nouveau mot`() {
        val watcher = EndpointWatcher()
        watcher.start(now = 0)
        watcher.tick(level = 0.02f, now = 100)
        // La transcription n'a pas bougé depuis 1,4 s, mais on entend encore une voix : une
        // hésitation, une respiration — **ce n'est pas la fin de la phrase**. C'est exactement ce
        // qui coupait l'énoncé au milieu.
        watcher.level(level = 0.6f, seconds = 0.1, now = 2900)
        assertFalse(
            "la personne parle encore",
            watcher.shouldEnd("bonjour comment tu", sinceLastChangeMs = 1400, sinceFirstSpeechMs = 2000, now = 3000),
        )
        // Et quand elle se tait pour de bon, l'écoute se termine.
        assertTrue(
            "0,72 s de calme après la dernière voix",
            watcher.shouldEnd("bonjour comment tu", sinceLastChangeMs = 4400, sinceFirstSpeechMs = 2000, now = 3700),
        )
    }

    @Test
    fun `le bruit de la piece ne passe pas pour une voix`() {
        val watcher = EndpointWatcher()
        watcher.start(now = 0)
        // Une pièce bruyante, mais constante : le plancher monte avec elle et rien n'est « une voix ».
        repeat(20) { i -> watcher.level(level = 0.2f, seconds = 0.1, now = 100L * (i + 1)) }
        assertTrue(
            "un bruit constant n'empêche pas la fin de l'énoncé",
            watcher.shouldEnd("bonjour", sinceLastChangeMs = 1300, sinceFirstSpeechMs = 2000, now = 2100),
        )
    }

    @Test
    fun `une phrase qui n en finit pas est terminee par le plafond`() {
        val watcher = EndpointWatcher()
        watcher.start(now = 0)
        // Une voix continue, donc jamais « calme » : sans plafond, l'écoute ne se terminerait jamais
        // et plus rien ne partirait. Le plafond de la référence (45 s) la termine.
        watcher.level(level = 0.6f, seconds = 0.1, now = 43_900)
        assertFalse(
            "pas encore au plafond",
            watcher.shouldEnd("bon", sinceLastChangeMs = 0, sinceFirstSpeechMs = 44_000, now = 44_000),
        )
        watcher.level(level = 0.6f, seconds = 0.1, now = 45_000)
        assertTrue(
            "le plafond termine l'énoncé",
            watcher.shouldEnd("bon", sinceLastChangeMs = 0, sinceFirstSpeechMs = 45_000, now = 45_000),
        )
    }

    @Test
    fun `un silence ne termine pas une ecoute qui n a pas commence`() {
        val watcher = EndpointWatcher()
        watcher.start(now = 0)
        watcher.tick(0.02f, 100)
        assertFalse(
            "rien n'a été entendu",
            watcher.shouldEnd("", sinceLastChangeMs = 5000, sinceFirstSpeechMs = 0, now = 5000),
        )
    }

    @Test
    fun `le plafond ne termine pas une ecoute qui n a pas commence`() {
        // Le défaut : l'horloge d'énoncé part de `firstSpeechAt`, qui vaut **zéro** tant qu'aucun
        // mot n'est arrivé. `now - 0` dépasse donc les 45 s dès la première mesure, et l'écoute
        // était close 120 ms après son ouverture — la personne n'avait jamais le temps de parler.
        // Le plafond ne juge que s'il y a un énoncé.
        val watcher = EndpointWatcher()
        watcher.start(now = 0)
        watcher.tick(0.02f, 120)
        assertFalse(
            "sans un mot, le plafond ne termine rien",
            watcher.shouldEnd("", sinceLastChangeMs = 120, sinceFirstSpeechMs = 1_700_000_000_120L, now = 120),
        )
        // Et avec des mots, il termine bien — le plafond garde son rôle.
        watcher.tick(0.6f, 45_000)
        assertTrue(
            "un énoncé qui n'en finit jamais est terminé",
            watcher.shouldEnd("bonjour", sinceLastChangeMs = 0, sinceFirstSpeechMs = 45_000, now = 45_000),
        )
    }
}

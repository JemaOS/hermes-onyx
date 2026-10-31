package com.onyx.app.voice

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lecture du PCM de Kokoro.
 *
 * Le défaut d'origine : le tampon du flux était taillé sur la **phrase entière**, donc la boucle
 * d'écriture ne bloquait jamais et rendait la main en quelques millisecondes ; le lecteur coupait
 * alors la piste — tête de lecture encore à zéro — et la phrase entière était jetée. Le bouton
 * affichait « en lecture » et aucun son ne sortait, sur l'appareil comme sur l'émulateur.
 */
class PcmPlaybackTest {

    private val player = File("src/main/java/com/onyx/app/voice/SpeechAudioPlayer.kt").readText()

    @Test
    fun `le tampon du flux ne fait pas la taille de la phrase`() {
        // Taillé sur la phrase, le tampon l'avale d'un coup : `write` ne bloque plus et tout part
        // avant qu'un seul échantillon ne sonne.
        assertFalse(player.contains("bytes.size.coerceAtMost(BUFFER_CAP)"))
        assertTrue(player.contains("STREAM_BUFFERS"))
        assertTrue(player.contains("MIN_BUFFER_BYTES"))
    }

    @Test
    fun `la piste ne se coupe pas avant que la tete de lecture ait tout joue`() {
        assertTrue(player.contains("track.playbackHeadPosition"))
        assertTrue(player.contains("totalFrames"))
        assertTrue(player.contains("pcmStillPlaying("))
        // Le mode de transfert reste un flux, et la coupe attend la marge d'abandon.
        assertTrue(player.contains("AudioTrack.MODE_STREAM"))
        assertTrue(player.contains("DRAIN_GRACE_MS"))
    }

    @Test
    fun `un lecteur refuse par l appareil est une erreur, pas un silence`() {
        assertTrue(player.contains("AudioTrack.STATE_INITIALIZED"))
    }

    @Test
    fun `on attend tant que la tete n a pas rattrape l ecrit`() {
        // 1 000 échantillons écrits, 400 joués, la piste joue, la marge n'est pas épuisée.
        assertTrue(pcmStillPlaying(headFrames = 400, totalFrames = 1_000, playing = true, nowMs = 10, deadlineMs = 500))
    }

    @Test
    fun `on n attend plus quand tout est joue`() {
        assertFalse(pcmStillPlaying(headFrames = 1_000, totalFrames = 1_000, playing = true, nowMs = 10, deadlineMs = 500))
        assertFalse(pcmStillPlaying(headFrames = 1_001, totalFrames = 1_000, playing = true, nowMs = 10, deadlineMs = 500))
    }

    @Test
    fun `on n attend pas une piste arretee ou en pause`() {
        // Une piste qui ne joue plus ne rattrapera rien : attendre serait une boucle sans fin.
        assertFalse(pcmStillPlaying(headFrames = 0, totalFrames = 1_000, playing = false, nowMs = 10, deadlineMs = 500))
    }

    @Test
    fun `on abandonne l attente passe le delai`() {
        // Tête immobile et marge épuisée : on rend la main au lieu de bloquer le fil.
        assertFalse(pcmStillPlaying(headFrames = 0, totalFrames = 1_000, playing = true, nowMs = 501, deadlineMs = 500))
    }
}

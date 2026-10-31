package com.onyx.app.voice

import android.media.AudioTrack
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lecture du PCM de Kokoro.
 *
 * Deux défauts s'y sont succédé, et ils se ressemblent de loin :
 *
 * 1. le tampon du flux était taillé sur la **phrase entière** : la boucle d'écriture ne bloquait
 *    jamais, rendait la main en quelques millisecondes, et le lecteur coupait la piste la tête de
 *    lecture encore à zéro — la phrase entière était jetée, le bouton affichait « en lecture » ;
 * 2. le tampon est ensuite passé à deux minimums, soit ~200 ms : le temps de **deux écritures**,
 *    donc aucune marge. Le moindre retard du fil qui alimente le mélangeur vidait la piste, et cela
 *    s'entendait — la voix **hachée par moments**. Le lecteur démarrait de plus sur une piste vide.
 *
 * Le contrat tenu ici : la piste se **remplit avant de démarrer**, la marge vaut le pré-tampon, et
 * un trou se **patiente** au lieu de passer pour une fin de phrase.
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
    fun `le tampon tient le pre-tampon, pas deux cent millisecondes`() {
        // Deux minimums font ~200 ms : deux écritures, aucune marge. Le tampon qu'on demande vaut
        // donc au moins le pré-tampon.
        assertTrue(player.contains("streamBufferBytes(minimum, PRE_ROLL_MS, BUFFER_CAP)"))
        assertEquals(48_000, streamBufferBytes(minimumBytes = 4_800))
        // Un appareil qui réclame plus que le pré-tampon garde ses deux minimums.
        assertEquals(96_000, streamBufferBytes(minimumBytes = 48_000))
        // Le plafond tient : la mémoire n'achète rien au-delà de quatre secondes.
        assertEquals(192_000, streamBufferBytes(minimumBytes = 400_000))
        // Le plancher vaut aussi quand l'appareil ne sait pas dire son minimum.
        assertEquals(48_000, streamBufferBytes(minimumBytes = 0))
    }

    @Test
    fun `la piste se remplit avant de demarrer`() {
        // Démarrer une piste vide met l'horloge en marche alors que rien n'est copié : les premiers
        // cycles du mélangeur consomment du vide, et le début de la phrase est mangé.
        assertTrue(
            player.indexOf("val preRoll = preRollBytes") <
                player.lastIndexOf("track.play()"),
        )
        assertTrue(player.contains("preRollBytes(bytes.size, buffer, PRE_ROLL_MS)"))
        // Un pré-tampon nul rend l'ancien démarrage (piste vide) : c'est le témoin de la mesure.
        assertEquals(0, preRollBytes(clipBytes = 240_000, bufferBytes = 48_000, preRollMillis = 0L))
        assertEquals(9_600, streamBufferBytes(minimumBytes = 4_800, preRollMillis = 0L))
        // Le pré-tampon ne dépasse jamais la phrase : 1 s en tampon, 300 ms de phrase = 300 ms.
        assertEquals(48_000, preRollBytes(clipBytes = 240_000, bufferBytes = 48_000))
        assertEquals(4_800, preRollBytes(clipBytes = 4_800, bufferBytes = 48_000))
    }

    @Test
    fun `le fil qui alimente le melangeur est prioritaire, et les sous-alimentations comptees`() {
        assertTrue(player.contains("Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)"))
        assertTrue(player.contains("track.underrunCount"))
        assertTrue(player.contains("MESURE underruns="))
        assertTrue(player.contains("firstSampleMs"))
        assertTrue(player.contains("feedRtf"))
    }

    @Test
    fun `le premier echantillon s observe depuis un autre fil`() {
        // L'observer depuis le fil d'écriture suspendrait l'écriture : la mesure fabriquerait le
        // trou qu'elle prétend mesurer.
        assertTrue(player.contains("startFirstSampleWatch(token, track)"))
        // Le nom du fil est celui du produit : la purge des anciens noms porte aussi sur ce relevé.
        assertTrue(player.contains("\"onyx-pcm-first-sample\""))
        assertTrue(player.contains("FIRST_SAMPLE_WATCH_MS"))
        val watch = FirstSample()
        assertEquals(-1L, watch.firstElapsedMs())
        watch.set(42L)
        watch.set(900L)
        assertEquals(42L, watch.firstElapsedMs())
    }

    @Test
    fun `une phrase courte ne se pre-tamponne pas plus qu elle ne dure`() {
        // 300 ms de phrase : le pré-tampon est la phrase entière, pas une seconde de silence
        // fabriquée pour remplir la piste.
        assertEquals(14_400, preRollBytes(clipBytes = 14_400, bufferBytes = 48_000))
    }

    @Test
    fun `le facteur temps reel se lit comme un rapport`() {
        // Une seconde d'horloge pour une seconde d'audio : on nourrit exactement la consommation.
        assertEquals(1.0, realTimeFactor(bytesWritten = 48_000, wallMs = 1_000L), 1e-9)
        // Deux fois plus lent que le temps réel : c'est la fabrique des hachures.
        assertEquals(0.5, realTimeFactor(bytesWritten = 24_000, wallMs = 1_000L), 1e-9)
        assertEquals(0.0, realTimeFactor(bytesWritten = 48_000, wallMs = 0L), 1e-9)
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
    fun `une ecriture refusee se patiente, l offset ne glisse pas`() {
        assertTrue(player.contains("WRITE_STALL_LIMIT"))
        assertTrue(player.contains("WRITE_STALL_WAIT_MS"))
        // Le refus passager ne jette plus la fin de la phrase : l'offset n'avance qu'accepté.
        assertTrue(player.contains("if (accepted <= 0) throw IllegalStateException"))
        assertTrue(player.contains("if (!isCurrent(token, track)) return 0"))
    }

    @Test
    fun `on attend tant que la tete n a pas rattrape l ecrit`() {
        // 1 000 échantillons écrits, 400 joués, la piste joue, la marge n'est pas épuisée.
        assertTrue(
            pcmStillPlaying(
                headFrames = 400,
                totalFrames = 1_000,
                playState = AudioTrack.PLAYSTATE_PLAYING,
                nowMs = 10,
                deadlineMs = 500,
            ),
        )
    }

    @Test
    fun `on n attend plus quand tout est joue`() {
        assertFalse(
            pcmStillPlaying(
                headFrames = 1_000,
                totalFrames = 1_000,
                playState = AudioTrack.PLAYSTATE_PLAYING,
                nowMs = 10,
                deadlineMs = 500,
            ),
        )
        assertFalse(
            pcmStillPlaying(
                headFrames = 1_001,
                totalFrames = 1_000,
                playState = AudioTrack.PLAYSTATE_PLAYING,
                nowMs = 10,
                deadlineMs = 500,
            ),
        )
    }

    @Test
    fun `une piste en pause se patiente au lieu d etre jetee`() {
        // Un trou n'est pas une fin de phrase. Jeter la piste parce qu'elle est en pause perd la fin
        // du texte — c'est un mot mangé, et le bouton « en lecture » continuerait d'afficher un son
        // que personne n'entend.
        assertTrue(
            pcmStillPlaying(
                headFrames = 0,
                totalFrames = 1_000,
                playState = AudioTrack.PLAYSTATE_PAUSED,
                nowMs = 10,
                deadlineMs = 500,
            ),
        )
        // Même au-delà de la marge : la marge juge la lecture, pas la pause.
        assertTrue(
            pcmStillPlaying(
                headFrames = 0,
                totalFrames = 1_000,
                playState = AudioTrack.PLAYSTATE_PAUSED,
                nowMs = 10_000,
                deadlineMs = 500,
            ),
        )
        // Les secondes passées en pause ne consomment pas la patience destinée à la lecture.
        assertTrue(
            pcmStillPlaying(
                headFrames = 0,
                totalFrames = 1_000,
                playState = AudioTrack.PLAYSTATE_PLAYING,
                pausedMs = 600,
                nowMs = 1_000,
                deadlineMs = 500,
            ),
        )
    }

    @Test
    fun `on n attend pas une piste arretee`() {
        // Arrêtée ou libérée, la piste ne rattrapera rien : attendre serait une boucle sans fin.
        assertFalse(
            pcmStillPlaying(
                headFrames = 0,
                totalFrames = 1_000,
                playState = AudioTrack.PLAYSTATE_STOPPED,
                nowMs = 10,
                deadlineMs = 500,
            ),
        )
    }

    @Test
    fun `on abandonne l attente passe le delai`() {
        // Tête immobile et marge épuisée : on rend la main au lieu de bloquer le fil.
        assertFalse(
            pcmStillPlaying(
                headFrames = 0,
                totalFrames = 1_000,
                playState = AudioTrack.PLAYSTATE_PLAYING,
                nowMs = 501,
                deadlineMs = 500,
            ),
        )
    }

    @Test
    fun `les millisecondes se convertissent en octets de PCM 24 kHz`() {
        assertEquals(48_000, bytesForMillis(1_000L))
        assertEquals(1_440, bytesForMillis(30L))
    }
}

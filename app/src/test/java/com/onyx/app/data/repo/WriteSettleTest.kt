package com.onyx.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La fenêtre d'écriture suit ce qu'il y a à écrire.**
 *
 * Le magasin des conversations est un seul fichier : chaque sauvegarde resérialise et réécrit
 * **tout**. Son prix ne dépend donc pas du changement, mais du total — et c'est ce que le
 * propriétaire ressent (« ça rame quand j'ai plusieurs conversations, ou une conversation qui a
 * lancé 60 outils »).
 *
 * Mesuré (`ConversationWriteCostTest`) : une conversation de 60 outils = 256 Kio / 7 ms ;
 * vingt-cinq de ces conversations = 6,4 Mio / 33 ms. À fenêtre fixe de 250 ms, un tour qui lance
 * soixante outils déclenchait quatre écritures par seconde — 130 ms de CPU et 26 Mio de disque par
 * seconde, pendant qu'on lit la réponse.
 */
class WriteSettleTest {

    @Test
    fun `une petite installation garde la fenetre d origine`() {
        assertEquals(WRITE_SETTLE_MILLIS, writeSettleMillis(0))
        assertEquals(WRITE_SETTLE_MILLIS + 40, writeSettleMillis(10))
    }

    @Test
    fun `une grosse installation espace les ecritures`() {
        // 300 messages : le fichier de la mesure (6,4 Mio, 33 ms par écriture).
        val big = writeSettleMillis(300)
        assertTrue("la fenêtre d'une grosse installation doit être plus longue", big > WRITE_SETTLE_MILLIS * 3)
        assertEquals(WRITE_SETTLE_CEILING_MILLIS, big)
    }

    @Test
    fun `la fenetre est monotone et bornee`() {
        var previous = 0L
        for (count in listOf(0, 1, 50, 200, 400, 5_000, 100_000)) {
            val window = writeSettleMillis(count)
            assertTrue("la fenêtre ne recule pas", window >= previous)
            assertTrue("la fenêtre reste sous son plafond", window <= WRITE_SETTLE_CEILING_MILLIS)
            previous = window
        }
        // Un compte négatif ne doit pas raccourcir la fenêtre : un état mal formé ne fait pas écrire
        // plus vite que prévu.
        assertEquals(WRITE_SETTLE_MILLIS, writeSettleMillis(-5))
    }

    @Test
    fun `la fenetre reste sous ce qu une personne attend d une sauvegarde`() {
        // Le plafond n'est pas « le plus long possible » : au-delà, on écrit, on n'attend plus.
        assertTrue(WRITE_SETTLE_CEILING_MILLIS <= 1_500)
    }
}